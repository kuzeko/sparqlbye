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

	final Set<QuerySolution>              positiveSolutions;
	final Set<QuerySolution>              negativeSolutions;
	final List<String>                    badUris;
	final Function<Query, QueryExecution> queryToQueryExecution;

	AOTree learnedTree;

	LearnerBase(
			Set<QuerySolution> positiveSolutions,
			Set<QuerySolution> negativeSolutions,
			List<String> badUris,
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
