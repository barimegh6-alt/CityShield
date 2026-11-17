package com.cityshield.algorithm.maxflow;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EdmondsKarpTest {

    private static FlowNetwork clrs() {
        FlowNetwork g = new FlowNetwork();
        for (int i = 0; i < 6; i++) {
            g.addNode("n" + i);
        }
        int[][] e = {{0, 1, 16}, {0, 2, 13}, {1, 2, 10}, {2, 1, 4}, {1, 3, 12}, {3, 2, 9},
            {2, 4, 14}, {4, 3, 7}, {3, 5, 20}, {4, 5, 4}};
        for (int[] x : e) {
            g.addEdge(x[0], x[1], x[2]);
        }
        return g;
    }

    /** Plain recursive Ford-Fulkerson with DFS on an adjacency matrix: independent oracle. */
    private static long fordFulkersonDfs(long[][] cap, int s, int t) {
        int n = cap.length;
        long[][] r = new long[n][];
        for (int i = 0; i < n; i++) {
            r[i] = cap[i].clone();
        }
        long flow = 0;
        while (true) {
            long pushed = dfs(r, s, t, Long.MAX_VALUE, new boolean[n]);
            if (pushed == 0) {
                return flow;
            }
            flow += pushed;
        }
    }

    private static long dfs(long[][] r, int u, int t, long limit, boolean[] vis) {
        if (u == t) {
            return limit;
        }
        vis[u] = true;
        for (int v = 0; v < r.length; v++) {
            if (!vis[v] && r[u][v] > 0) {
                long got = dfs(r, v, t, Math.min(limit, r[u][v]), vis);
                if (got > 0) {
                    r[u][v] -= got;
                    r[v][u] += got;
                    return got;
                }
            }
        }
        return 0;
    }

    @Test
    @DisplayName("Classic CLRS network has max flow 23 and min cut equal to it")
    void classicNetwork() {
        MaxFlowResult r = EdmondsKarp.solve(clrs(), 0, 5);
        assertEquals(23, r.maxFlow());
        assertEquals(23, r.minCutCapacity());
        assertTrue(r.sourceSide().contains(0));
        assertFalse(r.sourceSide().contains(5));
        assertFalse(r.steps().isEmpty());
    }

    @Test
    @DisplayName("Disconnected source and sink give flow 0 and no augmenting paths")
    void disconnected() {
        FlowNetwork g = new FlowNetwork();
        int a = g.addNode("a");
        int b = g.addNode("b");
        int c = g.addNode("c");
        g.addEdge(a, b, 5);
        MaxFlowResult r = EdmondsKarp.solve(g, a, c);
        assertEquals(0, r.maxFlow());
        assertTrue(r.steps().isEmpty());
        assertTrue(r.cutEdgeIds().isEmpty());
        assertEquals(java.util.List.of(a, b), r.sourceSide());
    }

    @Test
    @DisplayName("Zero-capacity edges are accepted and carry no flow")
    void zeroCapacityEdges() {
        FlowNetwork g = new FlowNetwork();
        int s = g.addNode("s");
        int m = g.addNode("m");
        int t = g.addNode("t");
        int e1 = g.addEdge(s, m, 0);
        int e2 = g.addEdge(m, t, 7);
        int e3 = g.addEdge(s, t, 3);
        MaxFlowResult r = EdmondsKarp.solve(g, s, t);
        assertEquals(3, r.maxFlow());
        assertEquals(0, r.flowOn(e1));
        assertEquals(0, r.flowOn(e2));
        assertEquals(3, r.flowOn(e3));
        assertEquals(java.util.List.of(e3), r.cutEdgeIds());
    }

    @Test
    @DisplayName("Invalid input (negative capacity, source==sink, unknown nodes) is rejected")
    void validation() {
        FlowNetwork g = new FlowNetwork();
        int a = g.addNode("a");
        int b = g.addNode("b");
        assertThrows(IllegalArgumentException.class, () -> g.addEdge(a, b, -1));
        assertThrows(IllegalArgumentException.class, () -> g.addEdge(a, 9, 1));
        assertThrows(IllegalArgumentException.class, () -> g.addEdge(-1, b, 1));
        assertThrows(IllegalArgumentException.class, () -> EdmondsKarp.solve(g, a, a));
        assertThrows(IllegalArgumentException.class, () -> EdmondsKarp.solve(g, a, 5));
        assertThrows(IllegalArgumentException.class, () -> EdmondsKarp.solve(g, -1, b));
    }

    @Test
    @DisplayName("Random graphs: Edmonds-Karp == Ford-Fulkerson DFS == brute-force minimum cut")
    void randomCrossCheck() {
        Random rnd = new Random(12345);
        for (int iter = 0; iter < 200; iter++) {
            int n = 2 + rnd.nextInt(6);
            long[][] cap = new long[n][n];
            FlowNetwork g = new FlowNetwork();
            for (int i = 0; i < n; i++) {
                g.addNode("v" + i);
            }
            int edges = rnd.nextInt(n * 3);
            for (int k = 0; k < edges; k++) {
                int u = rnd.nextInt(n);
                int v = rnd.nextInt(n);
                if (u == v) {
                    continue;
                }
                long c = rnd.nextInt(10);
                g.addEdge(u, v, c);
                cap[u][v] += c;
            }
            int s = 0;
            int t = n - 1;
            long expected = fordFulkersonDfs(cap, s, t);
            MaxFlowResult r = EdmondsKarp.solve(g, s, t);
            assertEquals(expected, r.maxFlow(), "iteration " + iter);
            assertEquals(r.maxFlow(), r.minCutCapacity(), "cut == flow, iteration " + iter);

            long best = Long.MAX_VALUE;
            for (int mask = 0; mask < (1 << n); mask++) {
                if ((mask & 1) == 0 || (mask & (1 << t)) != 0) {
                    continue;
                }
                long c = 0;
                for (int u = 0; u < n; u++) {
                    for (int v = 0; v < n; v++) {
                        if ((mask & (1 << u)) != 0 && (mask & (1 << v)) == 0) {
                            c += cap[u][v];
                        }
                    }
                }
                best = Math.min(best, c);
            }
            assertEquals(best, r.maxFlow(), "brute-force min cut, iteration " + iter);
        }
    }

    @Test
    @DisplayName("Edge flows respect capacity and conservation; path bottlenecks sum to max flow")
    void flowInvariants() {
        FlowNetwork g = clrs();
        MaxFlowResult r = EdmondsKarp.solve(g, 0, 5);
        long[] net = new long[g.nodeCount()];
        for (int e = 0; e < g.edgeCount(); e++) {
            long f = r.flowOn(e);
            assertTrue(f >= 0 && f <= g.edgeCapacity(e));
            net[g.edgeFrom(e)] -= f;
            net[g.edgeTo(e)] += f;
        }
        for (int v = 1; v < 5; v++) {
            assertEquals(0, net[v], "conservation at " + v);
        }
        assertEquals(23, net[5]);
        long sum = 0;
        for (AugmentingStep st : r.steps()) {
            sum += st.bottleneck();
            assertEquals(0, st.nodes().get(0));
            assertEquals(5, st.nodes().get(st.nodes().size() - 1));
            assertTrue(st.bottleneck() > 0);
        }
        assertEquals(23, sum);
        for (int e : r.cutEdgeIds()) {
            assertEquals(g.edgeCapacity(e), r.flowOn(e), "cut edges are saturated");
        }
    }

    @Test
    @DisplayName("Super-source/super-sink: multi-supply multi-demand and the cut names the bottleneck")
    void superSourceSink() {
        FlowNetwork g = new FlowNetwork();
        int ss = g.addNode("SS");
        int w1 = g.addNode("W1");
        int w2 = g.addNode("W2");
        int hub = g.addNode("Hub");
        int d1 = g.addNode("D1");
        int d2 = g.addNode("D2");
        int tt = g.addNode("TT");
        g.addEdge(ss, w1, 40);
        g.addEdge(ss, w2, 30);
        g.addEdge(w1, hub, 25);
        g.addEdge(w2, hub, 20);
        int bridge = g.addEdge(hub, d1, 30);
        g.addEdge(hub, d2, 50);
        g.addEdge(d1, tt, 28);
        g.addEdge(d2, tt, 12);
        MaxFlowResult r = EdmondsKarp.solve(g, ss, tt);
        // hub receives at most 45; demand sink side takes 28 + 12 = 40
        assertEquals(40, r.maxFlow());
        assertEquals(40, r.minCutCapacity());
        assertFalse(r.cutEdgeIds().contains(bridge));
    }

    @Test
    @DisplayName("BFS picks shortest augmenting paths first and solving twice is repeatable")
    void shortestPathFirstAndRepeatable() {
        FlowNetwork g = new FlowNetwork();
        for (int i = 0; i < 4; i++) {
            g.addNode(null);
        }
        g.addEdge(0, 1, 5);
        g.addEdge(1, 3, 5);
        g.addEdge(0, 3, 2);
        g.addEdge(0, 2, 4);
        g.addEdge(2, 1, 4);
        MaxFlowResult a = EdmondsKarp.solve(g, 0, 3);
        MaxFlowResult b = EdmondsKarp.solve(g, 0, 3);
        assertEquals(java.util.List.of(0, 3), a.steps().get(0).nodes());
        assertEquals(a.maxFlow(), b.maxFlow());
        assertEquals(a.steps(), b.steps());
        assertEquals(7, a.maxFlow());
        assertEquals("3", g.label(3));
    }

    @Test
    @DisplayName("Parallel edges add up and antiparallel residual edges allow flow cancellation")
    void parallelEdges() {
        FlowNetwork g = new FlowNetwork();
        int s = g.addNode("s");
        int t = g.addNode("t");
        g.addEdge(s, t, 3);
        g.addEdge(s, t, 4);
        assertEquals(7, EdmondsKarp.solve(g, s, t).maxFlow());
    }
}
