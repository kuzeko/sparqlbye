package uk.ac.ox.cs.sparqlbye.core;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.jena.query.QuerySolution;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.sparql.core.Var;

public abstract class UtilsQuerySolutions {

	public static int solutionsDepth(Collection<QuerySolution> solutions) {
		if(solutions == null) { throw new IllegalArgumentException(); }
		if(solutions.isEmpty()) { return 0; }

		Set<Var> fullDomain = UtilsJena.solutionsDomain(solutions);
		Set<Set<QuerySolution>> coverages = new HashSet<>();

		for(Var var : fullDomain) {
			Set<QuerySolution> coverage = coverage(var, solutions);
			coverages.add(coverage);
		}

		return UtilsSets.latticeDepth(coverages);
	}

	public static Set<QuerySolution> coverage(Var var, Collection<QuerySolution> solutions) {
		return solutions.stream()
				.filter(solution -> UtilsJena.dom(solution).contains(var))
				.collect(Collectors.toSet());
	}

	public static boolean checkQuerySolutionEquality(QuerySolution a, QuerySolution b) {
		if(a == null || b == null) { throw new IllegalArgumentException(); }
		if(a == b) { return true; }

		Set<Var> domA = UtilsJena.dom(a);
		Set<Var> domB = UtilsJena.dom(b);

		if(!domA.equals(domB)) { return false; }

		for(Var var : domA) {
			RDFNode nodeA = a.get(var.getName());
			RDFNode nodeB = b.get(var.getName());
			if(!nodeA.equals(nodeB)) {
				return false;
			}
		}

		return true;
	}

}
