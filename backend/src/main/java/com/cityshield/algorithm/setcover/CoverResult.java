package com.cityshield.algorithm.setcover;

import java.util.List;

/**
 * Result of a covering algorithm.
 *
 * @param steps          ordered selections (forced sets first, then greedy picks)
 * @param selectedIds    ids of all selected sets in selection order
 * @param coveredCount   number of covered elements
 * @param uncovered      elements left uncovered (ascending)
 * @param totalCost      cost of the NEWLY chosen (non-forced) sets only
 * @param subsetsChecked subsets examined (exact solver) or 0 for greedy methods
 */
public record CoverResult(List<CoverStep> steps, List<Long> selectedIds, int coveredCount,
                          List<Integer> uncovered, double totalCost, long subsetsChecked) {
}
