package uk.ac.ox.cs.sparqlbye.service;

import org.apache.log4j.Logger;

/**
 * Static methods for the LearnerController class.
 *
 * @author gdiazc
 */
abstract class UtilsLearnerController {
	private static final Logger log = Logger.getLogger(UtilsLearnerController.class);

	public static final String KEYWORD_SEARCH_QUERY_VAR_URI = "uri";
	public static final String KEYWORD_SEARCH_QUERY_VAR_LABEL = "label";
	public static final String KEYWORD_SEARCH_QUERY_VAR_TYPE = "type";

	public static String makeKeywordSearchQuery(String keywordsString, String graphUri) {
		log.info("makeKeyworkSearchQuery(): Simple implementation for keyword search!");

		//		String[] keywords = keywordsString.split(" ");

		//		String queryString = "select ?s ?label "
		//				+ "from <http://dbpedia.org/> "
		//				+ "where { "
		//				+ "?s <http://www.w3.org/2000/01/rdf-schema#label> ?label . "
		//				+ "?label <bif:contains> '" + keyword + "' "
		//				+ "} "
		//				+ "limit " + 10;

		String uri = q(KEYWORD_SEARCH_QUERY_VAR_URI);
		String label = q(KEYWORD_SEARCH_QUERY_VAR_LABEL);
		String type = q(KEYWORD_SEARCH_QUERY_VAR_TYPE);
		String RDF_TYPE = "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>";
		String RDFS_LABEL = "<http://www.w3.org/2000/01/rdf-schema#label>";

		return
			"select " + uri + " " + label + " " + type + " "
			+ "from <" + graphUri + "> where { "
			+ uri + " " + RDFS_LABEL + " " + label + " . "
			+ label + " <bif:contains> " + "'" + keywordsString + "' . "
			+ "{ "
			+ "select " + uri + " " + "(MIN(STR(?auxType)) as " + type + ") "
			+ "where { " + uri + " " + RDF_TYPE + " ?auxType } "
			+ "group by " + uri + " "
			+ "} "
			+ "} "
			+ "limit 10";
	}

	private static String q(String varName) {
		return "?" + varName;
	}

}
