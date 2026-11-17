package com.cityshield.algorithm.tsp;

import java.util.ArrayList;
import java.util.List;

/** Baseline heuristic: always travel to the nearest unvisited node, then return to the depot. O(n^2). */
public final class NearestNeighbourTsp {
    private NearestNeighbourTsp() {
    }

    public static TspResult solve(double[][] d) {
        if (d == null || d.length == 0) {
            throw new IllegalArgumentException("distance matrix must not be empty");
        }
        int n = d.length;
        boolean[] seen = new boolean[n];
        List<Integer> tour = new ArrayList<>();
        int cur = 0;
        seen[0] = true;
        tour.add(0);
        double len = 0;
        for (int step = 1; step < n; step++) {
            int best = -1;
            for (int v = 0; v < n; v++) {
                if (!seen[v] && (best < 0 || d[cur][v] < d[cur][best])) {
                    best = v;
                }
            }
            len += d[cur][best];
            seen[best] = true;
            tour.add(best);
            cur = best;
        }
        len += d[cur][0];
        tour.add(0);
        return new TspResult(tour, len, Double.NaN, List.of(), List.of());
    }
}
