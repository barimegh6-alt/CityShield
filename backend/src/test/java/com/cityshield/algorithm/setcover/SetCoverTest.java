package com.cityshield.algorithm.setcover;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SetCoverTest {
    private static CoverSet s(long id, double cost, int... e) {
        return new CoverSet(id, cost, e);
    }

    @Test
    @DisplayName("Greedy covers a simple universe picking best ratio first")
    void simple() {
        List<CoverSet> sets = List.of(s(1, 1, 0, 1, 2), s(2, 1, 3), s(3, 1, 3, 4));
        CoverResult r = GreedySetCover.solve(5, sets, null, null);
        assertEquals(List.of(1L, 3L), r.selectedIds());
        assertEquals(5, r.coveredCount());
        assertTrue(r.uncovered().isEmpty());
        assertEquals(2.0, r.totalCost(), 1e-9);
    }

    @Test
    @DisplayName("Classic instance: greedy is strictly worse than the exact optimum")
    void greedySuboptimal() {
        // Universe 0..5. Optimal = {A,B} (cost 2). Greedy takes big set C first (4 elements), then needs 2 more.
        CoverSet a = s(1, 1, 0, 1, 2);
        CoverSet b = s(2, 1, 3, 4, 5);
        CoverSet c = s(3, 1, 0, 1, 3, 4);
        CoverSet d = s(4, 1, 2);
        CoverSet e = s(5, 1, 5);
        List<CoverSet> sets = List.of(a, b, c, d, e);
        CoverResult greedy = GreedySetCover.solve(6, sets, null, null);
        CoverResult exact = ExactSetCover.solve(6, sets, null);
        assertEquals(2.0, exact.totalCost(), 1e-9);
        assertEquals(3.0, greedy.totalCost(), 1e-9);
        assertTrue(greedy.totalCost() > exact.totalCost());
        assertEquals(32, exact.subsetsChecked());
    }

    @Test
    @DisplayName("Cost-weighted ratio prefers cheap sets over large expensive ones")
    void costWeighted() {
        List<CoverSet> sets = List.of(s(1, 10, 0, 1, 2, 3), s(2, 1, 0, 1), s(3, 1, 2, 3));
        CoverResult r = GreedySetCover.solve(4, sets, null, null);
        assertEquals(List.of(2L, 3L), r.selectedIds());
        assertEquals(2.0, r.totalCost(), 1e-9);
    }

    @Test
    @DisplayName("Forced (existing) sets are selected first at zero cost")
    void forced() {
        List<CoverSet> sets = List.of(s(1, 5, 0, 1), s(2, 1, 2), s(3, 1, 3));
        CoverResult r = GreedySetCover.solve(4, sets, Set.of(1L), null);
        assertEquals(1L, r.selectedIds().get(0));
        assertTrue(r.steps().get(0).forced());
        assertEquals(2.0, r.totalCost(), 1e-9);
        assertEquals(4, r.coveredCount());
    }

    @Test
    @DisplayName("Budget on number of new sets stops early and reports uncovered elements")
    void budget() {
        List<CoverSet> sets = List.of(s(1, 1, 0), s(2, 1, 1), s(3, 1, 2));
        CoverResult r = GreedySetCover.solve(3, sets, null, 2);
        assertEquals(2, r.selectedIds().size());
        assertEquals(1, r.uncovered().size());
    }

    @Test
    @DisplayName("Elements in no set remain uncovered; empty universe and no sets are handled")
    void uncoverable() {
        CoverResult r = GreedySetCover.solve(3, List.of(s(1, 1, 0)), null, null);
        assertEquals(List.of(1, 2), r.uncovered());
        assertEquals(0, GreedySetCover.solve(0, List.of(), null, null).coveredCount());
        CoverResult ex = ExactSetCover.solve(3, List.of(s(1, 1, 0)), null);
        assertEquals(List.of(1L), ex.selectedIds());
    }

    @Test
    @DisplayName("Invalid input: element out of range, negative cost, too many sets for exact")
    void invalid() {
        assertThrows(IllegalArgumentException.class, () -> GreedySetCover.solve(2, List.of(s(1, 1, 5)), null, null));
        assertThrows(IllegalArgumentException.class, () -> s(1, -1, 0));
        List<CoverSet> many = new java.util.ArrayList<>();
        for (int i = 0; i < 25; i++) {
            many.add(s(i, 1, 0));
        }
        assertThrows(IllegalArgumentException.class, () -> ExactSetCover.solve(1, many, null));
    }

    @Test
    @DisplayName("Randomized (seed 7): greedy never beats exact; both cover the same coverable universe")
    void randomized() {
        Random r = new Random(7);
        for (int t = 0; t < 200; t++) {
            int n = 4 + r.nextInt(8);
            int m = 2 + r.nextInt(7);
            java.util.ArrayList<CoverSet> sets = new java.util.ArrayList<>();
            for (int i = 0; i < m; i++) {
                int sz = 1 + r.nextInt(n);
                int[] el = new int[sz];
                for (int j = 0; j < sz; j++) {
                    el[j] = r.nextInt(n);
                }
                sets.add(s(i, 1 + r.nextInt(5), el));
            }
            CoverResult g = GreedySetCover.solve(n, sets, null, null);
            CoverResult e = ExactSetCover.solve(n, sets, null);
            assertEquals(e.coveredCount(), g.coveredCount());
            assertTrue(g.totalCost() >= e.totalCost() - 1e-9);
            CoverResult b = SizeBaselineCover.solve(n, sets, null);
            assertEquals(e.coveredCount(), b.coveredCount());
        }
    }
}
