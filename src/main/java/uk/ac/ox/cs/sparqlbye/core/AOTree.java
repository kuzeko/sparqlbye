package uk.ac.ox.cs.sparqlbye.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.jena.graph.Triple;
import org.apache.jena.sparql.core.Var;

/**
 * A mutable representation of an SPARQL AO-query.
 *
 * @author gdiazc
 *
 */
public final class AOTree {
	private AOTree             parent;
	private final List<AOTree> children;
	private final List<Triple> triples;
	private final Set<Var>     desiredTopVars;

	private AOTree() {
		parent   = null;
		children = new ArrayList<>();
		triples  = new ArrayList<>();
		desiredTopVars = new HashSet<>();
	}


	/**
	 * Adds {@code child} to the list of children of this node. Note that if {@code child} has
	 * children of its own, they will become grandchildren of {@code this}.
	 *
	 * This method modifies {@code child.parent}.
	 *
	 * @param child an {@code AOTree} to add as a child of this node.
	 */
	public void addChild(AOTree child) {
		children.add(child);
		child.parent = this;
	}

	public void addTriple(Triple t)       { triples.add(t); }
	public void addDesiredTopVar(Var var) { desiredTopVars.add(var); }

	public Set<Var>     getDesiredTopVars() { return Collections.unmodifiableSet(desiredTopVars); }
	public AOTree       getParent()         { return parent; }
	public List<AOTree> getChildren()       { return Collections.unmodifiableList(children); }

	/**
	 * Returns all triples mentioned in this subtree. Note that this tree might not be a root note, in which
	 * case this method will only return the triples mentioned in this subtree.
	 *
	 * @author gdiazc
	 * @return all triples included in this (sub-)tree.
	 */
	public Set<Triple> getTriplesInSubtree() {
		Set<Triple> ans = new HashSet<>();
		_accTriples(ans);
		return ans;
	}
	private void _accTriples(Set<Triple> ans) {
		ans.addAll(triples);
		for(AOTree child : children) {
			child._accTriples(ans);
		}
	}

	public Set<Triple>   getMandTriples()    { return Collections.unmodifiableSet(new HashSet<>(triples)); }
//	public List<Triple>  getMandTriplesList() { return Collections.unmodifiableList(triples); }
    private Set<Triple>  getScopedTriples()  { return UtilsSets.union(this.getMandTriples(), this.getHigherTriples()); }
	private Set<Triple>  getHigherTriples()  {
		if(this.getParent() == null) {
			return Collections.emptySet();
		} else {
			return this.getParent().getScopedTriples();
		}
	}

	public Set<Var> getVarsInSubtree() { return UtilsJena.dom(this.getTriplesInSubtree()); }
	public Set<Var> getMandVars()      { return UtilsJena.dom(triples);                    }
	public Set<Var> getHigherVars()    { return UtilsJena.dom(this.getHigherTriples());    }
//	public Set<Var> getScopedVars()    { return UtilsJena.dom(this.getScopedTriples());    }
	public Set<Var> getTopVars()       { return UtilsSets.diff( this.getMandVars(), this.getHigherVars() ); }

	public Set<Var> getDesiredScopedVars() { return UtilsSets.union(this.desiredTopVars, this.getDesiredHigherVars()); }
	public Set<Var> getDesiredHigherVars() {
		if(parent == null) {
			return Collections.emptySet();
		} else {
			return this.parent.getDesiredScopedVars();
		}
	}

//	private int getOptDepth() {
//		if(children.isEmpty()) {
//			return 0;
//		} else {
//			return children.stream()
//					.map(AOTree::getOptDepth)
//					.max((a, b) -> Integer.compare(a, b))
//					.get() + 1;
//		}
//	}

	@Override
	public String toString() { return this._toString(0); }

	private String _toString(int indent) {
		StringBuilder aux = new StringBuilder();
		for(int i = 0; i < indent; i++) {
			aux.append("  ");
		}
		String padding = aux.toString();
		StringBuilder sb = new StringBuilder();

		sb.append("Tree: ").append(triples);
		sb.append(" topvars: ").append(getTopVars());
		if(!desiredTopVars.isEmpty()) { sb.append(" desiredTopVars: ").append(desiredTopVars); }
		sb.append("\n");

		for(AOTree c : children) {
			sb.append(padding).append("-> ");
			sb.append(c._toString(indent + 1));
		}

		return sb.toString();
	}

	public static AOTree from(Collection<Triple> triples) {
		AOTree tree = new AOTree();
		if(triples != null) {
			for(Triple triple : triples) {
				tree.addTriple(triple);
			}
		}
		return tree;
	}

}
