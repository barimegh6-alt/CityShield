package com.cityshield.algorithm.setcover;

import java.util.Arrays;

/**
 * A candidate set in a set-cover instance: an emergency centre and the zones it covers.
 *
 * @param id       caller-defined identifier (e.g. centre id)
 * @param cost     non-negative cost of selecting the set
 * @param elements element indices in {@code [0, universeSize)} covered by the set
 */
public record CoverSet(long id, double cost, int[] elements) {
    public CoverSet {
        if (cost < 0 || Double.isNaN(cost)) {
            throw new IllegalArgumentException("cost must be >= 0 but was " + cost);
        }
        if (elements == null) {
            throw new IllegalArgumentException("elements must not be null");
        }
        elements = Arrays.copyOf(elements, elements.length);
    }

    @Override
    public int[] elements() {
        return Arrays.copyOf(elements, elements.length);
    }
}
