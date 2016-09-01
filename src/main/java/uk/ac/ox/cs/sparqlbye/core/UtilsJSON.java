package uk.ac.ox.cs.sparqlbye.core;

public abstract class UtilsJSON {




//	private static final Logger log = Logger.getLogger(Utils.class);
//
//	private static final String SPARQL_JSON_TYPE_KEY = "type";
//	private static final String SPARQL_JSON_TYPE_URI = "uri";
//	private static final String SPARQL_JSON_TYPE_LITERAL = "literal";
//
//	private static final String SPARQL_JSON_VALUE_KEY = "value";
//	private static final String SPARQL_JSON_XMLLANG_KEY = "xml:lang";

//	public static List<QuerySolution> parseJsonSolutions(JSONArray jsonArray) throws Exception {
//		log.info("Utils.parseJSONSolutions: " + jsonArray);
//		List<QuerySolution> solutions = new ArrayList<>();
//
//		Iterator<Object> iter = jsonArray.iterator();
//		while(iter.hasNext()) {
//			Object obj = iter.next();
//			if(obj instanceof JSONObject) {
//				JSONObject jsonMapping = (JSONObject) obj;
//
//				QuerySolution solution = parseJsonSolution(jsonMapping);
//				solutions.add(solution);
//			}
//		}
//		return solutions;
//	}

//	public static QuerySolution parseJsonSolution(JSONObject jsonObject) throws Exception {
//		QuerySolutionMap solutionMap = new QuerySolutionMap();
//
//		Iterator<String> keys = jsonObject.keys();
//		while(keys.hasNext()) {
//			String key = keys.next();
//			Object obj = jsonObject.get(key);
//
//			// Expecting a JSON object of the form: { "type": "uri", "value": "http://www.w3.org/1999/02/22-rdf-syntax-ns#type" }
//			if(obj instanceof JSONObject) {
//				JSONObject jsonObj = (JSONObject) obj;
//
//				String bindingType  = jsonObj.getString(SPARQL_JSON_TYPE_KEY);
//				String bindingValue = jsonObj.getString(SPARQL_JSON_VALUE_KEY);
//
//				Node node = null;
//				switch(bindingType) {
//				case SPARQL_JSON_TYPE_URI:
//					node = NodeFactory.createURI(bindingValue);
//					break;
//				case SPARQL_JSON_TYPE_LITERAL:
//					String bindingLang = jsonObject.optString(SPARQL_JSON_XMLLANG_KEY);
//					node = NodeFactory.createLiteral(bindingValue, bindingLang);
//					break;
//				default:
//					throw new Exception("unknown bindingType: " + bindingType);
//				}
//
//
//
//			} else {
//				throw new JSONException("illegal format");
//			}
//
//			solutionMap.add(key, null);
//		}
//
//		return null;
//	}

}
