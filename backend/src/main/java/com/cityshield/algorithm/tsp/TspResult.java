package com.cityshield.algorithm.tsp;

import java.util.List;

/**
 * A closed tour.
 *
 * @param tour     node indices starting and ending at the depot (index 0); size n+1 for n nodes (n=1 gives [0,0])
 * @param length   total tour length
 * @param mstWeight weight of the minimum spanning tree (lower bound on the optimum) or NaN if not computed
 * @param mstEdges  MST edges as {from, to} index pairs (empty if not computed)
 * @param preorder  MST preorder (before shortcutting), empty if not computed
 */
public record TspResult(List<Integer> tour, double length, double mstWeight,
                        List<int[]> mstEdges, List<Integer> preorder) {
}
