package com.cityshield.algorithm.matching;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KuhnMatcherTest {

    private static int brute(int[][] adj, int s, boolean[] used) {
        if (s == adj.length) {
            return 0;
        }
        int best = brute(adj, s + 1, used); // leave slot unmatched
        for (int t : adj[s]) {
            if (!used[t]) {
                used[t] = true;
                best = Math.max(best, 1 + brute(adj, s + 1, used));
                used[t] = false;
            }
        }
        return best;
    }

    private static void assertConsistent(int[][] adj, int teams, MatchingResult r) {
        int[] ms = r.matchOfSlot();
        int[] mt = r.matchOfTeam();
        boolean[] seen = new boolean[teams];
        int count = 0;
        for (int s = 0; s < ms.length; s++) {
            if (ms[s] == -1) {
                continue;
            }
            count++;
            assertFalse(seen[ms[s]], "team used twice");
            seen[ms[s]] = true;
            assertEquals(s, mt[ms[s]]);
            boolean eligible = false;
            for (int t : adj[s]) {
                eligible |= t == ms[s];
            }
            assertTrue(eligible, "assigned team not eligible");
        }
        assertEquals(count, r.size());
        assertEquals(ms.length - count, r.unmatchedSlots().size());
    }

    @Test
    @DisplayName("Earlier (higher priority) slot wins the only shared team")
    void priorityWins() {
        int[][] adj = {{0}, {0}};
        MatchingResult r = KuhnMatcher.match(adj, 1);
        assertEquals(1, r.size());
        assertEquals(0, r.matchOfSlot()[0]);
        assertEquals(-1, r.matchOfSlot()[1]);
        assertEquals(List.of(1), r.unmatchedSlots());
    }

    @Test
    @DisplayName("Augmenting path re-routes an earlier slot to free a team for a later one")
    void augmentingPathReroutes() {
        int[][] adj = {{0, 1}, {0}}; // slot0 grabs team0 first, slot1 forces slot0 to team1
        MatchingResult r = KuhnMatcher.match(adj, 2);
        assertEquals(2, r.size());
        assertEquals(1, r.matchOfSlot()[0]);
        assertEquals(0, r.matchOfSlot()[1]);
        assertTrue(r.unmatchedSlots().isEmpty());
        assertTrue(r.dfsVisits() >= 3);
    }

    @Test
    @DisplayName("Adjacency order expresses preference: first listed team is chosen when free")
    void preferenceOrder() {
        int[][] adj = {{2, 0, 1}};
        assertEquals(2, KuhnMatcher.match(adj, 3).matchOfSlot()[0]);
    }

    @Test
    @DisplayName("Kuhn equals brute-force maximum matching on random tiny graphs")
    void bruteForceCrossCheck() {
        Random rnd = new Random(2024);
        for (int iter = 0; iter < 300; iter++) {
            int slots = rnd.nextInt(7);
            int teams = rnd.nextInt(6);
            int[][] adj = new int[slots][];
            for (int s = 0; s < slots; s++) {
                List<Integer> row = new ArrayList<>();
                for (int t = 0; t < teams; t++) {
                    if (rnd.nextInt(3) == 0) {
                        row.add(t);
                    }
                }
                java.util.Collections.shuffle(row, rnd);
                adj[s] = row.stream().mapToInt(Integer::intValue).toArray();
            }
            MatchingResult r = KuhnMatcher.match(adj, teams);
            assertConsistent(adj, teams, r);
            assertEquals(brute(adj, 0, new boolean[teams]), r.size(), "iteration " + iter);
        }
    }

    @Test
    @DisplayName("A slot matched earlier is never unmatched by later slots (prefix monotonicity)")
    void matchedSlotsStayMatched() {
        Random rnd = new Random(77);
        for (int iter = 0; iter < 100; iter++) {
            int slots = 8;
            int teams = 5;
            int[][] adj = new int[slots][];
            for (int s = 0; s < slots; s++) {
                List<Integer> row = new ArrayList<>();
                for (int t = 0; t < teams; t++) {
                    if (rnd.nextInt(3) == 0) {
                        row.add(t);
                    }
                }
                adj[s] = row.stream().mapToInt(Integer::intValue).toArray();
            }
            boolean[] wasMatched = new boolean[slots];
            for (int prefix = 1; prefix <= slots; prefix++) {
                int[][] sub = java.util.Arrays.copyOf(adj, prefix);
                MatchingResult r = KuhnMatcher.match(sub, teams);
                int[] ms = r.matchOfSlot();
                for (int s = 0; s < prefix - 1; s++) {
                    if (wasMatched[s]) {
                        assertNotEquals(-1, ms[s], "slot " + s + " lost its team at prefix " + prefix);
                    }
                }
                for (int s = 0; s < prefix; s++) {
                    wasMatched[s] = ms[s] != -1;
                }
            }
        }
    }

    @Test
    @DisplayName("Empty inputs, zero teams and empty adjacency rows are handled")
    void edgeCases() {
        MatchingResult none = KuhnMatcher.match(new int[0][], 3);
        assertEquals(0, none.size());
        assertEquals(3, none.matchOfTeam().length);
        MatchingResult noTeams = KuhnMatcher.match(new int[][] {{}, {}}, 0);
        assertEquals(0, noTeams.size());
        assertEquals(List.of(0, 1), noTeams.unmatchedSlots());
    }

    @Test
    @DisplayName("Out-of-range team index and negative team count are rejected")
    void validation() {
        assertThrows(IllegalArgumentException.class, () -> KuhnMatcher.match(new int[][] {{3}}, 3));
        assertThrows(IllegalArgumentException.class, () -> KuhnMatcher.match(new int[][] {{-1}}, 3));
        assertThrows(IllegalArgumentException.class, () -> KuhnMatcher.match(new int[][] {{0}}, -1));
        assertThrows(IllegalArgumentException.class, () -> KuhnMatcher.match((int[][]) null, 1));
    }

    @Test
    @DisplayName("List overload agrees with array overload and results are defensive copies")
    void listOverloadAndImmutability() {
        List<List<Integer>> adj = List.of(List.of(0, 1), List.of(0));
        MatchingResult r = KuhnMatcher.match(adj, 2);
        assertEquals(2, r.size());
        r.matchOfSlot()[0] = 99;
        assertEquals(1, r.matchOfSlot()[0]);
        assertThrows(UnsupportedOperationException.class, () -> r.unmatchedSlots().add(1));
    }

    @Test
    @DisplayName("Large chain graph needing long augmenting paths still reaches a perfect matching")
    void longAugmentingChain() {
        int n = 500;
        int[][] adj = new int[n][];
        for (int s = 0; s < n; s++) {
            adj[s] = s == n - 1 ? new int[] {0} : new int[] {s, s + 1};
        }
        // last slot only accepts team 0, forcing a cascade of n-1 re-assignments
        MatchingResult r = KuhnMatcher.match(adj, n);
        assertEquals(n, r.size());
        assertConsistent(adj, n, r);
    }
}
