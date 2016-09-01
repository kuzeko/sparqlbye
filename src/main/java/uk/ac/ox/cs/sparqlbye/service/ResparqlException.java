package uk.ac.ox.cs.sparqlbye.service;

public abstract class ResparqlException extends RuntimeException {
	private static final long serialVersionUID = 986L;
	public ResparqlException()                                  { super(); }
	public ResparqlException(String message)                    { super(message); }
	public ResparqlException(Throwable cause)                   { super(cause); }
    public ResparqlException(String message, Throwable cause)   { super(message, cause); }
}
