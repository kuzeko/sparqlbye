package uk.ac.ox.cs.sparqlbye.service;

public class ResparqlNoEndpointException extends ResparqlException {
	private static final long serialVersionUID = 987L;
	public ResparqlNoEndpointException()                                  { super(); }
	public ResparqlNoEndpointException(String message)                    { super(message); }
	public ResparqlNoEndpointException(Throwable cause)                   { super(cause); }
    public ResparqlNoEndpointException(String message, Throwable cause)   { super(message, cause); }
}
