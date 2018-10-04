package uk.ac.ox.cs.sparqlbye.core;

import java.util.*;

public abstract class UtilsSets {

	/**
	 * Calculates the union of two sets.
	 *
	 * @param a first set.
	 * @param b second set.
	 * @return the union of <code>a</code> and <code>b</code>.
	 */
	static <T> Set<T> union(Set<T> a, Set<T> b) {
		Set<T> ans = new HashSet<>(a);
		ans.addAll(b);
		return ans;
	}

	static <T> Set<T> intersection(Set<T> a, Set<T> b) {
		Set<T> ans = new HashSet<>(a);
		ans.retainAll(b);
		return ans;
	}

	static <T> Set<T> diff(Collection<T> a, Collection<T> b) {
		Set<T> ans = new HashSet<>(a);
		ans.removeAll(b);
		return ans;
	}

	public static <T> T getRandomElement(Collection<T> a) {
	    if(a.isEmpty()) {
	        return null;
        } else {
		    return a.stream().skip(new Random().nextInt(a.size())).findFirst().orElse(null);
        }
	}


//	public static <T> boolean isSubset(Set<T> a, Set<T> b) {
//		return b.containsAll(a);
//	}

//	public static <T> boolean doIntersect(Set<T> a, Set<T> b) {
//		for(T e : a) {
//			if(b.contains(e)) {
//				return true;
//			}
//		}
//		return false;
//	}

//	public static <T> boolean isLinear(Set<Set<T>> sets) {
//		for(Set<T> a : sets) {
//			for(Set<T> b : sets) {
//				if(!b.containsAll(a) && !a.containsAll(b)) {
//					return false;
//				}
//			}
//		}
//		return true;
//	}

//	public static boolean subset(BitSet a, BitSet b) {
//		for (int i = a.nextSetBit(0); i >= 0; i = a.nextSetBit(i+1)) {
//			if(!b.get(i)) { return false; }
//		}
//		return true;
//	}

//	public static <T> Comparator<Set<T>> subsetOrder() {
//		return new Comparator<Set<T>>() {
//			@Override
//			public int compare(Set<T> o1, Set<T> o2) {
//				if(o1.equals(o2)) {
//					return 0;
//				} else if(o2.containsAll(o1)) {
//					return -1;
//				} else if(o1.containsAll(o2)) {
//					return 1;
//				} else {
//					throw new ClassCastException();
//				}
//			}
//		};
//	}


	/**
	 * Given a collection {@code sets} of sets, return the maximum set, if there is any.
	 * The maximum set is a set {@code a} in {@code sets} such that for every set {@code b}
	 * in {@code sets}, {@code b} is a subset of {@code a}.
	 *
	 * @param sets collection of sets.
	 * @return a maximum set from {@code sets}.
	 */
	public static <T> Optional<Set<T>> getMaximumSet(Collection<Set<T>> sets) {
		return sets.stream()
				.filter(set -> isSupersetOfAll(set, sets))
				.findAny();
	}

	private static <T> boolean isSupersetOfAll(Set<T> set, Collection<Set<T>> sets) {
		return sets.stream().allMatch(set::containsAll);
	}

	public static <T> boolean isMaximal(Set<T> set, Collection<Set<T>> sets) {
		return sets.stream().allMatch(otherSet -> !otherSet.containsAll(set) || otherSet.equals(set));
	}

	/**
	 *
	 * @author gdiazc
	 * @param sets collection of sets.
	 * @return the depth of a lattice.
	 */
	public static <T> int latticeDepth(Collection<Set<T>> sets) {
		Optional<Set<T>> optMaximumSet = UtilsSets.getMaximumSet(sets);
		if(optMaximumSet.isPresent()) {
			return height(optMaximumSet.get(), sets);
		} else {
			throw new IllegalArgumentException();
		}
	}

	private static <T> int height(Set<T> set, Collection<Set<T>> sets) {
		Optional<Integer> ans = sets.stream()
				.filter(other -> set.containsAll(other) && !set.equals(other))
				.map(other -> height(other, sets))
				.max((a, b) -> Integer.compare(a, b));
		return ans.map(integer -> 1 + integer).orElse(0);
	}

}
