package com.cityshield.algorithm.kmp;

import java.util.ArrayList;
import java.util.List;

/**
 * Knuth-Morris-Pratt string matcher.
 *
 * <p>{@link #buildLps} runs in O(m) time / O(m) space; {@link #search} in O(n + m) time
 * (never more than 2n character comparisons) and O(m) extra space for the LPS table,
 * and finds ALL overlapping matches. Case-insensitive mode compares characters folded with
 * {@link Character#toLowerCase(char)}; reported positions always refer to the original text.</p>
 */
public final class KmpMatcher {

    private KmpMatcher() {
    }

    /**
     * Builds the longest-proper-prefix-which-is-also-suffix table (case-sensitive).
     *
     * @param pattern non-empty pattern
     * @return array lps where lps[i] is the LPS length of pattern[0..i]
     * @throws IllegalArgumentException if pattern is null or empty
     */
    public static int[] buildLps(String pattern) {
        return buildLps(pattern, false);
    }

    /**
     * Builds the LPS table.
     *
     * @param pattern    non-empty pattern
     * @param ignoreCase whether characters are compared ignoring case
     * @return the LPS array (length = pattern length)
     * @throws IllegalArgumentException if pattern is null or empty
     */
    public static int[] buildLps(String pattern, boolean ignoreCase) {
        requirePattern(pattern);
        int m = pattern.length();
        int[] lps = new int[m];
        int len = 0;
        int i = 1;
        while (i < m) {
            if (same(pattern.charAt(i), pattern.charAt(len), ignoreCase)) {
                lps[i++] = ++len;
            } else if (len > 0) {
                len = lps[len - 1];
            } else {
                lps[i++] = 0;
            }
        }
        return lps;
    }

    /**
     * Case-sensitive search for all occurrences.
     *
     * @param text    text to search (not null)
     * @param pattern non-empty pattern
     * @return all start positions and the comparison count
     * @throws IllegalArgumentException if text is null or pattern is null/empty
     */
    public static MatchResult search(String text, String pattern) {
        return search(text, pattern, false);
    }

    /**
     * Searches for all (overlapping) occurrences of pattern in text.
     * If the pattern is longer than the text the result is empty with 0 comparisons.
     *
     * @param text       text to search (not null; may be empty)
     * @param pattern    non-empty pattern
     * @param ignoreCase true for case-insensitive matching
     * @return all start positions in ascending order and the comparison count
     * @throws IllegalArgumentException if text is null or pattern is null/empty
     */
    public static MatchResult search(String text, String pattern, boolean ignoreCase) {
        if (text == null) {
            throw new IllegalArgumentException("text must not be null");
        }
        requirePattern(pattern);
        int n = text.length();
        int m = pattern.length();
        List<Integer> positions = new ArrayList<>();
        if (m > n) {
            return new MatchResult(positions, 0);
        }
        int[] lps = buildLps(pattern, ignoreCase);
        long comparisons = 0;
        int i = 0;
        int j = 0;
        while (i < n) {
            comparisons++;
            if (same(text.charAt(i), pattern.charAt(j), ignoreCase)) {
                i++;
                j++;
                if (j == m) {
                    positions.add(i - m);
                    j = lps[j - 1];
                }
            } else if (j > 0) {
                j = lps[j - 1];
            } else {
                i++;
            }
        }
        return new MatchResult(positions, comparisons);
    }

    private static void requirePattern(String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            throw new IllegalArgumentException("pattern must not be null or empty");
        }
    }

    static boolean same(char a, char b, boolean ignoreCase) {
        return a == b || (ignoreCase && Character.toLowerCase(a) == Character.toLowerCase(b));
    }
}
