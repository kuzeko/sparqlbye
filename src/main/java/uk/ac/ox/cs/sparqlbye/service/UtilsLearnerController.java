package uk.ac.ox.cs.sparqlbye.service;

import org.apache.jena.graph.Triple;
import org.apache.log4j.Logger;

/**
 * Static methods for the LearnerController class.
 *
 * @author gdiazc
 */
abstract class UtilsLearnerController {
	private static final Logger log = Logger.getLogger(UtilsLearnerController.class);

	static final String KEYWORD_SEARCH_QUERY_VAR_URI = "uri";
	static final String KEYWORD_SEARCH_QUERY_VAR_LABEL = "label";
	static final String KEYWORD_SEARCH_QUERY_VAR_TYPE = "type";
	static final String URI_RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type";
    static final String URI_RDFS_LABEL = "http://www.w3.org/2000/01/rdf-schema#label";
    static final String URI_RDFS_SUBCLASSOF = "http://www.w3.org/2000/01/rdf-schema#subClassOf";

    private static String wrapUri(String str) {
        return "<" + str + ">";
    }

	static String makeKeywordSearchQuery(String keywordsString, String graphUri) {
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

		return
			"select " + uri + " " + label + " " + type + " "
			+ "from " + wrapUri(graphUri) + " where { "
			+ uri + " " + wrapUri(URI_RDFS_LABEL) + " " + label + " . "
			+ label + " <bif:contains> " + "'" + keywordsString + "' . "
			+ "{ "
			+ "select " + uri + " " + "(MIN(STR(?auxType)) as " + type + ") "
			+ "where { " + uri + " " + wrapUri(URI_RDF_TYPE) + " ?auxType } "
			+ "group by " + uri + " "
			+ "} "
			+ "} "
			+ "limit 10";
	}

    static String makeSuperclassQuery(String cl, String varName, String graphUri) {
        log.info("makeSuperclassQuery(): Make a query that finds superclasses of a given class.");

        return String.format("select %s from <%s> where { <%s> <%s> %s }",
                q(varName), graphUri, cl, URI_RDFS_SUBCLASSOF, q(varName));
    }

	private static String q(String varName) {
		return "?" + varName;
	}

}
