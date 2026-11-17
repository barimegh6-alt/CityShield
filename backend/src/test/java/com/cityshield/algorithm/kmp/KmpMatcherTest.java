package com.cityshield.algorithm.kmp;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class KmpMatcherTest {

    @Test
    @DisplayName("LPS table of classic patterns is correct")
    void lps() {
        assertArrayEquals(new int[]{0, 0, 1, 2, 0, 1, 2, 3, 4, 3, 4}, KmpMatcher.buildLps("ABABCABABAB"));
        assertArrayEquals(new int[]{0, 1, 0, 1, 2, 0, 1, 2, 3}, KmpMatcher.buildLps("AABAACAAB"));
        assertArrayEquals(new int[]{0, 1, 2, 3}, KmpMatcher.buildLps("aaaa"));
        assertArrayEquals(new int[]{0}, KmpMatcher.buildLps("x"));
        assertArrayEquals(new int[]{0, 0, 0}, KmpMatcher.buildLps("abc"));
    }

    @Test
    @DisplayName("Finds all overlapping matches")
    void overlapping() {
        assertEquals(List.of(0, 1, 2, 3), KmpMatcher.search("aaaaaa", "aaa").positions());
        assertEquals(List.of(0, 2, 4), KmpMatcher.search("ababab", "ab").positions());
        assertEquals(List.of(0, 2), KmpMatcher.search("ababa", "aba").positions());
    }

    @Test
    @DisplayName("Empty/null pattern and null text throw IllegalArgumentException")
    void invalid() {
        assertThrows(IllegalArgumentException.class, () -> KmpMatcher.search("abc", ""));
        assertThrows(IllegalArgumentException.class, () -> KmpMatcher.search("abc", null));
        assertThrows(IllegalArgumentException.class, () -> KmpMatcher.search(null, "a"));
        assertThrows(IllegalArgumentException.class, () -> KmpMatcher.buildLps(""));
        assertThrows(IllegalArgumentException.class, () -> NaiveMatcher.search("abc", ""));
    }

    @Test
    @DisplayName("Pattern longer than text, or empty text, gives empty result")
    void longerPattern() {
        MatchResult r = KmpMatcher.search("ab", "abc");
        assertTrue(r.positions().isEmpty());
        assertEquals(0, r.comparisons());
        assertTrue(KmpMatcher.search("", "a").positions().isEmpty());
        assertTrue(NaiveMatcher.search("ab", "abc").positions().isEmpty());
    }

    @Test
    @DisplayName("Case-insensitive option matches mixed case and keeps original positions")
    void caseInsensitive() {
        String text = "Flood in Zone A. flood warning, FLOOD gates";
        assertEquals(List.of(0, 17, 32), KmpMatcher.search(text, "flood", true).positions());
        assertEquals(List.of(17), KmpMatcher.search(text, "flood", false).positions());
        assertEquals(3, KmpMatcher.search(text, "flood", true).count());
    }

    @Test
    @DisplayName("Single-character text and pattern, match and mismatch")
    void singleChar() {
        assertEquals(List.of(0), KmpMatcher.search("a", "a").positions());
        assertTrue(KmpMatcher.search("a", "b").positions().isEmpty());
        assertEquals(1, KmpMatcher.search("a", "a").comparisons());
    }

    @Test
    @DisplayName("KMP uses far fewer comparisons than naive on a worst-case input")
    void fewerComparisonsThanNaive() {
        String text = "a".repeat(2000) + "b";
        String pattern = "a".repeat(50) + "b";
        MatchResult k = KmpMatcher.search(text, pattern);
        MatchResult n = NaiveMatcher.search(text, pattern);
        assertEquals(n.positions(), k.positions());
        assertTrue(k.comparisons() <= 2L * text.length());
        assertTrue(k.comparisons() * 5 < n.comparisons());
    }

    @Test
    @DisplayName("Punctuation and spaces in text/pattern are matched literally")
    void punctuation() {
        assertEquals(List.of(5), KmpMatcher.search("gas. leak (urgent)", "leak (u").positions());
        assertEquals(List.of(3), KmpMatcher.search("no a.b", "a.b").positions());
    }

    @Test
    @DisplayName("Randomized (seed 31): KMP positions equal naive positions, both modes")
    void randomizedVsNaive() {
        Random r = new Random(31);
        String alpha = "abAB";
        for (int trial = 0; trial < 3000; trial++) {
            String text = random(r, alpha, r.nextInt(40));
            String pat = random(r, alpha, 1 + r.nextInt(5));
            boolean ic = r.nextBoolean();
            MatchResult k = KmpMatcher.search(text, pat, ic);
            MatchResult n = NaiveMatcher.search(text, pat, ic);
            assertEquals(n.positions(), k.positions(), text + " / " + pat + " / " + ic);
            if (text.length() >= pat.length()) {
                assertTrue(k.comparisons() <= 2L * text.length());
            }
        }
    }

    private static String random(Random r, String alpha, int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) sb.append(alpha.charAt(r.nextInt(alpha.length())));
        return sb.toString();
    }
}
