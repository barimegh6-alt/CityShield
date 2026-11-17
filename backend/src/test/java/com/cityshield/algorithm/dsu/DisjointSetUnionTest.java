package com.cityshield.algorithm.dsu;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DisjointSetUnionTest {

    @Test
    @DisplayName("New DSU has n singleton components")
    void singletons() {
        DisjointSetUnion d = new DisjointSetUnion(5);
        assertEquals(5, d.getComponentCount());
        assertFalse(d.connected(0, 1));
        assertEquals(1, d.sizeOf(3));
    }

    @Test
    @DisplayName("Zero elements is valid; negative count is rejected")
    void emptyAndInvalid() {
        assertEquals(0, new DisjointSetUnion(0).getComponentCount());
        assertThrows(IllegalArgumentException.class, () -> new DisjointSetUnion(-1));
        DisjointSetUnion d = new DisjointSetUnion(3);
        assertThrows(IllegalArgumentException.class, () -> d.find(3));
        assertThrows(IllegalArgumentException.class, () -> d.union(-1, 0));
    }

    @Test
    @DisplayName("union merges sets, reports no-op for repeats, tracks sizes and counts")
    void unionBehaviour() {
        DisjointSetUnion d = new DisjointSetUnion(6);
        assertTrue(d.union(0, 1));
        assertTrue(d.union(1, 2));
        assertFalse(d.union(0, 2));
        assertTrue(d.connected(0, 2));
        assertEquals(3, d.sizeOf(2));
        assertEquals(4, d.getComponentCount());
        assertEquals(2, d.getUnionCount());
        assertEquals(3, d.getUnionAttempts());
        assertTrue(d.getFindCount() >= 6);
        assertEquals(List.of(3, 1, 1, 1), d.componentSizes());
    }

    @Test
    @DisplayName("Path compression flattens a long chain after find")
    void pathCompressionAndRank() {
        DisjointSetUnion d = new DisjointSetUnion(1000);
        for (int i = 0; i < 999; i++) d.union(i, i + 1);
        assertEquals(1, d.getComponentCount());
        int root = d.find(0);
        for (int i = 0; i < 1000; i++) assertEquals(root, d.find(i));
        assertEquals(1000, d.sizeOf(500));
        assertEquals(root, d.componentId(999));
    }

    @Test
    @DisplayName("Self-union is a no-op")
    void selfUnion() {
        DisjointSetUnion d = new DisjointSetUnion(2);
        assertFalse(d.union(1, 1));
        assertEquals(2, d.getComponentCount());
    }
}
