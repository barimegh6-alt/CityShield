package com.cityshield.algorithm.quickselect;

/**
 * Outcome of a selection query.
 *
 * @param value           the selected value
 * @param k               1-based rank that was selected (k-th smallest)
 * @param n               number of values
 * @param comparisons     element comparisons made while partitioning
 * @param pivotSelections random pivots chosen
 * @param partitionRounds partitioning passes executed
 */
public record SelectionResult(double value, int k, int n, long comparisons, long pivotSelections, long partitionRounds) {
}
