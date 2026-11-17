package com.cityshield.algorithm.heap;

/**
 * Immutable snapshot of the operation counters of an {@link IncidentMaxHeap}.
 *
 * @param insertions  number of {@code insert} calls that succeeded
 * @param extractions number of {@code extractMax} calls that succeeded
 * @param comparisons number of element-to-element priority comparisons
 * @param swaps       number of element swaps performed while sifting
 */
public record HeapStats(long insertions, long extractions, long comparisons, long swaps) {
}
