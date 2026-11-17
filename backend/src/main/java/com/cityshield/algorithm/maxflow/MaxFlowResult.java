package com.cityshield.algorithm.maxflow;

import java.util.List;

/**
 * Immutable result of {@link EdmondsKarp#solve}.
 *
 * @param maxFlow         value of the maximum flow (equals {@code minCutCapacity})
 * @param edgeFlows       flow on each forward edge, indexed by forward edge id (unmodifiable)
 * @param steps           augmenting paths in the order they were applied
 * @param sourceSide      nodes reachable from the source in the final residual graph (the S side of the min cut), ascending
 * @param cutEdgeIds      forward edges leaving S towards T that have positive capacity (all saturated): the bottlenecks
 * @param minCutCapacity  sum of capacities of {@code cutEdgeIds}
 * @param bfsRounds       number of BFS runs (augmentations + the final failing one)
 */
public record MaxFlowResult(long maxFlow,
                            List<Long> edgeFlows,
                            List<AugmentingStep> steps,
                            List<Integer> sourceSide,
                            List<Integer> cutEdgeIds,
                            long minCutCapacity,
                            int bfsRounds) {
    public MaxFlowResult {
        edgeFlows = List.copyOf(edgeFlows);
        steps = List.copyOf(steps);
        sourceSide = List.copyOf(sourceSide);
        cutEdgeIds = List.copyOf(cutEdgeIds);
    }

    /** @return flow on forward edge {@code edgeId}. */
    public long flowOn(int edgeId) {
        return edgeFlows.get(edgeId);
    }
}
