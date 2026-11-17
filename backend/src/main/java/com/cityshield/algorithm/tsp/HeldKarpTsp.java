package com.cityshield.algorithm.tsp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Exact TSP by Held-Karp dynamic programming over subsets. Time O(2^n * n^2), space O(2^n * n),
 * so it is restricted to at most {@link #MAX_NODES} nodes (depot included).
 */
public final class HeldKarpTsp {
    /** Maximum number of nodes (including the depot). */
    public static final int MAX_NODES = 13;

    private HeldKarpTsp() {
    }

    public static TspResult solve(double[][] d) {
        if (d == null || d.length == 0) {
            throw new IllegalArgumentException("distance matrix must not be empty");
        }
        int n = d.length;
        if (n > MAX_NODES) {
            throw new IllegalArgumentException("Held-Karp supports at most " + MAX_NODES + " nodes but got " + n);
        }
        if (n == 1) {
            return new TspResult(List.of(0, 0), 0, Double.NaN, List.of(), List.of());
        }
        int m = n - 1; // nodes 1..n-1 are bits 0..m-1
        int full = 1 << m;
        double[][] dp = new double[full][m];
        int[][] prev = new int[full][m];
        for (double[] row : dp) {
            java.util.Arrays.fill(row, Double.POSITIVE_INFINITY);
        }
        for (int j = 0; j < m; j++) {
            dp[1 << j][j] = d[0][j + 1];
            prev[1 << j][j] = -1;
        }
        for (int mask = 1; mask < full; mask++) {
            for (int j = 0; j < m; j++) {
                if ((mask >> j & 1) == 0 || dp[mask][j] == Double.POSITIVE_INFINITY) {
                    continue;
                }
                for (int k = 0; k < m; k++) {
                    if ((mask >> k & 1) != 0) {
                        continue;
                    }
                    int nm = mask | (1 << k);
                    double cand = dp[mask][j] + d[j + 1][k + 1];
                    if (cand < dp[nm][k]) {
                        dp[nm][k] = cand;
                        prev[nm][k] = j;
                    }
                }
            }
        }
        double best = Double.POSITIVE_INFINITY;
        int last = -1;
        for (int j = 0; j < m; j++) {
            double c = dp[full - 1][j] + d[j + 1][0];
            if (c < best) {
                best = c;
                last = j;
            }
        }
        List<Integer> rev = new ArrayList<>();
        int mask = full - 1;
        int cur = last;
        while (cur >= 0) {
            rev.add(cur + 1);
            int p = prev[mask][cur];
            mask &= ~(1 << cur);
            cur = p;
        }
        Collections.reverse(rev);
        List<Integer> tour = new ArrayList<>();
        tour.add(0);
        tour.addAll(rev);
        tour.add(0);
        return new TspResult(tour, best, Double.NaN, List.of(), List.of());
    }
}
