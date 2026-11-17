package com.cityshield.algorithm.segmenttree;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SegmentTreeTest {

    @Test
    @DisplayName("Range sums on a small array are correct")
    void basicSums() {
        SegmentTree t = new SegmentTree(new long[]{5, 3, 8, 1, 9, 2});
        assertEquals(28, t.total());
        assertEquals(28, t.sum(0, 5));
        assertEquals(12, t.sum(1, 3));
        assertEquals(8, t.sum(2, 2));
        assertEquals(6, t.size());
    }

    @Test
    @DisplayName("Single-element tree works for query, set and add")
    void singleElement() {
        SegmentTree t = new SegmentTree(new long[]{7});
        assertEquals(7, t.sum(0, 0));
        t.set(0, 3);
        t.add(0, 4);
        assertEquals(7, t.get(0));
        assertEquals(7, t.total());
    }

    @Test
    @DisplayName("Point set and add-delta update later queries")
    void updates() {
        SegmentTree t = new SegmentTree(new long[]{1, 2, 3, 4});
        t.set(1, 10);
        assertEquals(18, t.total());
        t.add(3, -2);
        assertEquals(16, t.sum(0, 3));
        assertEquals(2, t.get(3));
    }

    @Test
    @DisplayName("add may drive a value negative; the tree does not reject it")
    void negativeAllowed() {
        SegmentTree t = new SegmentTree(new long[]{2, 2});
        t.add(0, -5);
        assertEquals(-3, t.get(0));
        assertEquals(-1, t.total());
    }

    @Test
    @DisplayName("Invalid input: empty/null array, from>to, index out of range")
    void validation() {
        assertThrows(IllegalArgumentException.class, () -> new SegmentTree(new long[0]));
        assertThrows(IllegalArgumentException.class, () -> new SegmentTree(null));
        SegmentTree t = new SegmentTree(new long[]{1, 2, 3});
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> t.sum(2, 1));
        assertTrue(ex.getMessage().contains("from"));
        assertThrows(IllegalArgumentException.class, () -> t.sum(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> t.sum(0, 3));
        assertThrows(IllegalArgumentException.class, () -> t.set(3, 1));
        assertThrows(IllegalArgumentException.class, () -> t.add(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> t.get(5));
    }

    @Test
    @DisplayName("Input array is copied: later changes to it do not affect the tree")
    void defensiveCopy() {
        long[] a = {1, 2, 3};
        SegmentTree t = new SegmentTree(a);
        a[0] = 100;
        assertEquals(6, t.total());
    }

    @Test
    @DisplayName("Nodes visited is tracked and far below n for large arrays")
    void nodesVisited() {
        SegmentTree t = new SegmentTree(new long[1024]);
        assertEquals(0, t.getLastNodesVisited());
        t.sum(0, 1023);
        assertEquals(1, t.getLastNodesVisited());
        t.sum(100, 900);
        assertTrue(t.getLastNodesVisited() > 1 && t.getLastNodesVisited() < 60);
        t.set(5, 1);
        assertEquals(11, t.getLastNodesVisited());
    }

    @Test
    @DisplayName("Randomized (seed 2024): segment tree equals LinearScan after random updates")
    void randomizedVsLinear() {
        Random r = new Random(2024);
        for (int trial = 0; trial < 30; trial++) {
            int n = 1 + r.nextInt(200);
            long[] ref = new long[n];
            for (int i = 0; i < n; i++) ref[i] = r.nextInt(2001) - 1000;
            SegmentTree t = new SegmentTree(ref);
            for (int op = 0; op < 300; op++) {
                int i = r.nextInt(n);
                if (r.nextBoolean()) {
                    long v = r.nextInt(2001) - 1000;
                    t.set(i, v);
                    ref[i] = v;
                } else {
                    long d = r.nextInt(201) - 100;
                    t.add(i, d);
                    ref[i] += d;
                }
                int a = r.nextInt(n);
                int b = r.nextInt(n);
                int from = Math.min(a, b);
                int to = Math.max(a, b);
                LinearScan.Result lin = LinearScan.sum(ref, from, to);
                assertEquals(lin.sum(), t.sum(from, to));
                assertEquals(to - from + 1, lin.elementsScanned());
            }
        }
    }
}
