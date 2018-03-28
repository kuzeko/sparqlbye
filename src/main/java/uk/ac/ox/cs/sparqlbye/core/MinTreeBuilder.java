package uk.ac.ox.cs.sparqlbye.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.jena.graph.Triple;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.sparql.core.Var;

public final class MinTreeBuilder {

	private final Set<QuerySolution> pSols;
	private final List<String> badUris;
	private final Function<Query, QueryExecution> queryToQueryExecution;

	MinTreeBuilder(
			Collection<QuerySolution> pSols,
			List<String> badUris,
			Function<Query, QueryExecution> queryToQueryExecution) {
		this.pSols = new HashSet<>(pSols);
		this.badUris = new ArrayList<>(badUris);
		this.queryToQueryExecution = Objects.requireNonNull(queryToQueryExecution);
	}

	public AOTree buildMinTree() {
		AOTree learnedTree = UtilsLearner.buildTemplateTree(pSols);
		buildNode(learnedTree);
		return learnedTree;
	}

	private boolean buildNode(AOTree node) {
		// Prepare signatures (domains):
		Set<Var> scopedVars = node.getDesiredScopedVars();
		Set<Var> higherVars = node.getDesiredHigherVars();
		Set<Var> newVars = node.getDesiredTopVars();

		Set<QuerySolution> killableSolutions = pSols.stream()
				.filter(solution -> {
					Set<Var> domain = UtilsJena.dom(solution);
					return domain.containsAll(higherVars)
							&& newVars.stream().noneMatch(domain::contains);
				})
				.collect(Collectors.toSet());

		System.out.println("Learner> node: scopedVars = " + scopedVars + ",  newVars = " + newVars);

		// Prepare new node:
		Set<Triple> triples = new HashSet<>();
		Set<Var> mentionedVars = new HashSet<>();
		boolean nodeReady = false;

		System.out.println("Learner> " + killableSolutions.size() + " killable solutions.");

		// Prepare TripleTypeLooper:
		UtilsLearner.UTripleTypeLooper looper = new UtilsLearner.UTripleTypeLooper(scopedVars);

		while(!nodeReady && looper.hasNext()) {
			org.apache.commons.lang3.tuple.Triple<Var, Var, Var> tripleType = looper.next();

			// Make an appropriate TripleProducer:
			UtilsLearner.UTripleProducer tripleProducer = new UtilsLearner.UTripleProducer(
					tripleType.getLeft(), tripleType.getMiddle(), tripleType.getRight(),
					scopedVars, pSols, badUris, queryToQueryExecution);

			while(!nodeReady && tripleProducer.hasNext()) {
				Triple nextTriple = tripleProducer.next(); // this triple works. does it kill?
				Set<Var> tDom = UtilsJena.dom(nextTriple);

				// TODO: this is an ugly hack for ignoring the very uninformative triple ?s rdf:type owl:Thing. fix it!
				if(nextTriple.getPredicate().getURI().equals("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")
						&& nextTriple.getObject().getURI().equals("http://www.w3.org/2002/07/owl#Thing")) {
					continue;
				}

				Set<QuerySolution> killed =
						UtilsLearner.killedByTriples(nextTriple, triples, killableSolutions, queryToQueryExecution);
				killableSolutions.removeAll(killed);

				if( !killed.isEmpty() || !mentionedVars.containsAll(tDom) ) {
					System.out.println("Adding triple pattern: " + nextTriple);
					triples.add(nextTriple);
					mentionedVars.addAll(tDom);
				}

				if( killableSolutions.isEmpty() && mentionedVars.containsAll(newVars) ) {
					System.out.println("Learner: Triples = " + triples);
					System.out.println("Learner: Proceed to next node.");
					nodeReady = true;
//					break outer;
				}
			}

			tripleProducer.close();
		}

		for(Triple t : triples) {
			node.addTriple(t);
		}

		if(nodeReady) {
			boolean ans = true;
			for(AOTree child : node.getChildren()) {
				ans = ans && buildNode(child);
			}

			return ans;
		} else {
			System.out.println("Malformed node!");
			return false;
		}
	}


}
