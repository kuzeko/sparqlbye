package uk.ac.ox.cs.sparqlbye.core;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.OpAsQuery;

public class ULearnedQueryChecker {

	private AOTree                          learnedTree;
	private Set<QuerySolution>              pSols;
	private Set<QuerySolution>              nSols;
	private Function<Query, QueryExecution> getQueryExecution;
	private Set<QuerySolution>              pSolsBad;
	private Set<QuerySolution>              nSolsBad;
	private boolean                         isWellFormed;

	ULearnedQueryChecker(
			AOTree learnedTree,
			Collection<QuerySolution> positiveSolutions,
			Collection<QuerySolution> negativeSolutions,
			Function<Query, QueryExecution> getQueryExecution) {
		this.learnedTree = learnedTree;
		this.pSols = new HashSet<>(positiveSolutions);
		this.nSols = new HashSet<>(negativeSolutions);
		this.getQueryExecution = getQueryExecution;

		pSolsBad = new HashSet<>();
		nSolsBad = new HashSet<>();
		isWellFormed = UtilsAOTrees.isWellFormed(learnedTree);
	}

	public boolean checkLearnedQuery() {
		System.out.println("checkLearnedQuery()");

		pSolsBad.clear();
		nSolsBad.clear();

		if(!isWellFormed) {
			return false;
		}

		Op    learnedOp    = UtilsJena.convertAOTreeToOp(learnedTree);
		Query learnedQuery = OpAsQuery.asQuery(learnedOp);

		// Check positive examples:
		for(QuerySolution solution : pSols) {
			Query membershipQuery = UtilsJena.toMembershipQuery(learnedQuery, solution);

			System.out.println("checkLearnedQuery: membershipQuery = \n" + membershipQuery);

			try(QueryExecution queryExecution = getQueryExecution.apply(membershipQuery)) {
				if(!queryExecution.execAsk()) {
					pSolsBad.add(solution);
				}
			}
		}

		// Check negative examples:
		for(QuerySolution solution : nSols) {
			Query membershipQuery = UtilsJena.toMembershipQuery(learnedQuery, solution);

			try(QueryExecution queryExecution = getQueryExecution.apply(membershipQuery)) {
				if(queryExecution.execAsk()) {
					nSolsBad.add(solution);
				}
			}
		}

		return (pSolsBad.size() > 0 || nSolsBad.size() > 0);
	}

}
