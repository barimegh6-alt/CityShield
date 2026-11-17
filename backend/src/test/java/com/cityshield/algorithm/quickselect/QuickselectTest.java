package com.cityshield.algorithm.quickselect;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class QuickselectTest {
    @Test
    @DisplayName("Randomized (seed 5): k-th smallest equals sorted[k-1] on duplicate-heavy arrays")
    void matchesSort() {
        Random r = new Random(5);
        for (int t = 0; t < 400; t++) {
            int n = 1 + r.nextInt(60);
            double[] a = new double[n];
            for (int i = 0; i < n; i++) {
                a[i] = r.nextInt(6);
            }
            double[] sorted = a.clone();
            Arrays.sort(sorted);
            RandomizedQuickselect q = new RandomizedQuickselect(t);
            for (int k = 1; k <= n; k++) {
                assertEquals(sorted[k - 1], q.kthSmallest(a, k).value());
                assertEquals(sorted[n - k], q.kthLargest(a, k).value());
            }
        }
    }

    @Test
    @DisplayName("All-equal array resolves in a single partition round")
    void allEqual() {
        double[] a = new double[1000];
        Arrays.fill(a, 3.0);
        SelectionResult r = new RandomizedQuickselect(1).kthSmallest(a, 500);
        assertEquals(3.0, r.value());
        assertEquals(1, r.partitionRounds());
    }

    @Test
    @DisplayName("Sorted and reverse-sorted input still select correctly")
    void sortedInputs() {
        double[] up = new double[2000];
        double[] down = new double[2000];
        for (int i = 0; i < 2000; i++) {
            up[i] = i;
            down[i] = 1999 - i;
        }
        RandomizedQuickselect q = new RandomizedQuickselect(9);
        assertEquals(1000.0, q.kthSmallest(up, 1001).value());
        assertEquals(1000.0, q.kthSmallest(down, 1001).value());
        assertEquals(0.0, q.kthSmallest(up, 1).value());
        assertEquals(1999.0, q.kthSmallest(down, 2000).value());
    }

    @Test
    @DisplayName("Same seed reproduces identical statistics; different seeds give the same answer")
    void reproducible() {
        Random r = new Random(2);
        double[] a = new double[500];
        for (int i = 0; i < a.length; i++) {
            a[i] = r.nextInt(1000);
        }
        SelectionResult x = new RandomizedQuickselect(42).percentile(a, 90);
        SelectionResult y = new RandomizedQuickselect(42).percentile(a, 90);
        assertEquals(x, y);
        boolean differs = false;
        for (long seed = 1; seed <= 8; seed++) {
            SelectionResult z = new RandomizedQuickselect(seed).percentile(a, 90);
            assertEquals(x.value(), z.value());
            differs |= z.comparisons() != x.comparisons();
        }
        assertTrue(differs, "different seeds should do different amounts of work on some run");
    }

    @Test
    @DisplayName("Nearest-rank percentile: P50/P90/P100 on 1..10, P90 high-risk group")
    void percentiles() {
        double[] a = {10, 9, 8, 7, 6, 5, 4, 3, 2, 1};
        RandomizedQuickselect q = new RandomizedQuickselect(0);
        assertEquals(5.0, q.percentile(a, 50).value());
        assertEquals(9.0, q.percentile(a, 90).value());
        assertEquals(10.0, q.percentile(a, 100).value());
        assertEquals(1.0, q.percentile(a, 0.1).value());
        assertEquals(List.of(0, 1), q.highRiskIndices(a, 90));
        assertEquals(9, RandomizedQuickselect.nearestRank(10, 90));
    }

    @Test
    @DisplayName("Input array is not modified")
    void inputUntouched() {
        double[] a = {5, 1, 4, 2, 3};
        double[] copy = a.clone();
        new RandomizedQuickselect(1).kthSmallest(a, 3);
        assertArrayEquals(copy, a);
    }

    @Test
    @DisplayName("Invalid input: empty, null, NaN, bad k, bad percentile")
    void invalid() {
        RandomizedQuickselect q = new RandomizedQuickselect(1);
        assertThrows(IllegalArgumentException.class, () -> q.kthSmallest(new double[0], 1));
        assertThrows(IllegalArgumentException.class, () -> q.kthSmallest(null, 1));
        assertThrows(IllegalArgumentException.class, () -> q.kthSmallest(new double[]{1, Double.NaN}, 1));
        assertThrows(IllegalArgumentException.class, () -> q.kthSmallest(new double[]{1}, 0));
        assertThrows(IllegalArgumentException.class, () -> q.kthSmallest(new double[]{1}, 2));
        assertThrows(IllegalArgumentException.class, () -> q.percentile(new double[]{1, 2}, 0));
        assertThrows(IllegalArgumentException.class, () -> q.percentile(new double[]{1, 2}, 101));
        assertThrows(IllegalArgumentException.class, () -> q.percentile(new double[]{1, 2}, Double.NaN));
    }
}
