package com.cityshield.algorithm.tsp;

import java.util.ArrayList;
import java.util.List;

/**
 * MST-based "double-tree" 2-approximation for metric TSP.
 *
 * <ol>
 *   <li>Validate the matrix is a metric (symmetric, zero diagonal, non-negative, triangle inequality).</li>
 *   <li>Build a minimum spanning tree with Prim's algorithm rooted at the depot (index 0).</li>
 *   <li>Take a preorder walk of the tree (children in ascending index order).</li>
 *   <li>Shortcut repeated visits, close the tour at the depot.</li>
 * </ol>
 *
 * <p>Guarantee: tour length &le; 2 &middot; OPT, because MST &le; OPT and, by the triangle inequality,
 * shortcutting the doubled tree walk (length 2&middot;MST) never increases length. The guarantee holds
 * only for metric distances. Time O(n^2) with the array-based Prim, space O(n).</p>
 */
public final class MetricTspApproximation {
    private static final double TOL = 1e-9;

    private MetricTspApproximation() {
    }

    /** Validates that {@code d} is a metric; throws IllegalArgumentException otherwise. */
    public static void validateMetric(double[][] d) {
        if (d == null || d.length == 0) {
            throw new IllegalArgumentException("distance matrix must not be empty");
        }
        int n = d.length;
        for (int i = 0; i < n; i++) {
            if (d[i] == null || d[i].length != n) {
                throw new IllegalArgumentException("distance matrix must be square");
            }
        }
        for (int i = 0; i < n; i++) {
            if (Math.abs(d[i][i]) > TOL) {
                throw new IllegalArgumentException("diagonal must be zero at index " + i);
            }
            for (int j = 0; j < n; j++) {
                if (Double.isNaN(d[i][j]) || d[i][j] < 0) {
                    throw new IllegalArgumentException("distances must be non-negative numbers");
                }
                if (Math.abs(d[i][j] - d[j][i]) > TOL) {
                    throw new IllegalArgumentException("matrix must be symmetric at (" + i + "," + j + ")");
                }
            }
        }
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                for (int k = 0; k < n; k++) {
                    if (d[i][j] > d[i][k] + d[k][j] + TOL) {
                        throw new IllegalArgumentException("triangle inequality violated for (" + i + "," + j + "," + k + ")");
                    }
                }
            }
        }
    }

    /** Computes the approximate tour starting and ending at node 0. */
    public static TspResult solve(double[][] d) {
        validateMetric(d);
        int n = d.length;
        // Prim's MST
        int[] parent = new int[n];
        double[] key = new double[n];
        boolean[] in = new boolean[n];
        java.util.Arrays.fill(key, Double.POSITIVE_INFINITY);
        java.util.Arrays.fill(parent, -1);
        key[0] = 0;
        double mst = 0;
        List<int[]> edges = new ArrayList<>();
        for (int iter = 0; iter < n; iter++) {
            int u = -1;
            for (int v = 0; v < n; v++) {
                if (!in[v] && (u < 0 || key[v] < key[u])) {
                    u = v;
                }
            }
            in[u] = true;
            if (parent[u] >= 0) {
                mst += key[u];
                edges.add(new int[]{parent[u], u});
            }
            for (int v = 0; v < n; v++) {
                if (!in[v] && d[u][v] < key[v]) {
                    key[v] = d[u][v];
                    parent[v] = u;
                }
            }
        }
        // children lists in ascending order
        List<List<Integer>> children = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            children.add(new ArrayList<>());
        }
        for (int v = 1; v < n; v++) {
            children.get(parent[v]).add(v); // v ascending => children ascending
        }
        // iterative preorder
        List<Integer> pre = new ArrayList<>();
        int[] stack = new int[n + 1];
        int sp = 0;
        stack[sp++] = 0;
        while (sp > 0) {
            int u = stack[--sp];
            pre.add(u);
            List<Integer> ch = children.get(u);
            for (int i = ch.size() - 1; i >= 0; i--) {
                stack[sp++] = ch.get(i);
            }
        }
        List<Integer> tour = new ArrayList<>(pre);
        tour.add(0);
        double len = 0;
        for (int i = 0; i + 1 < tour.size(); i++) {
            len += d[tour.get(i)][tour.get(i + 1)];
        }
        return new TspResult(tour, len, mst, edges, pre);
    }
}
