package com.cityshield.algorithm.segmenttree;

/**
 * Baseline O(n) range-sum used to compare against {@link SegmentTree}.
 */
public final class LinearScan {

    private LinearScan() {
    }

    /**
     * Result of a linear scan.
     *
     * @param sum             the range sum
     * @param elementsScanned number of array elements read
     */
    public record Result(long sum, int elementsScanned) {
    }

    /**
     * Sums values[from..to] inclusive by scanning. Time O(to-from+1), space O(1).
     *
     * @param values array, not null
     * @param from   first index
     * @param to     last index
     * @return sum and number of elements scanned
     * @throws IllegalArgumentException on null/empty array, from &gt; to or out-of-range indices
     */
    public static Result sum(long[] values, int from, int to) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("values must not be null or empty");
        }
        if (from > to) {
            throw new IllegalArgumentException("invalid range: from (" + from + ") > to (" + to + ")");
        }
        if (from < 0 || to >= values.length) {
            throw new IllegalArgumentException("range [" + from + ", " + to + "] out of bounds [0, " + (values.length - 1) + "]");
        }
        long s = 0;
        int scanned = 0;
        for (int i = from; i <= to; i++) {
            s += values[i];
            scanned++;
        }
        return new Result(s, scanned);
    }
}
