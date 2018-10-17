package uk.ac.ox.cs.sparqlbye;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import static java.lang.String.format;
import java.util.Properties;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import static org.apache.log4j.Level.DEBUG;
import static org.apache.log4j.Level.ERROR;
import static org.apache.log4j.Level.FATAL;
import static org.apache.log4j.Level.INFO;
import static org.apache.log4j.Level.WARN;
import org.apache.log4j.Logger;
import org.apache.log4j.SimpleLayout;

import uk.ac.ox.cs.sparqlbye.service.LearnerController;
import uk.ac.ox.cs.sparqlbye.service.LearnerWebSocketHandler;
import spark.Spark;

public class Main {

    //the base folder is ./, the root of the main properties file  
    public static final String CONF_FILE = "./sparqlbye.properties";
    public static Logger rootLogger;

    public static void main(String[] args) {

        rootLogger = Logger.getRootLogger();
        rootLogger.setLevel(Level.INFO);
        rootLogger.addAppender(new ConsoleAppender(new SimpleLayout()));

        //to load application's properties, we use this class
        Properties mainProperties = new Properties();

        boolean loadedConf = false;
        //load the file handle for main.properties
        try (FileInputStream confFileStream = new FileInputStream(CONF_FILE)) {
            //load all the properties from this file
            mainProperties.load(confFileStream);
            //we have loaded the properties, so close the file handle
            loadedConf = true;
        } catch (FileNotFoundException ex) {
            error("Conf File '%s' could not be found", CONF_FILE);            
        } catch (IOException ex) {
            error("Errors reading Conf File '%s'", CONF_FILE, ex);            
        }
        
        if(!loadedConf){
           error("Problems in loading configuration! Quitting...");
           System.exit(2);
        }
        String logLevel = mainProperties.getProperty("core.loglevel", "INFO");
        switch (logLevel) {
            case "WARN":
                rootLogger.setLevel(Level.WARN);                
                break;
            case "DEBUG":
                rootLogger.setLevel(Level.DEBUG);                
                break;                
            case "INFO":                
            default:
                rootLogger.setLevel(Level.INFO);                
                break;
        }
        
        
        String sparqlEndpoint = mainProperties.getProperty("core.endpoint", "http://localhost:8890/sparql");
        String graphUri = mainProperties.getProperty("core.graphUri", "http://dbpedia.org/");        
        Integer serverPort = 5555;
        try{
            serverPort = Integer.parseInt(mainProperties.getProperty("core.port", ""));            
            if(serverPort<80 || serverPort > 999999){
                throw new IllegalArgumentException("Value " + serverPort  + " is not a valid port number");
            }
        } catch (Exception ex){
            error("core.port is not a valid value", ex);
        }
        
        info("Configuration\n\t endpoint: '%s'\n\t graphUri: '%s'\n\t port: '%s'\n", sparqlEndpoint, graphUri, serverPort);
        
        
        LearnerController.getInstance().setParamLocalSparqlServiceEndpoint(sparqlEndpoint);
        LearnerController.getInstance().setParamGraphUri(graphUri);

        // https://github.com/perwendel/spark
        // Spark - a tiny web framework for Java 8        
        Spark.port(serverPort);
        Spark.staticFileLocation("/public");
        Spark.webSocket("/learning", LearnerWebSocketHandler.class);
        Spark.init();
    }
    
    
    
    
    
    
    
    
    
    
    

    /**
     * This code abstracts the concept of object with logging capabilities.
     *
     * @author Davide Mottin <mottin@disi.unitn.eu>
     */
    
    /**
     * Logging methods (wrappers of log4 with format string facilities)
     * @param message format string
     * @param args format value
     */
    protected static void debug(String message, Object... args) {
        log(DEBUG, null, message, args);
    }

    protected static void warn(String message, Object... args) {
        log(WARN, null, message, args);
    }

    protected static void fatal(String message, Object... args) {
        log(FATAL, null, message, args);
    }

    protected static void error(String message, Object... args) {
        log(ERROR, null, message, args);
    }

    protected static void info(String message, Object... args) {
        log(INFO, null, message, args);
    }

    protected static void debug(String message, Throwable ex, Object... args) {
        log(DEBUG, ex, message, args);
    }

    protected static void warn(String message, Throwable ex, Object... args) {
        log(WARN, ex, message, args);
    }

    protected static void fatal(String message, Throwable ex, Object... args) {
        log(FATAL, ex, message, args);
    }

    protected static void error(String message, Throwable ex, Object... args) {
        log(ERROR, ex, message, args);
    }

    protected static void info(String message, Throwable ex, Object... args) {
        log(INFO, ex, message, args);
    }

    protected static void log(Level level, String message, Object... args) {
        log(level, null, message, args);
    }

    
    /**
     * 
     * @param level LOG LEVEL
     * @param ex Exception
     * @param message String to format
     * @param args values to format
     */
    protected static void log(Level level, Throwable ex, String message, Object... args) {
        rootLogger.log(level, format(message, args), ex);
    }

}
