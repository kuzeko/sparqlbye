package uk.ac.ox.cs.sparqlbye.core;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.sparql.ARQException;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.OpAsQuery;
import org.apache.jena.sparql.algebra.op.OpBGP;
import org.apache.jena.sparql.algebra.op.OpLeftJoin;
import org.apache.jena.sparql.core.BasicPattern;
import org.apache.jena.sparql.core.Var;
import org.apache.jena.sparql.engine.binding.Binding;
import org.apache.jena.sparql.engine.binding.BindingFactory;
import org.apache.jena.sparql.expr.E_Bound;
import org.apache.jena.sparql.expr.E_Equals;
import org.apache.jena.sparql.expr.E_LogicalAnd;
import org.apache.jena.sparql.expr.E_LogicalNot;
import org.apache.jena.sparql.expr.E_NotEquals;
import org.apache.jena.sparql.expr.Expr;
import org.apache.jena.sparql.expr.ExprFunction2;
import org.apache.jena.sparql.expr.ExprList;
import org.apache.jena.sparql.expr.ExprVar;
import org.apache.jena.sparql.expr.NodeValue;
import org.apache.jena.sparql.expr.nodevalue.NodeValueNode;
import org.apache.jena.sparql.syntax.Element;
import org.apache.jena.sparql.syntax.ElementData;
import org.apache.jena.sparql.syntax.ElementFilter;
import org.apache.jena.sparql.syntax.ElementGroup;

public abstract class UtilsJena {

	public static Set<Var> dom(QuerySolution sol) {
		Set<Var> ans = new HashSet<>();

		Iterator<String> it = sol.varNames();
		while(it.hasNext()) {
			String varName = it.next();
			ans.add(Var.alloc(varName));
		}

		return ans;
	}

	public static Set<Var> solutionsDomain(Collection<QuerySolution> solutions) {
		Set<Var> ans = new HashSet<>();
		for(QuerySolution solution : solutions) {
			Iterator<String> it = solution.varNames();
			while(it.hasNext()) {
				String varName = it.next();
				ans.add(Var.alloc(varName));
			}
		}
		return ans;
	}

	/**
	 * Get the set of variables mentioned in a triple pattern.
	 *
	 * @param triple
	 * @return the set of variables mentioned in this triple pattern
	 */
	public static Set<Var> dom(Triple triple) {
		Set<Var> ans = new HashSet<>();
		if(triple.getSubject().isVariable())   { ans.add((Var) triple.getSubject());   }
		if(triple.getPredicate().isVariable()) { ans.add((Var) triple.getPredicate()); }
		if(triple.getObject().isVariable())    { ans.add((Var) triple.getObject());    }
		return ans;
	}

	public static Set<Var> dom(Collection<Triple> triples) {
		Set<Var> ans = new HashSet<>();

		for(Triple triple : triples) {
			if(triple.getSubject().isVariable()) {
				ans.add((Var) triple.getSubject());
			}
			if(triple.getPredicate().isVariable()) {
				ans.add((Var) triple.getPredicate());
			}
			if(triple.getObject().isVariable()) {
				ans.add((Var) triple.getObject());
			}
		}

		return ans;
	}


	public static Set<Node> constantsInTriples(Collection<Triple> triples) {
		Set<Node> ans = new HashSet<>();
		for(Triple triple : triples) {
			if(!triple.getSubject().isVariable())   { ans.add(triple.getSubject()); }
			if(!triple.getPredicate().isVariable()) { ans.add(triple.getPredicate()); }
			if(!triple.getObject().isVariable())    { ans.add(triple.getObject()); }
		}
		return ans;
	}

	public static Set<String> urisInTriples(Collection<Triple> triples) {
		Set<String> ans = new HashSet<>();
		for(Triple triple : triples) {
			if(triple.getSubject().isURI())   { ans.add(triple.getSubject().getURI()); }
			if(triple.getPredicate().isURI()) { ans.add(triple.getPredicate().getURI()); }
			if(triple.getObject().isURI())    { ans.add(triple.getObject().getURI()); }
		}
		return ans;
	}

	public static Set<Node> constants(Triple triple) {
		Set<Node> ans = new HashSet<>();
		if(!triple.getSubject().isVariable())   { ans.add(triple.getSubject()); }
		if(!triple.getPredicate().isVariable()) { ans.add(triple.getPredicate()); }
		if(!triple.getObject().isVariable())    { ans.add(triple.getObject()); }
		return ans;
	}

	public static Set<Node> constantsInExprs(Collection<Expr> exprs) {
		Set<Node> ans = new HashSet<>();
		for(Expr expr : exprs) {
			if( !(expr instanceof E_Equals || expr instanceof E_NotEquals) ) {
				throw new IllegalArgumentException("illegal filter!");
			}
			ExprFunction2 exprFunc = (ExprFunction2) expr;
			Expr arg1 = exprFunc.getArg1();
			Expr arg2 = exprFunc.getArg2();

			if(arg1 instanceof NodeValue) {
				ans.add((Node) arg1);
			} else if(arg1 instanceof ExprVar) { } else { throw new IllegalArgumentException("illegal filter!"); }

			if(arg2 instanceof NodeValue) {
				ans.add((Node) arg2);
			} else if(arg2 instanceof ExprVar) { } else { throw new IllegalArgumentException("illegal filter!"); }
		}
		return ans;
	}




	public static Triple eval(Triple t, QuerySolution sol) {
		Node[] oldNodes = new Node[3];
		oldNodes[0] = t.getSubject();
		oldNodes[1] = t.getPredicate();
		oldNodes[2] = t.getObject();

		Node[] newNodes = new Node[3];

		for(int i = 0; i < 3; i++) {
			if(oldNodes[i].isVariable()) {
				RDFNode rdfNode = sol.get(((Var) oldNodes[i]).getName());

				if(rdfNode == null) {
					System.out.println("triple: " + t);
					System.out.println("sol: " + sol);
					throw new IllegalArgumentException();
				}

				newNodes[i] = rdfNode.asNode();
			} else {
				newNodes[i] = oldNodes[i];
			}
		}

		return Triple.create(newNodes[0], newNodes[1], newNodes[2]);
	}

	public static Triple eval(Triple t, Map<Var,Node> sol) {
		Node[] oldNodes = new Node[3];
		oldNodes[0] = t.getSubject();
		oldNodes[1] = t.getPredicate();
		oldNodes[2] = t.getObject();

		Node[] newNodes = new Node[3];

		for(int i = 0; i < 3; i++) {
			if(oldNodes[i].isVariable()) {
				Node node = sol.get((Var) oldNodes[i]);

				if(node == null) {
					throw new IllegalArgumentException();
				}

				newNodes[i] = node;
			} else {
				newNodes[i] = oldNodes[i];
			}
		}

		return Triple.create(newNodes[0], newNodes[1], newNodes[2]);
	}

	public static Triple looseEval(Triple t, QuerySolution sol) {
		return looseEval(t, querySolutionAsMap(sol));
	}

	public static Expr looseEval(Expr expr, QuerySolution sol) {
		return looseEval(expr, querySolutionAsMap(sol));
	}

	public static Expr looseEval(Expr expr, Map<Var, Node> sol) {
		Expr arg1 = null;
		Expr arg2 = null;

		boolean equals = false;

		if(expr instanceof E_Equals) {
			E_Equals exprEquals = (E_Equals) expr;
			arg1 = exprEquals.getArg1();
			arg2 = exprEquals.getArg2();
			equals = true;
		} else if(expr instanceof E_NotEquals) {
			E_NotEquals exprNotEquals = (E_NotEquals) expr;
			arg1 = exprNotEquals.getArg1();
			arg2 = exprNotEquals.getArg2();
			equals = false;
		} else {
			throw new IllegalArgumentException("wrong filter type!");
		}

		if(!(arg1 instanceof ExprVar && arg2 instanceof ExprVar)) {
			throw new IllegalArgumentException("wrong filter type!");
		}

		Var var1 = arg1.asVar();
		Var var2 = arg2.asVar();

		Node node1 = sol.get(var1);
		Node node2 = sol.get(var2);

//		Expr left = new ExprVar(node1 != null ? node1 : var1);
//		Expr right = new ExprVar(node2 != null ? node2 : var2);
		Expr left  = node1 != null ? new NodeValueNode(node1) : arg1;
		Expr right = node2 != null ? new NodeValueNode(node2) : arg2;

		if(equals) {
			return new E_Equals(left, right);
		} else {
			return new E_NotEquals(left, right);
		}
	}

	public static Triple looseEval(Triple t, Map<Var, Node> sol) {
		Node s = looseEval(t.getSubject(), sol);
		Node p = looseEval(t.getPredicate(), sol);
		Node o = looseEval(t.getObject(), sol);

		return Triple.create(s, p, o);
	}

	/**
	 * Replaces var by sol(var) if possible.
	 *
	 * @param var
	 * @param sol
	 */
	public static Node looseEval(Node var, Map<Var,Node> sol) {
		Node val = sol.get(var);
		if(val == null) {
			return var;
		} else {
			return val;
		}
	}

	public static boolean isInteresting(Query query, Model model) {
		try(QueryExecution qe = QueryExecutionFactory.create(query, model)) {
			ResultSet results = qe.execSelect();

			if(!results.hasNext()) {
				System.out.println("isInteresting: Empty result set.");
				return false;
			}

			QuerySolution sol = results.next();

			// Prepare first domain:
			Set<Var> domain = UtilsJena.dom(sol);

			while(results.hasNext()) {
				sol = results.next();
				Set<Var> newDom = UtilsJena.dom(sol);
				if(!domain.equals(newDom)) {
					return true;
				}
			}

		} catch(ARQException e) {
			return false;
		}

		return false;
	}

	public static Map<Var,Node> querySolutionAsMap(QuerySolution sol) {
		Map<Var,Node> ans = new HashMap<>();
		Iterator<String> it = sol.varNames();
		while(it.hasNext()) {
			String varName = it.next();
			ans.put(Var.alloc(varName), sol.get(varName).asNode());
		}

		return ans;
	}

	/**
	 * Converts a list of basic patterns to a linear Op with the same triples.
	 * @param bps
	 * @param pos
	 * @return
	 */
	public static Op toLinearOp(List<BasicPattern> bps, int pos) {
		if(pos == bps.size() - 1) {
			return new OpBGP(bps.get(pos));
		}

		return OpLeftJoin.create(new OpBGP(bps.get(pos)), toLinearOp(bps, pos + 1), ExprList.emptyList);
	}

	public static Query toQuery(Collection<Triple> triples) {
		BasicPattern pattern = new BasicPattern();
		for(Triple t : triples) {
			pattern.add(t);
		}
		return OpAsQuery.asQuery(new OpBGP(pattern));
	}

	/**
	 * Recursively convert an AOTree to an Op.
	 *
	 * @param tree
	 * @return
	 */
	public static Op convertAOFTreeToOp(AOTree tree) {
		BasicPattern bp = new BasicPattern();
		for(Triple triple : tree.getMandTriples()) {
			bp.add(triple);
		}
		Op op = new OpBGP(bp);

		for(AOTree child : tree.getChildren()) {
			op = OpLeftJoin.create(op, convertAOFTreeToOp(child), ExprList.emptyList);
		}

//		for(Expr expr : tree.getMandExprs()) {
//			op = OpFilter.filter(expr, op);
//		}

		return op;
	}

	/**
	 * Produces an ASK query that will return true if and only if {@code solution} is in the answer
	 * of {@code query}.
	 *
	 * @param query
	 * @param solution
	 * @return
	 */
	public static Query toMembershipQuery(Query query, QuerySolution solution) {
//		System.out.println("toMembershipQuery");

		Query clone = query.cloneQuery();

		// Prepare data bindings for present variables:
		ElementData eValues = new ElementData();
		Binding binding = BindingFactory.binding();

		Iterator<String> iter = solution.varNames();
		while(iter.hasNext()) {
			String  varName = iter.next();
			RDFNode rdfNode = solution.get(varName);
			binding = BindingFactory.binding(binding, Var.alloc(varName), rdfNode.asNode());

//			System.out.println("toMembershipQuery; binding = " + binding);

			eValues.add(Var.alloc(varName));
		}

		eValues.add(binding);
//		System.out.println("toMembershipQuery; eValues = " + eValues);

		// Prepare filters for absent variables:
		Expr exprAnd = null;
		List<Var> vars = clone.getProjectVars();

		for(Var var : UtilsSets.diff(vars, UtilsJena.dom(solution))) {
			Expr exprVarNotBound = new E_LogicalNot( new E_Bound(new ExprVar(var)) );

			if(exprAnd == null) {
				exprAnd = exprVarNotBound;
			} else {
				exprAnd = new E_LogicalAnd(exprAnd, exprVarNotBound);
			}
		}

		ElementFilter eFilter = null;
		if(exprAnd != null) {
			eFilter = new ElementFilter(exprAnd);
		}

//		System.out.println("toMembershipQuery; eFilter = " + eFilter);

		Element queryPattern = clone.getQueryPattern();
		if(queryPattern instanceof ElementGroup) {
			ElementGroup eg = (ElementGroup) queryPattern;
			eg.addElement(eValues);
			if(eFilter != null) {
				eg.addElement(eFilter);
			}
		} else {
			throw new IllegalArgumentException("unkown getQueryPattern() type: " + queryPattern.getClass());
		}

		clone.setQueryAskType();
		return clone;
	}

	/**
	 * Evaluates the query and returns answer mappings binned by domain.
	 *
	 * @param query
	 * @param model
	 * @return
	 */
//	public static Map<Set<Var>,Set<QuerySolution>> evalQuery(Query query, Model model) {
//		Map<Set<Var>,Set<QuerySolution>> domsToSols = new HashMap<>();
//
//		try (QueryExecution qexec = QueryExecutionFactory.create(query, model)) {
//			ResultSet results = qexec.execSelect();
//
//			while(results.hasNext()) {
//				QuerySolution sol = results.next();
//				Set<Var> domain = JenaUtils.dom(sol);
//
//				// Add to map:
//				Set<QuerySolution> value = domsToSols.get(domain);
//				if(value == null) {
//					value = new HashSet<>();
//					domsToSols.put(domain, value);
//				}
//				value.add(sol);
//			}
//		}
//		return domsToSols;
//	}


}
