package uk.ac.ox.cs.sparqlbye.core;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.sparql.core.Var;

public abstract class UtilsAOTrees {

	public static boolean isWellFormed(AOTree tree) {
		return isWellFormedHere(tree)
				&& tree.getChildren().stream().allMatch(node -> isWellFormed(node));
	}

	private static boolean isWellFormedHere(AOTree node) {
		Set<Var> desiredTopVars = node.getDesiredTopVars();
		Set<Var> actualTopVars = node.getTopVars();
		Set<Var> higherVars = node.getHigherVars();
		return !desiredTopVars.isEmpty()
				&& UtilsSets.intersection(desiredTopVars, higherVars).isEmpty()
				&& actualTopVars.equals(desiredTopVars);
	}

	public static boolean isWellDesigned(AOTree tree) {
		if(tree.getParent() != null) {
			throw new IllegalArgumentException("can only call isWellDesigned on a root node.");
		}
		return isWellDesigned(tree, tree, new HashSet<>(), new HashSet<>());
	}

	private static boolean isWellDesigned(AOTree root, AOTree node, Set<Var> outerVars, Set<Var> badVars) {
		if(UtilsSets.doIntersect(node.getVarsInSubtree(), badVars)) { return false; }

		Set<Var> mandVars = node.getMandVars();
		List<AOTree> children = node.getChildren();

		for(AOTree child : children) {
			Set<Var> newOuterVars = new HashSet<>(outerVars);
			newOuterVars.addAll(mandVars);

			for(AOTree ch : children) {
				if(ch != child) {
					newOuterVars.addAll(ch.getVarsInSubtree());
				}
			}

			Set<Var> newBadVars = UtilsSets.diff(newOuterVars, mandVars);
			if(!isWellDesigned(root, child, newOuterVars, newBadVars)) {
				return false;
			}
		}

		return true;
	}

	/**
	 *
	 * @param tree
	 * @return the set of constants mentioned in <code>tree</code>.
	 */
	public static Set<Node> constants(AOTree tree) {
		return UtilsJena.constantsInTriples(tree.getTriplesInSubtree());
	}

	public static AOTree copy(AOTree tree) {
		AOTree copy = AOTree.from(Collections.emptyList());

		for(Triple triple : tree.getMandTriples()) {
			copy.addTriple(triple);
		}

		for(Var var : tree.getDesiredTopVars()) {
			copy.addDesiredTopVar(var);
		}

		for(AOTree child : tree.getChildren()) {
			tree.addChild(copy(child));
		}

		return copy;
	}

}
