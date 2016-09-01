package uk.ac.ox.cs.sparqlbye.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import org.apache.jena.graph.Triple;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.sparql.core.Var;
import org.apache.log4j.Logger;

import uk.ac.ox.cs.sparqlbye.core.UtilsLearner.URevengResponse;
import uk.ac.ox.cs.sparqlbye.core.UtilsLearner.URevengResponse.RevengStatus;
import uk.ac.ox.cs.sparqlbye.core.UtilsLearner.UTripleProducer;
import uk.ac.ox.cs.sparqlbye.core.UtilsLearner.UTripleTypeLooper;

public final class LearnerDirector {
	private static final Logger log = Logger.getLogger(LearnerDirector.class);

	private final Set<QuerySolution> pSols;
	private final Set<QuerySolution> nSols;
	private final List<String> badUris;
	private final Function<Query,QueryExecution> queryToQueryExecution;

	public LearnerDirector(
			Collection<QuerySolution> pSols,
			Collection<QuerySolution> nSols,
			List<String> badUris,
			Function<Query,QueryExecution> queryToQueryExecution) {
		this.pSols = new HashSet<>(pSols);
		this.nSols = new HashSet<>(nSols);
		this.badUris = new ArrayList<>(badUris);
		this.queryToQueryExecution = queryToQueryExecution;
	}

	public URevengResponse learn() {
		log.debug("LearnerDirector.learn()");

		int pDepth = UtilsQuerySolutions.solutionsDepth(pSols);
		Set<Var> pVars = UtilsJena.solutionsDomain(pSols);

		// Only accept maximal negative solutions:
		for(QuerySolution solution : nSols) {
			if(!UtilsJena.dom(solution).equals(pVars)) {
				return new URevengResponse(
						RevengStatus.FAILURE, Optional.empty(), "Invalid negative examples");
			}
		}

		if(pDepth == 0) {
			return learnHomogeneous();
		} else if(pDepth > 0) {
			return learnTreeLike();
		} else {
			return new URevengResponse(
					RevengStatus.FAILURE, Optional.empty(), "Unkown pDepth");
		}
	}

	private URevengResponse learnHomogeneous() {
		log.info("LearnerDirector.learnHomogeneous()");

		PNAndTreeLearner learner =
				new PNAndTreeLearner(pSols, nSols, badUris, queryToQueryExecution);

		Optional<AOTree> optLearnedTree = learner.learn();

		if(optLearnedTree.isPresent()) {
			return new URevengResponse(
					RevengStatus.SUCCESS, optLearnedTree, "success");
		} else {
			return new URevengResponse(
					RevengStatus.FAILURE, Optional.empty(), "PNAndTreeLearner found nothing");
		}
	}

	private URevengResponse learnTreeLike() {
		log.info("LearnerDirector.learnTreeLike()");

		// Build the min tree:
		AOTree minTree = new MinTreeBuilder(pSols, badUris, queryToQueryExecution).buildMinTree();

		// The min tree is ready. Check if it works on negative examples:
		List<QuerySolution> badNegSols = new ArrayList<>();

		for(QuerySolution solution : nSols) {
			if( UtilsLearner.execMembershipQuery(minTree, solution, queryToQueryExecution) ) {
				badNegSols.add(solution);
			}
		}

		if(badNegSols.isEmpty()) {
			return new URevengResponse(
					RevengStatus.SUCCESS, Optional.of(minTree), "success");
		}

		log.info("LearnerDirector.learnTreeLike: Going to have to pack more triples");

		// Fill tree until we kill all negative solutions:
		packNode(minTree, badNegSols);

		if(badNegSols.isEmpty()) {
			return new URevengResponse(
					RevengStatus.SUCCESS, Optional.of(minTree), "success");
		} else {
			return new URevengResponse(
					RevengStatus.FAILURE, Optional.empty(), "LearnerDirector found nothing");
		}
	}

	private boolean packNode(AOTree node, Collection<QuerySolution> badNegSols) {
		if(badNegSols.isEmpty()) {
			return true;
		}

		Set<Var> desiredScopedVars = node.getDesiredScopedVars();
		Set<Triple> triples = new HashSet<>(node.getMandTriples());

		// Prepare TripleTypeLooper:
		UTripleTypeLooper looper = new UTripleTypeLooper(desiredScopedVars);

		while(looper.hasNext() && !badNegSols.isEmpty()) {
			org.apache.commons.lang3.tuple.Triple<Var, Var, Var> tripleType = looper.next();

			// Make an appropriate TripleProducer:
			UTripleProducer tripleProducer = new UTripleProducer(
					tripleType.getLeft(), tripleType.getMiddle(), tripleType.getRight(),
					desiredScopedVars, pSols, badUris, queryToQueryExecution);

			while(tripleProducer.hasNext() && !badNegSols.isEmpty()) {
				Triple nextTriple = tripleProducer.next();

				if( !triples.contains(nextTriple) ) {
					Set<QuerySolution> killed =
							UtilsLearner.killedByTriples(nextTriple, triples, badNegSols, queryToQueryExecution);

					if(!killed.isEmpty()) {
						triples.add(nextTriple);
						badNegSols.removeAll(killed);
					}

				}
			}

			tripleProducer.close();
		}

		for(Triple t : triples) {
			node.addTriple(t);
		}

		boolean ready = true;
		if(!badNegSols.isEmpty()) {
			for(AOTree child : node.getChildren()) {
				ready = ready && packNode(child, badNegSols);
			}
		}

		if(node.getParent() == null) {
			log.info("LearnerDirector.packNode: ready = " + ready + "  badNegSols = " + badNegSols);
		}

		return ready;
	}

}
