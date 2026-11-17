package com.cityshield.algorithm.maxflow;

import java.util.List;

/**
 * One augmenting path found by Edmonds-Karp.
 *
 * @param step       1-based iteration number
 * @param nodes      node indices from source to sink (inclusive)
 * @param bottleneck units of flow pushed along the path
 */
public record AugmentingStep(int step, List<Integer> nodes, long bottleneck) {
    public AugmentingStep {
        nodes = List.copyOf(nodes);
    }
}
