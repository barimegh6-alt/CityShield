package com.cityshield.algorithm.quickselect;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Randomized Quickselect with three-way (Dutch national flag) partitioning.
 *
 * <p><b>Why randomize?</b> A fixed pivot rule (first/last element) degrades to O(n^2) on sorted or
 * adversarial input. A uniformly random pivot makes the expected running time O(n) for every input,
 * because each round discards a constant expected fraction of the elements. Worst case remains
 * O(n^2) but occurs with vanishing probability. <b>Why three-way?</b> Severity and urgency have only five
 * distinct values, so many duplicates exist; grouping equal values means they are never re-partitioned.</p>
 *
 * <p>The generator is seeded: the same seed on the same input reproduces the same pivots, statistics
 * and result. The input array is never modified. Time expected O(n), space O(n) for the working copy.</p>
 */
public final class RandomizedQuickselect {
    private final long seed;

    public RandomizedQuickselect(long seed) {
        this.seed = seed;
    }

    public long getSeed() {
        return seed;
    }

    /** k-th smallest value, 1 &lt;= k &lt;= n. */
    public SelectionResult kthSmallest(double[] values, int k) {
        requireValues(values);
        int n = values.length;
        if (k < 1 || k > n) {
            throw new IllegalArgumentException("k must be in [1," + n + "] but was " + k);
        }
        double[] a = values.clone();
        Random rnd = new Random(seed);
        long comparisons = 0;
        long pivots = 0;
        long rounds = 0;
        int lo = 0;
        int hi = n - 1;
        int target = k - 1;
        while (true) {
            if (lo == hi) {
                return new SelectionResult(a[lo], k, n, comparisons, pivots, rounds);
            }
            double pivot = a[lo + rnd.nextInt(hi - lo + 1)];
            pivots++;
            rounds++;
            int lt = lo;
            int i = lo;
            int gt = hi;
            while (i <= gt) {
                comparisons++;
                if (a[i] < pivot) {
                    double t = a[lt];
                    a[lt] = a[i];
                    a[i] = t;
                    lt++;
                    i++;
                } else {
                    comparisons++;
                    if (a[i] > pivot) {
                        double t = a[gt];
                        a[gt] = a[i];
                        a[i] = t;
                        gt--;
                    } else {
                        i++;
                    }
                }
            }
            // a[lo..lt-1] < pivot, a[lt..gt] == pivot, a[gt+1..hi] > pivot
            if (target < lt) {
                hi = lt - 1;
            } else if (target > gt) {
                lo = gt + 1;
            } else {
                return new SelectionResult(pivot, k, n, comparisons, pivots, rounds);
            }
        }
    }

    /** k-th largest value (1 = maximum). */
    public SelectionResult kthLargest(double[] values, int k) {
        requireValues(values);
        if (k < 1 || k > values.length) {
            throw new IllegalArgumentException("k must be in [1," + values.length + "] but was " + k);
        }
        SelectionResult r = kthSmallest(values, values.length - k + 1);
        return new SelectionResult(r.value(), k, r.n(), r.comparisons(), r.pivotSelections(), r.partitionRounds());
    }

    /** Rank k (1-based, nearest-rank method) for percentile p in (0,100]. */
    public static int nearestRank(int n, double percentile) {
        if (Double.isNaN(percentile) || percentile <= 0 || percentile > 100) {
            throw new IllegalArgumentException("percentile must be in (0,100] but was " + percentile);
        }
        if (n < 1) {
            throw new IllegalArgumentException("values must not be empty");
        }
        return Math.max(1, Math.min(n, (int) Math.ceil(percentile / 100.0 * n - 1e-12)));
    }

    /** Percentile threshold using the nearest-rank method (the rank-th smallest value). */
    public SelectionResult percentile(double[] values, double percentile) {
        requireValues(values);
        return kthSmallest(values, nearestRank(values.length, percentile));
    }

    /** Indices of values greater than or equal to the given percentile threshold. */
    public List<Integer> highRiskIndices(double[] values, double percentile) {
        double threshold = percentile(values, percentile).value();
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            if (values[i] >= threshold) {
                out.add(i);
            }
        }
        return out;
    }

    private static void requireValues(double[] values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("values must not be empty");
        }
        for (double v : values) {
            if (Double.isNaN(v)) {
                throw new IllegalArgumentException("values must not contain NaN");
            }
        }
    }
}
