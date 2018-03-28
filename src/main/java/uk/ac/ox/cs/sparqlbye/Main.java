package uk.ac.ox.cs.sparqlbye;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.SimpleLayout;

import uk.ac.ox.cs.sparqlbye.service.LearnerController;
import uk.ac.ox.cs.sparqlbye.service.LearnerWebSocketHandler;
import spark.Spark;

public class Main {

	public static void main(String[] args) {
		Logger rootLogger = Logger.getRootLogger();
		rootLogger.setLevel(Level.INFO);
		rootLogger.addAppender(new ConsoleAppender(new SimpleLayout()));

        LearnerController.getInstance().setParamLocalSparqlServiceEndpoint("http://localhost:8890/sparql");
        LearnerController.getInstance().setParamGraphUri("http://dbpedia.org/");

//		Spark.port(Integer.valueOf( System.getenv("PORT") ));
		Spark.port(5555);
		Spark.staticFileLocation("/public");
		Spark.webSocket("/learning", LearnerWebSocketHandler.class);
		Spark.init();
	}

}
