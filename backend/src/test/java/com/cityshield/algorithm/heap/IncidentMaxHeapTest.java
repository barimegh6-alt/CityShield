package com.cityshield.algorithm.heap;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class IncidentMaxHeapTest {

    private static HeapEntry e(long id, double p, long t) {
        return new HeapEntry(id, p, t);
    }

    @Test
    @DisplayName("Empty heap: size 0, peek null, extractMax throws, topN empty")
    void emptyHeap() {
        IncidentMaxHeap h = new IncidentMaxHeap();
        assertTrue(h.isEmpty());
        assertEquals(0, h.size());
        assertNull(h.peek());
        assertThrows(NoSuchElementException.class, h::extractMax);
        assertTrue(h.topN(5).isEmpty());
    }

    @Test
    @DisplayName("Single element is both peek and extractMax result")
    void singleElement() {
        IncidentMaxHeap h = new IncidentMaxHeap();
        h.insert(e(1, 42.5, 10));
        assertEquals(1, h.peek().id());
        assertEquals(1, h.extractMax().id());
        assertTrue(h.isEmpty());
    }

    @Test
    @DisplayName("Extraction order is descending by priority")
    void extractsInDescendingOrder() {
        IncidentMaxHeap h = new IncidentMaxHeap();
        double[] ps = {10, 90, 50, 70, 20, 99, 1};
        for (int i = 0; i < ps.length; i++) h.insert(e(i, ps[i], i));
        double prev = Double.MAX_VALUE;
        while (!h.isEmpty()) {
            double p = h.extractMax().priority();
            assertTrue(p <= prev);
            prev = p;
        }
    }

    @Test
    @DisplayName("Equal priorities: earlier reportedAt first, then lower id")
    void tieBreaking() {
        IncidentMaxHeap h = new IncidentMaxHeap();
        h.insert(e(5, 50, 200));
        h.insert(e(3, 50, 100));
        h.insert(e(2, 50, 200));
        h.insert(e(9, 50, 100));
        List<HeapEntry> top = h.topN(10);
        assertEquals(List.of(3L, 9L, 2L, 5L), top.stream().map(HeapEntry::id).toList());
    }

    @Test
    @DisplayName("updatePriority moves an element up and down correctly")
    void updateKey() {
        IncidentMaxHeap h = new IncidentMaxHeap();
        for (int i = 1; i <= 10; i++) h.insert(e(i, i * 10, i));
        assertTrue(h.updatePriority(1, 500));
        assertEquals(1, h.peek().id());
        assertTrue(h.updatePriority(1, -5));
        assertEquals(10, h.peek().id());
        assertTrue(h.isValidHeap());
        assertEquals(1, h.topN(10).get(9).id());
        assertFalse(h.updatePriority(999, 1));
    }

    @Test
    @DisplayName("remove-by-id works for top, middle, last and unknown ids")
    void removeById() {
        IncidentMaxHeap h = new IncidentMaxHeap();
        for (int i = 1; i <= 9; i++) h.insert(e(i, i, i));
        assertEquals(9, h.remove(9).id());
        assertEquals(5, h.remove(5).id());
        assertEquals(1, h.remove(1).id());
        assertNull(h.remove(42));
        assertFalse(h.contains(5));
        assertEquals(6, h.size());
        assertTrue(h.isValidHeap());
        assertEquals(8, h.peek().id());
    }

    @Test
    @DisplayName("Invalid input: null entry, duplicate id, NaN priority, negative limit")
    void invalidInput() {
        IncidentMaxHeap h = new IncidentMaxHeap();
        assertThrows(IllegalArgumentException.class, () -> h.insert(null));
        h.insert(e(1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> h.insert(e(1, 2, 2)));
        assertThrows(IllegalArgumentException.class, () -> e(2, Double.NaN, 1));
        assertThrows(IllegalArgumentException.class, () -> e(2, Double.POSITIVE_INFINITY, 1));
        assertThrows(IllegalArgumentException.class, () -> h.topN(-1));
        assertThrows(IllegalArgumentException.class, () -> h.buildHeap(null));
    }

    @Test
    @DisplayName("buildHeap (bottom-up heapify) yields a valid heap and rejects duplicate ids")
    void buildHeapWorks() {
        List<HeapEntry> in = new ArrayList<>();
        Random r = new Random(7);
        for (int i = 0; i < 100; i++) in.add(e(i, r.nextInt(50), r.nextInt(1000)));
        IncidentMaxHeap h = new IncidentMaxHeap();
        h.buildHeap(in);
        assertEquals(100, h.size());
        assertTrue(h.isValidHeap());
        in.add(e(0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new IncidentMaxHeap().buildHeap(in));
        IncidentMaxHeap empty = new IncidentMaxHeap();
        empty.buildHeap(List.of());
        assertTrue(empty.isEmpty());
    }

    @Test
    @DisplayName("topN is non-destructive and does not change counters")
    void topNNonDestructive() {
        IncidentMaxHeap h = new IncidentMaxHeap();
        for (int i = 0; i < 20; i++) h.insert(e(i, i % 7, i));
        HeapStats before = h.stats();
        List<HeapEntry> a = h.topN(5);
        List<HeapEntry> b = h.topN(5);
        assertEquals(5, a.size());
        assertEquals(a, b);
        assertEquals(20, h.size());
        assertEquals(before, h.stats());
        assertTrue(h.isValidHeap());
        assertEquals(20, h.topN(100).size());
        assertTrue(h.topN(0).isEmpty());
    }

    @Test
    @DisplayName("Operation counters count insertions, extractions, comparisons and swaps")
    void counters() {
        IncidentMaxHeap h = new IncidentMaxHeap();
        h.insert(e(1, 1, 1));
        h.insert(e(2, 2, 2)); // swaps with root
        HeapStats s = h.stats();
        assertEquals(2, s.insertions());
        assertEquals(1, s.swaps());
        assertTrue(s.comparisons() >= 1);
        h.extractMax();
        assertEquals(1, h.stats().extractions());
        h.resetStats();
        assertEquals(new HeapStats(0, 0, 0, 0), h.stats());
    }

    @Test
    @DisplayName("Randomized (seed 12345): heap order and operations match a sort-based oracle")
    void randomizedVsSort() {
        Random r = new Random(12345);
        IncidentMaxHeap h = new IncidentMaxHeap();
        List<HeapEntry> model = new ArrayList<>();
        long nextId = 0;
        for (int step = 0; step < 3000; step++) {
            int op = r.nextInt(10);
            if (op < 5 || model.isEmpty()) {
                HeapEntry x = e(nextId++, r.nextInt(30), r.nextInt(20));
                h.insert(x);
                model.add(x);
            } else if (op < 7) {
                HeapEntry x = model.get(r.nextInt(model.size()));
                double np = r.nextInt(30);
                h.updatePriority(x.id(), np);
                model.set(model.indexOf(x), e(x.id(), np, x.reportedAt()));
            } else if (op < 8) {
                HeapEntry x = model.remove(r.nextInt(model.size()));
                assertEquals(x, h.remove(x.id()));
            } else {
                HeapEntry best = oracleBest(model);
                assertEquals(best, h.extractMax());
                model.remove(best);
            }
            if (step % 250 == 0) {
                assertTrue(h.isValidHeap());
                assertEquals(model.size(), h.size());
            }
        }
        List<HeapEntry> sorted = new ArrayList<>(model);
        sorted.sort((a, b) -> {
            if (a.priority() != b.priority()) return Double.compare(b.priority(), a.priority());
            if (a.reportedAt() != b.reportedAt()) return Long.compare(a.reportedAt(), b.reportedAt());
            return Long.compare(a.id(), b.id());
        });
        assertEquals(sorted, h.topN(Integer.MAX_VALUE));
    }

    private static HeapEntry oracleBest(List<HeapEntry> m) {
        HeapEntry best = m.get(0);
        for (HeapEntry x : m) {
            if (x.priority() > best.priority()
                    || (x.priority() == best.priority() && x.reportedAt() < best.reportedAt())
                    || (x.priority() == best.priority() && x.reportedAt() == best.reportedAt() && x.id() < best.id())) {
                best = x;
            }
        }
        return best;
    }
}
