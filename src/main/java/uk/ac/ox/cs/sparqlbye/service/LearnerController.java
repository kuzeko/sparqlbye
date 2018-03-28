package uk.ac.ox.cs.sparqlbye.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

import org.apache.jena.graph.Triple;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFactory;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.OpAsQuery;
import org.apache.jena.sparql.engine.http.QueryExceptionHTTP;
import org.apache.log4j.Logger;
import org.eclipse.jetty.websocket.api.Session;
import org.json.JSONArray;
import org.json.JSONObject;

import uk.ac.ox.cs.sparqlbye.core.AOTree;
import uk.ac.ox.cs.sparqlbye.core.LearnerDirector;
import uk.ac.ox.cs.sparqlbye.core.UtilsJena;
import uk.ac.ox.cs.sparqlbye.core.UtilsLearner.URevengResponse;
import uk.ac.ox.cs.sparqlbye.core.UtilsLearner.URevengResponse.RevengStatus;
import uk.ac.ox.cs.sparqlbye.service.LearnerWebSocketHandler.Update;

public class LearnerController implements Observer {
	private static final Logger log = Logger.getLogger(LearnerController.class);

	private static final String API_KEY_QUERY_TEXT = "queryText";
	private static final String API_KEY_CALLBACK_ID = "callback_id";

	private static final String API_KEY_COMMAND = "command";
	private static final String API_VAL_COMMAND_EXECUTE = "execute";
    private static final String API_VAL_COMMAND_SEARCH = "search";
	private static final String API_VAL_COMMAND_REVENG = "reveng";

    private static final String API_KEY_EXECUTE_VIRTUOSO_RESPONSE = "virtuoso_response";

	private static final String API_KEY_SEARCH_ANSWERS = "pairs";
	private static final String API_KEY_SEARCH_URI = "uri";
	private static final String API_KEY_SEARCH_LABEL = "label";
	private static final String API_KEY_SEARCH_TYPE = "type";

	private static final String API_KEY_REVENG_LEARNED_QUERY = "learnedQuery";
	private static final String API_KEY_REVENG_P_BINDINGS = "pBindings";
	private static final String API_KEY_REVENG_N_BINDINGS = "nBindings";
	private static final String API_KEY_REVENG_BAD_URIS = "badUris";
	private static final String API_KEY_REVENG_URIS_USED = "urisUsed";

    private static final String API_KEY_MESSAGE = "message";
    private static final String API_VAL_MESSAGE_ERROR = "no sparql endpoint found";
    private static final String API_KEY_STATUS = "status";
	private static final String API_VAL_STATUS_ERROR = "error";

	// TODO: change this string to "answers"
	//	private static final String EXAMPLES_KEY      = "pnExamples";
	//	private static final String VIRTUOSO_KEY      = "virtuoso";

	public enum ApiErrorType { NO_SPARQL_ENDPOINT }

	private static final String LOCAL_SPARQL_SERVICE_ENDPOINT = "http://localhost:8890/sparql";

	private static final Function<Query, QueryExecution> queryToQueryExecution = (query) ->
	QueryExecutionFactory.sparqlService(LOCAL_SPARQL_SERVICE_ENDPOINT, query);

	private static LearnerController INSTANCE;

	private final List<Session>   sessions;
	private final ExecutorService executorService;

	private LearnerController() {
		log.info("LearnerController()");
		sessions        = new ArrayList<>();
		executorService = Executors.newFixedThreadPool(10);
	}

	/**
	 * This is the entry point for all updates coming from the Spark service.
	 */
	@Override
	public void update(Observable o, Object arg) {
		if( !(arg instanceof LearnerWebSocketHandler.Update) ) { throw new IllegalArgumentException(); }

		Update update = (Update) arg;
		switch (update.getType()) {
		case CONNECT:
			onConnect(update.getUser()); break;
		case CLOSE:
			onClose(update.getUser(), update.getStatusCode().get(), update.getReason().get()); break;
		case MESSAGE:
			onMessage(update.getUser(), update.getMessage().get()); break;
		default:
			throw new IllegalArgumentException("Unrecognised update type: " + arg);
		}
	}

	private void onConnect(Session user) {
		log.info("LearnerController.onConnect(user)");
		sessions.add(user);
	}

	private void onClose(Session user, int statusCode, String reason) {
		log.info("LearnerController.onClose(user, "+statusCode+", "+reason+")");
		sessions.remove(user);
	}

	/**
	 * Normal communications arrive as messages, which contain a single JSON object.
	 *
	 * @param user Session object representing the sender.
	 * @param message String object containing the json payload.
	 */
	private void onMessage(Session user, String message) {
		log.info("LearnerController.onMessage(user,"+message+")");

		JSONObject jsonMessage = new JSONObject(message);
		String     commandStr  = jsonMessage.getString(API_KEY_COMMAND);

		switch (commandStr) {
		case API_VAL_COMMAND_EXECUTE:
			executeQuery(user, jsonMessage);  break;
		case API_VAL_COMMAND_REVENG:
			executeReveng(user, jsonMessage); break;
		case API_VAL_COMMAND_SEARCH:
			executeSearch(user, jsonMessage); break;
		default:
			throw new IllegalArgumentException("Unrecognised command: " + commandStr);
		}
	}

    /**
     * Execute a keyword search on the graph and return the results to the user.
     *
     * @param user Session object representing the sender.
     * @param jsonMessage JSONObject containing a keyword field.
     */
	private void executeSearch(Session user, JSONObject jsonMessage) {
		log.info("executeSearch()");

		String keywordsString = jsonMessage.getString(API_KEY_QUERY_TEXT);
		int callbackId = jsonMessage.getInt(API_KEY_CALLBACK_ID);
		String queryString = UtilsLearnerController.makeKeywordSearchQuery(keywordsString);
		Query query = QueryFactory.create(queryString);

		CompletableFuture.supplyAsync(() -> {
			JSONArray jsonTuples = new JSONArray();

			try(QueryExecution qe = queryToQueryExecution.apply(query)) {
				log.info("  going to execute: " + queryString);
				ResultSet results = qe.execSelect();
				log.info("  result received");

				while(results.hasNext()) {
					QuerySolution solution = results.next();

					String uri = solution
						.get(UtilsLearnerController.KEYWORD_SEARCH_QUERY_VAR_URI).asResource().getURI();
					String label = solution
						.getLiteral(UtilsLearnerController.KEYWORD_SEARCH_QUERY_VAR_LABEL).getString();
					String type = solution
						.getLiteral(UtilsLearnerController.KEYWORD_SEARCH_QUERY_VAR_TYPE).getString();

					JSONObject jsonTuple = new JSONObject();
					jsonTuple.put(API_KEY_SEARCH_URI, uri);
					jsonTuple.put(API_KEY_SEARCH_LABEL, label);
					jsonTuple.put(API_KEY_SEARCH_TYPE, type);
					jsonTuples.put(jsonTuple);
				}
			} catch(QueryExceptionHTTP e) {
				log.warn("  Caught QueryExceptionHTTP");
				throw new ResparqlNoEndpointException(e);
			} catch(Exception e) {
				log.warn("  Caught unexpected exception. This will probably be fatal.");
				throw e;
			}

			log.info("  jsonTuples = " + jsonTuples);
			return jsonTuples;
		}, executorService)
		.thenAcceptAsync((jsonTuples) -> {
			log.info("  sending response: " + jsonTuples);

			JSONObject jsonResponse = new JSONObject();
			jsonResponse.put(API_KEY_CALLBACK_ID, callbackId);
			jsonResponse.put(API_KEY_COMMAND, API_VAL_COMMAND_SEARCH);
			jsonResponse.put(API_KEY_SEARCH_ANSWERS, jsonTuples);
			String responseMessage = String.valueOf(jsonResponse);

			try {
				log.info("  send payload: " + responseMessage);
				user.getRemote().sendString(responseMessage);
			} catch (IOException e) {
				log.warn("  Caught IOException.");
				throw new ResparqlSendException(e);
			}
		}, executorService)
		.exceptionally((exception) ->
			handleExceptionAndSendResponse(exception, user, API_VAL_COMMAND_SEARCH, callbackId)
		);
	}

	private void executeQuery(Session user, JSONObject jsonMessage) {
		log.info("executeQuery()");

		String queryString = jsonMessage.getString(API_KEY_QUERY_TEXT);
		int callbackId = jsonMessage.getInt(API_KEY_CALLBACK_ID);

		CompletableFuture.supplyAsync(() -> {
			log.info("  executing query");

			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			Query query = QueryFactory.create(queryString);

			try(QueryExecution qe = queryToQueryExecution.apply(query)) {
				ResultSet results = qe.execSelect();
				ResultSetFormatter.outputAsJSON(baos, results);
			} catch(QueryExceptionHTTP e) {
				log.warn("  Caught QueryExceptionHTTP");
				throw new ResparqlNoEndpointException(e);
			} catch(Exception e) {
				log.warn("  Caught unexpected exception. This will probably be fatal.");
				throw e;
			}

			return baos.toString();
		}, executorService)
		.thenAcceptAsync((jsonSolutionsStr) -> {
			log.info("  sending response");
			JSONObject jsonSolutions = new JSONObject(jsonSolutionsStr);
			JSONObject jsonResponse = new JSONObject();
			jsonResponse.put(API_KEY_CALLBACK_ID, callbackId);
			jsonResponse.put(API_KEY_EXECUTE_VIRTUOSO_RESPONSE, jsonSolutions);
			String responseMessage = String.valueOf(jsonResponse);
			log.info("  payload: " + responseMessage);

			try {
				user.getRemote().sendString(responseMessage);
			} catch (IOException e) {
				throw new ResparqlSendException(e);
			}
		}, executorService)
		.exceptionally((exception) ->
			handleExceptionAndSendResponse(exception, user, API_VAL_COMMAND_EXECUTE, callbackId)
		);
	}

	private void executeReveng(Session user, JSONObject jsonMessage) {
		log.info("executeRevEng() " + jsonMessage.toString());

		JSONObject jsonPBindings = jsonMessage.getJSONObject(API_KEY_REVENG_P_BINDINGS);
		JSONObject jsonNBindings = jsonMessage.getJSONObject(API_KEY_REVENG_N_BINDINGS);
		JSONArray jsonBadUris = jsonMessage.getJSONArray(API_KEY_REVENG_BAD_URIS);
		int callbackId = jsonMessage.getInt(API_KEY_CALLBACK_ID);

		CompletableFuture.supplyAsync(() -> {
			ResultSet pResultSet =
					ResultSetFactory.fromJSON(new ByteArrayInputStream(jsonPBindings.toString().getBytes(StandardCharsets.UTF_8)));
			ResultSet nResultSet =
					ResultSetFactory.fromJSON(new ByteArrayInputStream(jsonNBindings.toString().getBytes(StandardCharsets.UTF_8)));

			List<QuerySolution> pSols = new ArrayList<>();
			List<QuerySolution> nSols = new ArrayList<>();
			while(pResultSet.hasNext()) { pSols.add(pResultSet.next()); }
			while(nResultSet.hasNext()) { nSols.add(nResultSet.next()); }

			List<String> badUris = new ArrayList<>();
			Iterator<Object> iter = jsonBadUris.iterator();
			while(iter.hasNext()) {
				String uri = (String) iter.next();
				badUris.add(uri);
			}

			log.info("  badUris = " + badUris);

			return new LearnerDirector(pSols, nSols, badUris, queryToQueryExecution).learn();
		}, executorService)
		.thenAcceptAsync((response) -> {

			if(response.getStatus().equals(RevengStatus.FAILURE) || !response.getOptLearnedTree().isPresent()) {
				sendRevengError(user, callbackId, response);
				return;
			}

			AOTree learnedTree = response.getOptLearnedTree().get();
			Op learnedOp = UtilsJena.convertAOFTreeToOp(learnedTree);
			Query learnedQuery = OpAsQuery.asQuery(learnedOp);

			log.info("ready with learned tree...");
			try {
				JSONObject resultObject = new JSONObject();
				resultObject.put(API_KEY_COMMAND, API_VAL_COMMAND_REVENG);
				resultObject.put(API_KEY_REVENG_LEARNED_QUERY, learnedQuery.toString());
				resultObject.put(API_KEY_CALLBACK_ID, callbackId);

				// prepare json array of uris used:
				Set<String> urisUsed = UtilsJena.urisInTriples(learnedTree.getTriplesInSubtree());
				JSONArray jsonUris = new JSONArray();

				for(String uri : urisUsed) {
					jsonUris.put(uri);
				}

				resultObject.put(API_KEY_REVENG_URIS_USED, jsonUris);

				// send a parseable representation of the query:
				JSONArray jsonTriples = new JSONArray();

				for(Triple triple : learnedTree.getTriplesInSubtree()) {
					JSONObject jsonTriple = new JSONObject();
					jsonTriple.put("s", triple.getSubject().toString());
					jsonTriple.put("p", triple.getPredicate().toString());
					jsonTriple.put("o", triple.getObject().toString());
					jsonTriples.put(jsonTriple);
				}

				resultObject.put("triples", jsonTriples);

				log.info("sending: " + resultObject.toString());

				user.getRemote().sendString(resultObject.toString());
			} catch (Exception e) {
				e.printStackTrace();
			}
		}, executorService)
		.exceptionally((error) -> {
			log.info("Error:" + error.getMessage());
			error.printStackTrace();
			return null;
		});
	}

	private static void sendRevengError(Session user, int callbackId, URevengResponse response) {
		log.info("sendRevengError()");

		JSONObject resultObject = new JSONObject();
		resultObject.put(API_KEY_STATUS, API_VAL_STATUS_ERROR);
		resultObject.put(API_KEY_MESSAGE, response.getMessage());
		resultObject.put(API_KEY_COMMAND, API_VAL_COMMAND_REVENG);
		resultObject.put(API_KEY_CALLBACK_ID, callbackId);
		resultObject.put(API_KEY_REVENG_LEARNED_QUERY, "");

		log.info("sendRevengError: " + resultObject);

		try {
			user.getRemote().sendString(resultObject.toString());
		} catch (IOException e) {
			log.warn("Could not send payload. Giving up.");
			e.printStackTrace();
		}
	}

	private static Void handleExceptionAndSendResponse(Throwable exception,
			Session user, String command, int callbackId) {
		log.info("handleExceptionAndSendResponse()");

		if(exception instanceof ResparqlNoEndpointException) {
			log.warn("ResparqlNoEndpointException");
			String responseMessage = makeErrorResponseMessage(
				command, callbackId, ApiErrorType.NO_SPARQL_ENDPOINT);
			log.info("payload: " + responseMessage);
			try {
				user.getRemote().sendString(responseMessage);
			} catch (IOException e) {
				log.warn("Could not send payload. Giving up.");
				e.printStackTrace();
			}
		} else if(exception instanceof ResparqlSendException) {
			log.warn("ResparqlSendException");
		} else {
			log.warn("Unexpected exception: " + exception);
			exception.printStackTrace();
		}
		return null;
	}

	private static String makeErrorResponseMessage(String command, int callbackId,
			ApiErrorType apiErrorType) {
		JSONObject jsonResponse = new JSONObject();
		jsonResponse.put(API_KEY_CALLBACK_ID, callbackId);
		jsonResponse.put(API_KEY_COMMAND, command);
		jsonResponse.put(API_KEY_STATUS, API_VAL_STATUS_ERROR);

		switch (apiErrorType) {
		case NO_SPARQL_ENDPOINT:
			jsonResponse.put(API_KEY_MESSAGE, API_VAL_MESSAGE_ERROR);
			break;
		default:
			log.error("Unknown ApiErrorType!");
			throw new RuntimeException();
		}

		return String.valueOf(jsonResponse);
	}

	public static LearnerController getInstance() {
		if(INSTANCE == null) {
			INSTANCE = new LearnerController();
		}
		return INSTANCE;
	}

}
