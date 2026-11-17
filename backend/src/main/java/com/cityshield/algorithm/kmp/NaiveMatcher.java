package com.cityshield.algorithm.kmp;

import java.util.ArrayList;
import java.util.List;

/**
 * Brute-force string matcher used as a baseline and as test oracle for {@link KmpMatcher}.
 * Time O(n*m) worst case, space O(1) besides the output.
 */
public final class NaiveMatcher {

    private NaiveMatcher() {
    }

    /**
     * Case-sensitive search for all occurrences.
     *
     * @param text    text (not null)
     * @param pattern non-empty pattern
     * @return start positions and comparison count
     * @throws IllegalArgumentException if text is null or pattern is null/empty
     */
    public static MatchResult search(String text, String pattern) {
        return search(text, pattern, false);
    }

    /**
     * Tries every alignment and compares characters left to right, stopping at the first mismatch.
     * Pattern longer than text gives an empty result with 0 comparisons.
     *
     * @param text       text (not null)
     * @param pattern    non-empty pattern
     * @param ignoreCase case-insensitive comparison
     * @return start positions (ascending, overlapping allowed) and comparison count
     * @throws IllegalArgumentException if text is null or pattern is null/empty
     */
    public static MatchResult search(String text, String pattern, boolean ignoreCase) {
        if (text == null) {
            throw new IllegalArgumentException("text must not be null");
        }
        if (pattern == null || pattern.isEmpty()) {
            throw new IllegalArgumentException("pattern must not be null or empty");
        }
        int n = text.length();
        int m = pattern.length();
        List<Integer> positions = new ArrayList<>();
        long comparisons = 0;
        for (int s = 0; s + m <= n; s++) {
            int k = 0;
            while (k < m) {
                comparisons++;
                if (!KmpMatcher.same(text.charAt(s + k), pattern.charAt(k), ignoreCase)) {
                    break;
                }
                k++;
            }
            if (k == m) {
                positions.add(s);
            }
        }
        return new MatchResult(positions, comparisons);
    }
}
