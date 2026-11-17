package com.cityshield.algorithm.maxflow;

import java.util.ArrayList;
import java.util.List;

/**
 * Edmonds-Karp maximum flow: Ford-Fulkerson where every augmenting path is a BFS shortest path
 * (fewest edges) in the residual graph.
 *
 * <p>Complexity: at most O(V*E) augmentations, each BFS is O(E), so O(V*E^2) time; O(V + E) space.
 * Residual capacities live in a private array, so the {@link FlowNetwork} is never modified and may
 * be solved repeatedly.
 *
 * <p>Min-cut: once no augmenting path exists, S = nodes reachable from the source in the residual
 * graph. Every forward edge from S to T is saturated and the sum of their capacities equals the
 * max flow (max-flow min-cut theorem); those edges are reported as bottlenecks.
 */
public final class EdmondsKarp {

    private EdmondsKarp() {
    }

    /**
     * Computes the maximum flow and a minimum cut.
     *
     * @param net    the network (not modified)
     * @param source source node
     * @param sink   sink node
     * @return flow, augmenting paths and minimum cut
     * @throws IllegalArgumentException if a node is unknown or {@code source == sink}
     * @throws ArithmeticException      if the flow value overflows a {@code long}
     */
    public static MaxFlowResult solve(FlowNetwork net, int source, int sink) {
        if (net == null) {
            throw new IllegalArgumentException("network is null");
        }
        if (!net.hasNode(source) || !net.hasNode(sink)) {
            throw new IllegalArgumentException("Unknown source or sink node");
        }
        if (source == sink) {
            throw new IllegalArgumentException("source and sink must differ");
        }
        final int n = net.nodeCount();
        final int m = net.edgeCount();
        // arc 2e = forward edge e, arc 2e+1 = its reverse
        final int[] head = new int[2 * m];
        final long[] residual = new long[2 * m];
        final List<List<Integer>> adj = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            adj.add(new ArrayList<>());
        }
        for (int e = 0; e < m; e++) {
            int u = net.edgeFrom(e);
            int v = net.edgeTo(e);
            head[2 * e] = v;
            head[2 * e + 1] = u;
            residual[2 * e] = net.edgeCapacity(e);
            adj.get(u).add(2 * e);
            adj.get(v).add(2 * e + 1);
        }

        long total = 0;
        final List<AugmentingStep> steps = new ArrayList<>();
        final int[] prevArc = new int[n];
        final boolean[] seen = new boolean[n];
        final int[] queue = new int[n];
        int rounds = 0;

        while (true) {
            rounds++;
            java.util.Arrays.fill(seen, false);
            java.util.Arrays.fill(prevArc, -1);
            int qh = 0;
            int qt = 0;
            queue[qt++] = source;
            seen[source] = true;
            while (qh < qt && !seen[sink]) {
                int u = queue[qh++];
                for (int arc : adj.get(u)) {
                    int v = head[arc];
                    if (!seen[v] && residual[arc] > 0) {
                        seen[v] = true;
                        prevArc[v] = arc;
                        queue[qt++] = v;
                    }
                }
            }
            if (!seen[sink]) {
                break; // 'seen' now holds the reachable set only if BFS ran to exhaustion (it did: sink unseen)
            }
            long bottleneck = Long.MAX_VALUE;
            List<Integer> path = new ArrayList<>();
            for (int v = sink; v != source; v = head[prevArc[v] ^ 1]) {
                bottleneck = Math.min(bottleneck, residual[prevArc[v]]);
                path.add(v);
            }
            path.add(source);
            java.util.Collections.reverse(path);
            for (int v = sink; v != source; v = head[prevArc[v] ^ 1]) {
                residual[prevArc[v]] -= bottleneck;
                residual[prevArc[v] ^ 1] += bottleneck;
            }
            total = Math.addExact(total, bottleneck);
            steps.add(new AugmentingStep(steps.size() + 1, path, bottleneck));
        }

        List<Long> flows = new ArrayList<>(m);
        for (int e = 0; e < m; e++) {
            flows.add(net.edgeCapacity(e) - residual[2 * e]);
        }
        List<Integer> sourceSide = new ArrayList<>();
        for (int v = 0; v < n; v++) {
            if (seen[v]) {
                sourceSide.add(v);
            }
        }
        List<Integer> cut = new ArrayList<>();
        long cutCap = 0;
        for (int e = 0; e < m; e++) {
            if (seen[net.edgeFrom(e)] && !seen[net.edgeTo(e)] && net.edgeCapacity(e) > 0) {
                cut.add(e);
                cutCap += net.edgeCapacity(e);
            }
        }
        return new MaxFlowResult(total, flows, steps, sourceSide, cut, cutCap, rounds);
    }
}
