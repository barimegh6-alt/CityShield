package com.cityshield.algorithm.setcover;

import java.util.List;

/**
 * One iteration of a covering algorithm.
 *
 * @param step         1-based step number
 * @param setId        id of the chosen set
 * @param newlyCovered elements covered for the first time by this set (ascending)
 * @param cost         cost of the set (0 when it was forced/pre-selected)
 * @param ratio        newly covered elements per unit cost (infinite cost-free sets report newly covered)
 * @param forced       true if the set was pre-selected (an existing centre) rather than chosen greedily
 */
public record CoverStep(int step, long setId, List<Integer> newlyCovered, double cost, double ratio, boolean forced) {
}
