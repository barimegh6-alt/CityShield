package com.cityshield.algorithm.dsu;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConnectivityAnalyzerTest {

    @Test
    @DisplayName("Chain graph with nothing blocked: one component, every road critical")
    void chain() {
        List<Edge> es = List.of(new Edge(1, 0, 1), new Edge(2, 1, 2), new Edge(3, 2, 3));
        ConnectivityResult r = ConnectivityAnalyzer.analyze(4, es, Set.of());
        assertEquals(1, r.componentCount());
        assertEquals(4, r.components().get(0).size());
        assertTrue(r.components().get(0).isLargest());
        assertTrue(r.isolatedZones().isEmpty());
        assertEquals(List.of(1L, 2L, 3L), r.criticalEdgeIds());
        assertEquals(3, r.unions());
    }

    @Test
    @DisplayName("Cycle has no critical roads; blocking one road makes the others critical")
    void cycle() {
        List<Edge> es = List.of(new Edge(1, 0, 1), new Edge(2, 1, 2), new Edge(3, 2, 0));
        assertTrue(ConnectivityAnalyzer.analyze(3, es, Set.of()).criticalEdgeIds().isEmpty());
        ConnectivityResult r = ConnectivityAnalyzer.analyze(3, es, Set.of(2L));
        assertEquals(1, r.componentCount());
        assertEquals(2, r.openEdgeCount());
        assertEquals(List.of(1L, 3L), r.criticalEdgeIds());
    }

    @Test
    @DisplayName("Blocking roads splits the graph and exposes isolated zones")
    void blockedCreatesIsolation() {
        List<Edge> es = List.of(new Edge(10, 0, 1), new Edge(11, 1, 2), new Edge(12, 3, 4));
        ConnectivityResult r = ConnectivityAnalyzer.analyze(6, es, Set.of(11L));
        // components: {0,1} {2} {3,4} {5}
        assertEquals(4, r.componentCount());
        assertEquals(List.of(2, 5), r.isolatedZones());
        assertEquals(2, r.components().get(0).size());
        assertTrue(r.components().get(0).isLargest());
        assertFalse(r.components().get(2).isLargest());
        assertEquals(List.of(0, 1), r.components().get(0).members());
        assertEquals(r.zoneComponent()[0], r.zoneComponent()[1]);
        assertNotEquals(r.zoneComponent()[1], r.zoneComponent()[2]);
    }

    @Test
    @DisplayName("Parallel roads and self-loops are never critical")
    void parallelAndSelfLoop() {
        List<Edge> es = List.of(new Edge(1, 0, 1), new Edge(2, 0, 1), new Edge(3, 1, 1), new Edge(4, 1, 2));
        ConnectivityResult r = ConnectivityAnalyzer.analyze(3, es, null);
        assertEquals(List.of(4L), r.criticalEdgeIds());
    }

    @Test
    @DisplayName("No zones and no edges give an empty result; all zones isolated when no edges")
    void emptyCases() {
        ConnectivityResult e = ConnectivityAnalyzer.analyze(0, List.of(), Set.of());
        assertEquals(0, e.componentCount());
        assertTrue(e.components().isEmpty());
        ConnectivityResult iso = ConnectivityAnalyzer.analyze(3, List.of(), Set.of());
        assertEquals(3, iso.componentCount());
        assertEquals(List.of(0, 1, 2), iso.isolatedZones());
        assertEquals(1, ConnectivityAnalyzer.analyze(1, List.of(), Set.of()).componentCount());
    }

    @Test
    @DisplayName("Invalid input: negative zone count, null edges, endpoint out of range")
    void invalid() {
        assertThrows(IllegalArgumentException.class, () -> ConnectivityAnalyzer.analyze(-1, List.of(), Set.of()));
        assertThrows(IllegalArgumentException.class, () -> ConnectivityAnalyzer.analyze(2, null, Set.of()));
        assertThrows(IllegalArgumentException.class,
                () -> ConnectivityAnalyzer.analyze(2, List.of(new Edge(1, 0, 2)), Set.of()));
    }

    @Test
    @DisplayName("Randomized (seed 99): components and critical roads match BFS brute force")
    void randomizedVsBfs() {
        Random rnd = new Random(99);
        for (int trial = 0; trial < 60; trial++) {
            int n = 1 + rnd.nextInt(20);
            int m = rnd.nextInt(30);
            List<Edge> es = new ArrayList<>();
            for (int i = 0; i < m; i++) es.add(new Edge(100 + i, rnd.nextInt(n), rnd.nextInt(n)));
            java.util.Set<Long> blocked = new java.util.HashSet<>();
            for (Edge e : es) if (rnd.nextInt(4) == 0) blocked.add(e.id());
            ConnectivityResult r = ConnectivityAnalyzer.analyze(n, es, blocked);

            List<Edge> open = es.stream().filter(e -> !blocked.contains(e.id())).toList();
            int expected = bfsComponents(n, open, -1);
            assertEquals(expected, r.componentCount(), "trial " + trial);
            int sum = 0;
            for (Component c : r.components()) sum += c.size();
            assertEquals(n, sum);
            assertEquals(1, r.components().stream().filter(Component::isLargest).count());
            List<Long> crit = new ArrayList<>();
            for (int i = 0; i < open.size(); i++) {
                if (bfsComponents(n, open, i) > expected) crit.add(open.get(i).id());
            }
            assertEquals(crit, r.criticalEdgeIds(), "trial " + trial);
        }
    }

    private static int bfsComponents(int n, List<Edge> edges, int skip) {
        List<List<Integer>> adj = new ArrayList<>();
        for (int i = 0; i < n; i++) adj.add(new ArrayList<>());
        for (int i = 0; i < edges.size(); i++) {
            if (i == skip) continue;
            adj.get(edges.get(i).a()).add(edges.get(i).b());
            adj.get(edges.get(i).b()).add(edges.get(i).a());
        }
        boolean[] seen = new boolean[n];
        int comps = 0;
        for (int s = 0; s < n; s++) {
            if (seen[s]) continue;
            comps++;
            ArrayDeque<Integer> q = new ArrayDeque<>();
            q.add(s);
            seen[s] = true;
            while (!q.isEmpty()) {
                int u = q.poll();
                for (int v : adj.get(u)) if (!seen[v]) { seen[v] = true; q.add(v); }
            }
        }
        return comps;
    }
}
