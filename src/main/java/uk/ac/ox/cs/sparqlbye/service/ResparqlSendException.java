package uk.ac.ox.cs.sparqlbye.service;

public class ResparqlSendException extends ResparqlException {
	private static final long serialVersionUID = 988L;
	public ResparqlSendException()                                  { super(); }
	public ResparqlSendException(String message)                    { super(message); }
	public ResparqlSendException(Throwable cause)                   { super(cause); }
    public ResparqlSendException(String message, Throwable cause)   { super(message, cause); }
}
