package uk.ac.ox.cs.sparqlbye.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.OpAsQuery;
import org.apache.jena.sparql.algebra.op.OpBGP;
import org.apache.jena.sparql.algebra.op.OpFilter;
import org.apache.jena.sparql.algebra.op.OpProject;
import org.apache.jena.sparql.core.BasicPattern;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.expr.E_GreaterThanOrEqual;
import org.apache.jena.sparql.expr.E_LessThanOrEqual;
import org.apache.jena.sparql.expr.Expr;
import org.apache.jena.sparql.expr.ExprVar;
import org.apache.jena.sparql.expr.nodevalue.NodeValueFloat;
import org.apache.jena.sparql.syntax.ElementTriplesBlock;

public abstract class UtilsLearner {

	public static AOTree buildTemplateTree(Collection<QuerySolution> positiveSolutions) {
		if(positiveSolutions == null || positiveSolutions.size() == 0) {
			throw new IllegalArgumentException();
		}

		Set<Set<QuerySolution>>           coverages         = new HashSet<>();
		Set<Var>                          fullDomain        = UtilsJena.solutionsDomain(positiveSolutions);
		Map<Set<QuerySolution>, Set<Var>> mapCoverageToVars = new HashMap<>();

		Set<Set<Var>> domains = new HashSet<>();
		for(QuerySolution solution : positiveSolutions) {
			Set<Var> domain = UtilsJena.dom(solution);
			domains.add(domain);
		}

		for(Var var : fullDomain) {
			Set<QuerySolution> coverage = UtilsQuerySolutions.coverage(var, positiveSolutions);
			coverages.add(coverage);
			Set<Var> vars = mapCoverageToVars.get(coverage);
			if(vars == null) {
				vars = new HashSet<>();
				mapCoverageToVars.put(coverage, vars);
			}
			vars.add(var);
		}

		AOTree templateTree = AOTree.from(null);
		Set<QuerySolution> mandatoryCoverage = UtilsSets.getMaximumSet(coverages).get();
		_buildTemplateTree(templateTree, coverages, mandatoryCoverage, mapCoverageToVars);

		return templateTree;
	}


	private static void _buildTemplateTree(AOTree node, Set<Set<QuerySolution>> coverages,
			Set<QuerySolution> thisCoverage, Map<Set<QuerySolution>, Set<Var>> mapCoverageToVars) {
		Set<Set<QuerySolution>> scopedCoverages = coverages.stream()
				.filter(coverage -> thisCoverage.containsAll(coverage) && !thisCoverage.equals(coverage))
				.collect(Collectors.toSet());

		Set<Set<QuerySolution>> maximalCoverages = scopedCoverages.stream()
				.filter(coverage -> UtilsSets.isMaximal(coverage, scopedCoverages) )
				.collect(Collectors.toSet());

		// Prepare this node:
		Set<Var> newVars = mapCoverageToVars.get(thisCoverage);
		for(Var var : newVars) {
			node.addDesiredTopVar(var);
		}

		for(Set<QuerySolution> coverage : maximalCoverages) {
			AOTree child = AOTree.from(null);
			node.addChild(child);
			_buildTemplateTree(child, coverages, coverage, mapCoverageToVars);
		}
	}

	/**
	 * Check an AOFTree for compatibility for positive and negative solutions.
	 * If intermediate results are needed, use {@code ULearnedQueryChecker} instead.
	 *
	 * @param learnedTree
	 * @param pSols
	 * @param nSols
	 * @param getQueryExecution
	 * @return
	 */
	public static boolean checkLearnedQuery(
			AOTree                          learnedTree,
			Collection<QuerySolution>       pSols,
			Collection<QuerySolution>       nSols,
			Function<Query, QueryExecution> getQueryExecution) {
		System.out.println("checkLearnedQuery()");

		if(!UtilsAOTrees.isWellFormed(learnedTree)) {
			System.out.println("  not well formed!");
			return false;
		}

		System.out.println("  checkLearnedQuery: learnedQuery = " + learnedTree);

		return pSols.stream().allMatch(pSol -> execMembershipQuery(learnedTree, pSol, getQueryExecution))
				&&
				nSols.stream().noneMatch(nSol -> execMembershipQuery(learnedTree, nSol, getQueryExecution));
	}

	static boolean execMembershipQuery(
			AOTree                          learnedTree,
			QuerySolution                   solution,
			Function<Query, QueryExecution> getQueryExecution) {
		System.out.println("execMembershipQuery()");

		Op    learnedOp       = UtilsJena.convertAOFTreeToOp(learnedTree);
		Query learnedQuery    = OpAsQuery.asQuery(learnedOp);
		Query membershipQuery = UtilsJena.toMembershipQuery(learnedQuery, solution);

		try(QueryExecution queryExecution = getQueryExecution.apply(membershipQuery)) {
			return queryExecution.execAsk();
		}
	}


	static Set<QuerySolution> killedByTriples(
			Triple                         triple,
			Collection<Triple>             triples,
			Collection<QuerySolution>      killables,
			Function<Query,QueryExecution> queryToQueryExecution) {

		if(killables.isEmpty()) { return Collections.emptySet(); }

		Set<Triple> candTriples = new HashSet<>(triples);
		candTriples.add(triple);

		Set<QuerySolution> killed = killables.stream()
				.filter(sol -> triplesKillSol(candTriples, sol, queryToQueryExecution))
				.collect(Collectors.toSet());

		return killed;
	}

	static boolean triplesKillSol(
			Collection<Triple>             triples,
			QuerySolution                  solution,
			Function<Query,QueryExecution> queryToQueryExecution) {

		Map<Var,Node> solutionMap = UtilsJena.querySolutionAsMap(solution);

		Set<Triple> triples2 = triples.stream()
				.map(triple -> UtilsJena.looseEval(triple, solutionMap))
				.collect(Collectors.toSet());

		Set<Var> vars = UtilsJena.dom(triples2);

		if(vars.isEmpty()) {
			ElementTriplesBlock block = new ElementTriplesBlock();
			for(Triple triple2 : triples2) {
				block.addTriple(triple2);
			}

			Query q = QueryFactory.make();
			q.setQueryPattern(block);
			q.setQueryAskType();

			boolean ans = false;
			try(QueryExecution qe = queryToQueryExecution.apply(q)) {
				ans = qe.execAsk();
			}
			return !ans;
		} else {
			return !hasSolution(triples2, queryToQueryExecution);
		}
	}

	static boolean hasSolution(
			Collection<Triple>             triples,
			Function<Query,QueryExecution> queryToQueryExecution) {

		BasicPattern pattern = new BasicPattern();
		for(Triple triple : triples) {
			pattern.add(triple);
		}
		Query q = OpAsQuery.asQuery(new OpBGP(pattern));

		try(QueryExecution qe = queryToQueryExecution.apply(q)) {
			ResultSet results = qe.execSelect();
			return results.hasNext();
		}
	}


	public static final class URevengResponse {
		private final RevengStatus status;
		private final Optional<AOTree> optLearnedTree;
		private final String message;

		public static enum RevengStatus {
			SUCCESS, FAILURE
		}

		public URevengResponse(RevengStatus status, Optional<AOTree> optLearnedTree, String message) {
			this.status = status;
			this.optLearnedTree = optLearnedTree;
			this.message = message;
		}

		public RevengStatus getStatus() { return status; }
		public Optional<AOTree> getOptLearnedTree() { return optLearnedTree; }
		public String getMessage() { return message; }
	}

	static final class UTripleProducer implements Iterator<Triple> {
		private static final int MAX_TRIPLES_IN_SPARQL_QUERY = 70;
		//		private Set<QuerySolution> solutions;
		private Triple         initialTriple;
		private QueryExecution qExecution;
		private String         qType;
		private boolean        resultAns;
		private boolean        qAskFinished;
		private ResultSet      resultSet;
		private List<String>   badUris;

//		private final Function<Query,QueryExecution> queryToQueryExecution;

		public UTripleProducer(
				Var                            s,
				Var                            p,
				Var                            o,
				Set<Var>                       sig,
				Set<QuerySolution>             solutions,
				List<String>                   badUris,
				Function<Query,QueryExecution> queryToQueryExecution) {
			this.badUris = new ArrayList<>(badUris);

			// Prepare interesting mappings:
			List<QuerySolution> interestingSolutions =
					solutions.stream()
					.filter(sol -> UtilsJena.dom(sol).containsAll(sig))
					.collect(Collectors.toList());

			Var s2 = s; Var p2 = p; Var o2 = o;
			if(s2 == null) { s2 = Var.alloc("tempS"); }
			if(p2 == null) { p2 = Var.alloc("tempP"); }
			if(o2 == null) { o2 = Var.alloc("tempO"); }
			initialTriple = Triple.create(s2, p2, o2);

			BasicPattern bp = new BasicPattern();
			List<Expr> filters = new ArrayList<>();
			boolean isUseless = false;

			outer:
				for(int i = 0; i < interestingSolutions.size(); i++) {
					QuerySolution solution = interestingSolutions.get(i);

					Node[] nodes = new Node[3];
					nodes[0] = s;
					nodes[1] = p;
					nodes[2] = o;
					Node[] newNodes = new Node[3];

					for(int j = 0; j < 2; j++) {
						Node node = nodes[j];
						if(node != null) {
							RDFNode rdfNode = solution.get(node.getName());

							if(rdfNode.isLiteral()) {
								isUseless = true;
								break outer;
							} else {
								newNodes[j] = solution.get(node.getName()).asNode();
							}
						} else {
							String suffix = null;
							switch (j) {
							case 0: suffix = "S"; break;
							case 1: suffix = "P"; break;
							default: break;
							}
							newNodes[j] = Var.alloc("temp" + suffix);
						}
					}

					if(o != null) {
						RDFNode rdfNode = solution.get(o.getName());

						if(rdfNode.isLiteral()) {
							Literal lit = rdfNode.asLiteral();
							String datatype = lit.getDatatypeURI();
							if( datatype != null && datatype.equals("http://www.w3.org/2001/XMLSchema#float") ) {
								String varName = o.getName() + "_aux_" + i;
								newNodes[2] = Var.alloc(varName);
								Expr e1 = new E_LessThanOrEqual(
										new ExprVar(varName),
										new NodeValueFloat(solution.get(o.getName()).asLiteral().getFloat() + 0.0001f)
										);
								filters.add(e1);

								Expr e2 = new E_GreaterThanOrEqual(
										new ExprVar(varName),
										new NodeValueFloat(solution.get(o.getName()).asLiteral().getFloat() - 0.0001f)
										);
								filters.add(e2);
							} else {
								newNodes[2] = solution.get(o.getName()).asNode();
							}
						} else {
							newNodes[2] = solution.get(o.getName()).asNode();
						}
					} else {
						newNodes[2] = Var.alloc("tempO");
					}

					Triple t3 = null;
					if(!isUseless) {
						t3 = Triple.create(newNodes[0], newNodes[1], newNodes[2]);
						if(bp.size() <= MAX_TRIPLES_IN_SPARQL_QUERY) {
							bp.add(t3);
						} else {
							System.out.println("TripleProducer> Truncating query.");
							break;
						}

					}
				}

			if(isUseless) {
				qType = "ask";
				resultAns = false;
				return;
			}

			Op opBGP = new OpBGP(bp);

			for(Expr expr : filters) {
				opBGP = OpFilter.filter(expr, opBGP);
			}

			List<Var> projectedVars = new ArrayList<>();
			if(s == null) { projectedVars.add(Var.alloc("tempS")); }
			if(p == null) { projectedVars.add(Var.alloc("tempP")); }
			if(o == null) { projectedVars.add(Var.alloc("tempO")); }
			opBGP = new OpProject(opBGP, projectedVars);

			Query query = OpAsQuery.asQuery(opBGP);
			if(projectedVars.isEmpty()) {
				query.setQueryAskType();
				qType = "ask";
			} else {
				for(Var var : projectedVars) {
					query.addResultVar(var);
				}
				query.setQuerySelectType();
				qType = "select";
			}

			qExecution = queryToQueryExecution.apply(query);

			if(!isUseless) {
				if(qType.equals("ask")) {
					try {
						resultAns = qExecution.execAsk();
					} catch(Exception e) {
						resultAns = false;
					}
				} else {
					try {
						resultSet = qExecution.execSelect();
					} catch(Exception e) {
						e.printStackTrace();
						resultAns = false;
					}
				}
			} else {
				qType = "ask";
				resultAns = false;
			}
		}

		@Override
		public boolean hasNext() {
			if(qType.equals("ask")) {
				return !qAskFinished && resultAns;
			} else {
				return resultSet != null && resultSet.hasNext();
			}
		}

		@Override
		public Triple next() {
			if(qType.equals("ask") && !qAskFinished && resultAns) {
				qAskFinished = true;
				return initialTriple;
			} else {
				QuerySolution solution = null;
				Triple ans = null;

				do {
					solution = resultSet.next();
					ans      = UtilsJena.looseEval(initialTriple, solution);
				} while(UtilsJena.constants(ans).stream()
						.filter(node -> node.isURI())
						.map(node -> node.getURI())
						.anyMatch(uri -> badUris.contains(uri)));

				return ans;
			}
		}

		public void close() {
			if(qExecution != null) {
				qExecution.close();
			}
		}
	}

	static final class UTripleTypeLooper implements Iterator<org.apache.commons.lang3.tuple.Triple<Var, Var, Var>> {
		private static final String[] families = { "vcv", "vcc", "vvv", "vvc", "cvv", "cvc", "ccv" };

		private final List<Var> vars;

		private int type;
		private int[] current;

		public UTripleTypeLooper(Collection<Var> vars) {
			this.vars = new ArrayList<>(vars);
			type = 0;
			current = new int[3];

			_init();
		}

		@Override
		public boolean hasNext() {
			return type < families.length;
		}

		@Override
		public org.apache.commons.lang3.tuple.Triple<Var, Var, Var> next() {
			Var l = current[0] == -1 ? null : vars.get(current[0]);
			Var m = current[1] == -1 ? null : vars.get(current[1]);
			Var r = current[2] == -1 ? null : vars.get(current[2]);

			org.apache.commons.lang3.tuple.Triple<Var, Var, Var> tripleType =
					new ImmutableTriple<Var, Var, Var>(l, m, r);

			_inc();

			return tripleType;
		}

		private void _inc() {
			String tripleType = families[type];

			boolean typeFinished = true;

			for(int i = 0; i < 3; i++) {
				if(tripleType.charAt(i) == 'v') {
					current[i] = (current[i] + 1) % vars.size();
					if(current[i] > 0) {
						typeFinished = false;
						break;
					}
				}
			}

			if(typeFinished) {
				type++;
				if(hasNext()) {
					_init();
				}
			}
		}

		private void _init() {
			String newTripleType = families[type];
			for(int i = 0; i < 3; i++) {
				if(newTripleType.charAt(i) == 'v') {
					current[i] = 0;
				} else {
					current[i] = -1;
				}
			}
		}
	}

}
