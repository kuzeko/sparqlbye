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
import org.apache.log4j.Logger;

class ULearnedQueryChecker {
    private static final Logger log = Logger.getLogger(ULearnedQueryChecker.class);

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
		this.learnedTree       = learnedTree;
		this.pSols             = new HashSet<>(positiveSolutions);
		this.nSols             = new HashSet<>(negativeSolutions);
		this.getQueryExecution = getQueryExecution;
		this.pSolsBad          = new HashSet<>();
		this.nSolsBad          = new HashSet<>();
		this.isWellFormed      = UtilsAOTrees.isWellFormed(learnedTree);
	}

	boolean checkLearnedQuery() {
		log.info("ULearnedQueryChecker.checkLearnedQuery()");

		pSolsBad.clear();
		nSolsBad.clear();

		if(!isWellFormed) {
            log.info("ULearnedQueryChecker.checkLearnedQuery(): not well formed");
			return false;
		}

		Op    learnedOp    = UtilsJena.convertAOTreeToOp(learnedTree);
		Query learnedQuery = OpAsQuery.asQuery(learnedOp);

		// Check positive examples:
		for(QuerySolution solution : pSols) {
			Query membershipQuery = UtilsJena.toMembershipQuery(learnedQuery, solution);

			log.info("ULearnedQueryChecker.checkLearnedQuery(): membershipQuery = \n" + membershipQuery);

			try(QueryExecution queryExecution = getQueryExecution.apply(membershipQuery)) {
				if(!queryExecution.execAsk()) {
				    log.info("ULearnedQueryChecker.checkLearnedQuery(): failed!");
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

        return pSolsBad.isEmpty() && nSolsBad.isEmpty();
	}

}
