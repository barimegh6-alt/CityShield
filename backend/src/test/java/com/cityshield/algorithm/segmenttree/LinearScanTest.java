package com.cityshield.algorithm.segmenttree;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LinearScanTest {

    @Test
    @DisplayName("LinearScan sums an inclusive range and reports elements scanned")
    void sums() {
        LinearScan.Result r = LinearScan.sum(new long[]{1, 2, 3, 4, 5}, 1, 3);
        assertEquals(9, r.sum());
        assertEquals(3, r.elementsScanned());
    }

    @Test
    @DisplayName("LinearScan validates null/empty arrays and bad ranges")
    void validation() {
        assertThrows(IllegalArgumentException.class, () -> LinearScan.sum(null, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> LinearScan.sum(new long[0], 0, 0));
        assertThrows(IllegalArgumentException.class, () -> LinearScan.sum(new long[]{1, 2}, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> LinearScan.sum(new long[]{1, 2}, 0, 2));
        assertThrows(IllegalArgumentException.class, () -> LinearScan.sum(new long[]{1, 2}, -1, 1));
    }
}
