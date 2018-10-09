package uk.ac.ox.cs.sparqlbye.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

import org.apache.jena.graph.NodeFactory;
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
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.engine.http.QueryExceptionHTTP;
import org.apache.jena.sparql.expr.ExprAggregator;
import org.apache.jena.sparql.expr.ExprVar;
import org.apache.jena.sparql.expr.aggregate.Aggregator;
import org.apache.jena.sparql.expr.aggregate.AggregatorFactory;
import org.apache.jena.sparql.syntax.Element;
import org.apache.jena.sparql.syntax.ElementGroup;
import org.apache.jena.sparql.syntax.ElementPathBlock;
import org.apache.log4j.Logger;
import org.eclipse.jetty.websocket.api.Session;
import org.json.JSONArray;
import org.json.JSONObject;

import uk.ac.ox.cs.sparqlbye.core.*;
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
    private static final String API_VAL_COMMAND_GETNEGS = "getnegs";

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

	private String paramLocalSparqlServiceEndpoint;
	private String paramGraphUri;

	private final Function<Query, QueryExecution> queryToQueryExecution = (query) ->
	QueryExecutionFactory.sparqlService(paramLocalSparqlServiceEndpoint, query);

	private static LearnerController INSTANCE;

//	private final List<Session>   sessions;
	private final ExecutorService executorService;

	private LearnerController() {
		log.info("LearnerController()");
//		sessions        = new ArrayList<>();
		executorService = Executors.newFixedThreadPool(10);
	}

	public void setParamLocalSparqlServiceEndpoint(String paramLocalSparqlServiceEndpoint) {
        this.paramLocalSparqlServiceEndpoint = paramLocalSparqlServiceEndpoint;
    }

    public void setParamGraphUri(String paramGraphUri) {
        this.paramGraphUri = paramGraphUri;
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
//		sessions.add(user);
	}

	private void onClose(Session user, int statusCode, String reason) {
		log.info("LearnerController.onClose(user, "+statusCode+", "+reason+")");
//		sessions.remove(user);
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
            case API_VAL_COMMAND_REVENG:
                executeReveng(user, jsonMessage); break;
            case API_VAL_COMMAND_GETNEGS:
                executeGetNegs(user, jsonMessage); break;
            case API_VAL_COMMAND_EXECUTE:
                executeQuery(user, jsonMessage);  break;
            case API_VAL_COMMAND_SEARCH:
                executeSearch(user, jsonMessage); break;
            default:
                throw new IllegalArgumentException("Unrecognised command: " + commandStr);
        }
	}

    private void executeGetNegs(Session user, JSONObject jsonMessage) {
//        getNegExamplesOutDegreeRanking(user, jsonMessage);
//        getNegExamplesTypesRanking(user, jsonMessage);
        getNegExamplesTrivial(user, jsonMessage);
    }

    private static class ReturnPacket {
        URevengResponse response;
        AOTree relaxedTree;

        ReturnPacket(URevengResponse response, AOTree relaxedTree) {
	        this.response = response;
	        this.relaxedTree = relaxedTree;
        }
    }

    private void executeReveng(Session user, JSONObject jsonMessage) {
        log.info("LearnerController.executeReveng() " + jsonMessage.toString());

        JSONObject jsonPBindings = jsonMessage.getJSONObject(API_KEY_REVENG_P_BINDINGS);
        JSONObject jsonNBindings = jsonMessage.getJSONObject(API_KEY_REVENG_N_BINDINGS);
        JSONArray jsonBadUris = jsonMessage.getJSONArray(API_KEY_REVENG_BAD_URIS);
        int callbackId = jsonMessage.getInt(API_KEY_CALLBACK_ID);

        CompletableFuture.supplyAsync(() -> {
            log.info("async step 1");

            ResultSet pResultSet = ResultSetFactory.fromJSON(
                    new ByteArrayInputStream(jsonPBindings.toString().getBytes(StandardCharsets.UTF_8)));
            ResultSet nResultSet = ResultSetFactory.fromJSON(
                    new ByteArrayInputStream(jsonNBindings.toString().getBytes(StandardCharsets.UTF_8)));

            List<QuerySolution> pSols = new ArrayList<>();
            List<QuerySolution> nSols = new ArrayList<>();
            while(pResultSet.hasNext()) { pSols.add(pResultSet.next()); }
            while(nResultSet.hasNext()) { nSols.add(nResultSet.next()); }

            List<String> badUris = new ArrayList<>();
            for(Object jsonBadUri : jsonBadUris) {
                String uri = (String) jsonBadUri;
                badUris.add(uri);
            }

            return new LearnerDirector(pSols, nSols, badUris, queryToQueryExecution).learn();
        }, executorService)
		.thenApply((response) -> {
			log.info("async step 1.5");

			if(response.getStatus().equals(RevengStatus.FAILURE) || !response.getOptLearnedTree().isPresent()) {
				sendRevengError(user, callbackId, response);
				return response;
			}

            AOTree learnedTree = response.getOptLearnedTree().get();

			// TODO: Generate a relaxed version of the query here, as it's much easier.
            AOTree relaxedTree = makeRelaxedTree(learnedTree);

            return new ReturnPacket(response, relaxedTree);
		})
        .thenAcceptAsync((returnPacket) -> {
            log.info("async step 3");

            // Unpack ReturnPacket:
            URevengResponse response = ((ReturnPacket) returnPacket).response;
            AOTree relaxedTree = ((ReturnPacket) returnPacket).relaxedTree;

            if(response.getStatus().equals(RevengStatus.FAILURE) || !response.getOptLearnedTree().isPresent()) {
                sendRevengError(user, callbackId, response);
                return;
            }

            Query relaxedQuery = relaxedTree == null ? null : OpAsQuery.asQuery(UtilsJena.convertAOTreeToOp(relaxedTree));
            log.warn("relaxed query:");
            log.warn(relaxedQuery);

            AOTree learnedTree = response.getOptLearnedTree().get();
            Op learnedOp = UtilsJena.convertAOTreeToOp(learnedTree);
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

                resultObject.put("relaxedQuery", relaxedQuery == null ? learnedQuery : relaxedQuery.toString());

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

    private void getNegExamplesOutDegreeRanking(Session user, JSONObject jsonMessage) {
        log.info("LearnerController.getNegExamplesOutDegreeRanking()");

        String queryString = jsonMessage.getString(API_KEY_QUERY_TEXT);
        int callbackId = jsonMessage.getInt(API_KEY_CALLBACK_ID);

        CompletableFuture.supplyAsync(() -> {
            log.info("async step 1");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Query query = QueryFactory.create(queryString);

            // Modify the original query:
            Query clone = query.cloneQuery();
            Element queryElement = clone.getQueryPattern();
            ElementGroup elementGroup = (ElementGroup) queryElement;
            List<Element> list = elementGroup.getElements();
            Element element = list.get(0);
            ElementPathBlock pathBlock = (ElementPathBlock) element;

            // Add z to obtain classes for the first variable (?x) only:
            pathBlock.addTriple(
                Triple.create( Var.alloc("x"), Var.alloc("p"), Var.alloc("z") )
            );

            clone.addGroupBy(Var.alloc("x"));

            Aggregator aggrCount =
                    AggregatorFactory.createCountExpr(true, new ExprVar(Var.alloc("z")));
            ExprAggregator expraggrCount = new ExprAggregator(null, aggrCount);

            clone.addOrderBy(new ExprVar(Var.alloc("zs")), -1);
            clone.addResultVar(Var.alloc("x"));
            clone.setQueryResultStar(false);

            clone.getAggregators().add(expraggrCount);
            clone.getProject().add(Var.alloc("zs"), expraggrCount);

//            log.info(clone.getResultVars().toString());
//            log.info(clone.getAggregators().toString());
//            log.info(clone.getProject().toString());
            log.info(clone.toString());

            query = clone;

            try(QueryExecution qe = queryToQueryExecution.apply(query)) {
                ResultSet results = qe.execSelect();
                ResultSetFormatter.outputAsJSON(baos, results);
            } catch(QueryExceptionHTTP e) {
                log.warn("Caught QueryExceptionHTTP");
                throw new ResparqlNoEndpointException(e);
            } catch(Exception e) {
                log.error("Caught unexpected exception. This will probably be fatal.");
                throw e;
            }

            return baos.toString();
        }, executorService)
        .thenAcceptAsync((jsonSolutionsStr) -> {
            log.info("async step 2");
            JSONObject jsonSolutions = new JSONObject(jsonSolutionsStr);
            JSONObject jsonResponse = new JSONObject();
            jsonResponse.put(API_KEY_CALLBACK_ID, callbackId);
            jsonResponse.put(API_KEY_EXECUTE_VIRTUOSO_RESPONSE, jsonSolutions);
            String responseMessage = String.valueOf(jsonResponse);
            log.info("  payload: " + responseMessage.substring(0, Math.min(responseMessage.length(), 1000)));

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

	private void getNegExamplesTypesRanking(Session user, JSONObject jsonMessage) {
		log.info("LearnerController.getNegExamplesTypesRanking()");

		String queryString = jsonMessage.getString(API_KEY_QUERY_TEXT);
		int callbackId = jsonMessage.getInt(API_KEY_CALLBACK_ID);

		CompletableFuture.supplyAsync(() -> {
			log.info("async step 1");

			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			Query query = QueryFactory.create(queryString);

			// Part 1: Create a dummy query just to test:
//            String queryString2 = "SELECT ?x (GROUP_CONCAT(?xType; SEPARATOR=\",\") AS ?xTypes)\n" +
//                    "WHERE {\n" +
//                    "  ?x  a                     <http://dbpedia.org/ontology/Country> .\n" +
//                    "  ?x a ?xType } GROUP BY ?x";
//            Query q2 = QueryFactory.create(queryString2);

//            Query clone = query.cloneQuery();
//			log.warn(q2.toString());
//
//			ExprAggregator ea = q2.getAggregators().get(0);
//			log.warn("FIRST");
//			log.warn(q2.getAggregators().toString());
//			log.warn(q2.getResultVars().toString());
//			log.warn(q2.getProject().toString());

            // Part 2: Try to modify the original query (work on a copy for now).
//            log.warn("SECOND");
            Query clone = query.cloneQuery();
            Element queryElement = clone.getQueryPattern();
            ElementGroup elementGroup = (ElementGroup) queryElement;
            List<Element> list = elementGroup.getElements();
            Element element = list.get(0);
            ElementPathBlock pathBlock = (ElementPathBlock) element;

            // Add xType to obtain classes for the first variable (?x) only:
            pathBlock.addTriple(
                    Triple.create(
                            Var.alloc("x"),
                            NodeFactory.createURI(UtilsLearnerController.URI_RDF_TYPE),
                            Var.alloc("xType")
                    )
            );

            // TODO: Implement the two-variable case. Desired end query:
            /*
            SELECT ?x ?y (COUNT(?xType) AS ?xTypes)  // part 1: add ?y to projection
            { ?x  a                     <http://dbpedia.org/ontology/Writer> .
            ?x a ?xType
            OPTIONAL
            { ?x  <http://dbpedia.org/ontology/deathPlace>  ?y }
            }
            GROUP BY ?x ?y   // part 2: add ?y to group by
            ORDER BY DESC(?xTypes)
            LIMIT 100
             */

            clone.addGroupBy(Var.alloc("x"));
//            Aggregator aggr = AggregatorFactory.createGroupConcat(false,new ExprVar(Var.alloc("xType")),",",null);
//            ExprAggregator expraggr = new ExprAggregator(Var.alloc(".0"), aggr);

            Aggregator aggrCount = //AggregatorFactory.createCount(true);
            AggregatorFactory.createCountExpr(true, new ExprVar(Var.alloc("xType")));
            ExprAggregator expraggrCount = new ExprAggregator(null, aggrCount);

            clone.addOrderBy(new ExprVar(Var.alloc("xTypes")), -1);

            clone.addResultVar(Var.alloc("x"));
            clone.setQueryResultStar(false);

            clone.getAggregators().add(expraggrCount);
            clone.getProject().add(Var.alloc("xTypes"), expraggrCount);

            log.warn(clone.getResultVars().toString());
//            ExprAggregator ea2 = clone.getAggregators().get(0);
            log.error(clone.getAggregators().toString());
            log.warn(clone.getProject().toString());

            log.warn(clone.toString());

            query = clone;

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
			log.info("  payload: " + responseMessage.substring(0, Math.min(responseMessage.length(), 1000)));

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

    private void getNegExamplesTrivial(Session user, JSONObject jsonMessage) {
        log.info("LearnerController.getNegExamplesTrivial()");

        String queryString = jsonMessage.getString(API_KEY_QUERY_TEXT);
        int callbackId = jsonMessage.getInt(API_KEY_CALLBACK_ID);

        CompletableFuture.supplyAsync(() -> {
            log.info("async step 1");

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
            log.info("async step 2");
            JSONObject jsonSolutions = new JSONObject(jsonSolutionsStr);
            JSONObject jsonResponse = new JSONObject();
            jsonResponse.put(API_KEY_CALLBACK_ID, callbackId);
            jsonResponse.put(API_KEY_EXECUTE_VIRTUOSO_RESPONSE, jsonSolutions);
            String responseMessage = String.valueOf(jsonResponse);
            log.info("payload: " + responseMessage.substring(0, Math.min(responseMessage.length(), 1000)));

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

    /**
     * Execute a query as is.
     *
     * @param user
     * @param jsonMessage
     */
    private void executeQuery(Session user, JSONObject jsonMessage) {
        log.info("LearnerController.executeQuery()");

        String queryString = jsonMessage.getString(API_KEY_QUERY_TEXT);
        int callbackId = jsonMessage.getInt(API_KEY_CALLBACK_ID);

        CompletableFuture.supplyAsync(() -> {
            log.info("async step 1");

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
            log.info("async step 2");
            JSONObject jsonSolutions = new JSONObject(jsonSolutionsStr);
            JSONObject jsonResponse = new JSONObject();
            jsonResponse.put(API_KEY_CALLBACK_ID, callbackId);
            jsonResponse.put(API_KEY_EXECUTE_VIRTUOSO_RESPONSE, jsonSolutions);
            String responseMessage = String.valueOf(jsonResponse);
            log.info("payload: " + responseMessage.substring(0, Math.min(responseMessage.length(), 1000)));

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

    /**
     * Execute a keyword search on the graph and return the results to the user.
     *
     * @param user Session object representing the sender.
     * @param jsonMessage JSONObject containing a keyword field.
     */
    private void executeSearch(Session user, JSONObject jsonMessage) {
        log.info("executeSearch()");

        String keywordsString = jsonMessage.getString(API_KEY_QUERY_TEXT);
        int    callbackId     = jsonMessage.getInt(API_KEY_CALLBACK_ID);
        String queryString    = UtilsLearnerController.makeKeywordSearchQuery(keywordsString, paramGraphUri);
        Query  query          = QueryFactory.create(queryString);

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

    // TODO: this is research item 2
    private AOTree makeRelaxedTree(AOTree tree) {
        // Make a copy:
        AOTree copy = UtilsAOTrees.copy(tree);

        // Find class inclusion triple:
        Set<Triple> interestingTriples = new HashSet<>();
        for(Triple triple : tree.getTriplesInSubtree()) {
            // check for the form (?var, type, <uri>)
            if(triple.getSubject().isVariable()
                    && triple.getPredicate().isURI()
                    && triple.getPredicate().getURI().equals(UtilsLearnerController.URI_RDF_TYPE)
                    && triple.getObject().isURI()) {
                interestingTriples.add(triple);
            }
        }

        // Sample one:
        Triple chosenTriple = UtilsSets.getRandomElement(interestingTriples);
        if (chosenTriple == null) {
            log.warn("no interesiting triples found");
            return null;
        }

        String chosenClass = chosenTriple.getObject().getURI();

        // Get superclasses:
        final String varName = "c";
        String queryStr = UtilsLearnerController.makeSuperclassQuery(chosenClass, varName, paramGraphUri);
        Query query = QueryFactory.create(queryStr);
        Set<QuerySolution> solutions = new HashSet<>();

        // Execute the query:
        try(QueryExecution qe = queryToQueryExecution.apply(query)) {
            ResultSet results = qe.execSelect();

            while(results.hasNext()) {
                solutions.add(results.next());
            }
        } catch(QueryExceptionHTTP e) {
            log.warn("  Caught QueryExceptionHTTP");
            throw new ResparqlNoEndpointException(e);
        } catch(Exception e) {
            log.warn("  Caught unexpected exception. This will probably be fatal.");
            throw e;
        }

        QuerySolution chosenSolution = UtilsSets.getRandomElement(solutions);
        String superClass = (chosenSolution == null) ? null : chosenSolution.get(varName).toString();

        log.warn("Going to replace " + chosenClass + " by " + superClass);

        return (superClass == null) ? null : UtilsAOTrees.copyAndReplace(copy, chosenClass, superClass);
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

	private static String makeErrorResponseMessage(String command, int callbackId, ApiErrorType apiErrorType) {
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
