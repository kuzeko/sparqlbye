package uk.ac.ox.cs.sparqlbye.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QuerySolution;

public class LearnerBase {

	protected final Set<QuerySolution>              positiveSolutions;
	protected final Set<QuerySolution>              negativeSolutions;
	protected final List<String>                    badUris;
	protected final Function<Query, QueryExecution> queryToQueryExecution;

	protected AOTree learnedTree;

	public LearnerBase(
			Set<QuerySolution>              positiveSolutions,
			Set<QuerySolution>              negativeSolutions,
			List<String>                    badUris,
			Function<Query, QueryExecution> queryToQueryExecution) {
		this.positiveSolutions     = new HashSet<>(positiveSolutions);
		this.negativeSolutions     = new HashSet<>(negativeSolutions);
		this.badUris               = new ArrayList<>(badUris);
		this.queryToQueryExecution = queryToQueryExecution;
	}

	public Optional<AOTree> learn() {
		throw new IllegalStateException();
	}

}
