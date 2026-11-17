package com.cityshield.algorithm.kmp;

import java.util.List;

/**
 * Immutable result of a string search.
 *
 * @param positions   0-based start indices of ALL (possibly overlapping) matches, ascending
 * @param comparisons number of character-to-character comparisons performed
 */
public record MatchResult(List<Integer> positions, long comparisons) {
    /** Makes the list immutable. */
    public MatchResult {
        positions = List.copyOf(positions);
    }

    /** @return number of matches */
    public int count() {
        return positions.size();
    }
}
