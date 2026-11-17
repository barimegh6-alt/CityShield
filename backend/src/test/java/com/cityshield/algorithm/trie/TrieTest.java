package com.cityshield.algorithm.trie;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TrieTest {

    private static Trie sample() {
        Trie t = new Trie();
        t.insert("Koramangala", "ZONE", 1);
        t.insert("Kormangal Hospital", "HOSPITAL", 2);
        t.insert("Kodihalli", "ZONE", 3);
        t.insert("Fire", "CATEGORY", 4);
        t.insert("Fire Station No. 5", "CENTRE", 5);
        return t;
    }

    private static List<String> texts(TrieSearchResult r) {
        return r.suggestions().stream().map(TrieEntry::text).toList();
    }

    @Test
    @DisplayName("Prefix search is case-insensitive and alphabetical")
    void prefixCaseInsensitive() {
        TrieSearchResult r = sample().search("KO", 10);
        assertEquals(List.of("Kodihalli", "Koramangala", "Kormangal Hospital"), texts(r));
        assertTrue(r.nodesVisited() > 0);
    }

    @Test
    @DisplayName("contains is case-insensitive and exact (not prefix)")
    void containsExact() {
        Trie t = sample();
        assertTrue(t.contains("koramangala"));
        assertTrue(t.contains("FIRE"));
        assertFalse(t.contains("Fir"));
        assertFalse(t.contains("Nothing"));
        assertFalse(t.contains(""));
        assertFalse(t.contains(null));
    }

    @Test
    @DisplayName("Spaces and punctuation are ordinary characters")
    void spacesAndPunctuation() {
        Trie t = sample();
        assertEquals(List.of("Fire Station No. 5"), texts(t.search("fire s", 5)));
        assertEquals(List.of("Fire Station No. 5"), texts(t.search("fire station no.", 5)));
        assertTrue(t.contains("fire station no. 5"));
        // shorter word first, then longer
        assertEquals(List.of("Fire", "Fire Station No. 5"), texts(t.search("fire", 5)));
    }

    @Test
    @DisplayName("Limit and type filter are honoured")
    void limitAndFilter() {
        Trie t = sample();
        assertEquals(1, t.search("k", 1).suggestions().size());
        assertEquals(List.of("Kodihalli", "Koramangala"), texts(t.search("k", 10, Set.of("zone"))));
        assertEquals(List.of("Fire Station No. 5"), texts(t.search("f", 10, Set.of("CENTRE"))));
        assertTrue(t.search("k", 10, Set.of("CATEGORY")).suggestions().isEmpty());
    }

    @Test
    @DisplayName("Invalid input: blank/null prefix and non-positive limit throw")
    void invalid() {
        Trie t = sample();
        assertThrows(IllegalArgumentException.class, () -> t.search("", 5));
        assertThrows(IllegalArgumentException.class, () -> t.search("   ", 5));
        assertThrows(IllegalArgumentException.class, () -> t.search(null, 5));
        assertThrows(IllegalArgumentException.class, () -> t.search("k", 0));
        assertThrows(IllegalArgumentException.class, () -> t.insert(" ", "ZONE", 1));
        assertThrows(IllegalArgumentException.class, () -> t.insert("a", "", 1));
        assertThrows(IllegalArgumentException.class, () -> t.insert((TrieEntry) null));
    }

    @Test
    @DisplayName("Duplicates: exact duplicate ignored; same text with other type/ref kept and ordered")
    void duplicates() {
        Trie t = new Trie();
        assertTrue(t.insert("Park", "ZONE", 2));
        assertFalse(t.insert("PARK", "ZONE", 2));
        assertTrue(t.insert("Park", "CENTRE", 9));
        assertTrue(t.insert("Park", "ZONE", 1));
        assertEquals(3, t.size());
        List<TrieEntry> s = t.search("par", 10).suggestions();
        assertEquals("CENTRE", s.get(0).type());
        assertEquals(1, s.get(1).refId());
        assertEquals(2, s.get(2).refId());
    }

    @Test
    @DisplayName("Unmatched prefix returns empty list with a small nodesVisited")
    void noMatch() {
        Trie t = sample();
        TrieSearchResult r = t.search("zzz", 5);
        assertTrue(r.suggestions().isEmpty());
        assertTrue(r.nodesVisited() >= 1);
        assertEquals(r.nodesVisited(), t.getLastNodesVisited());
    }

    @Test
    @DisplayName("Randomized (seed 5): trie prefix search equals linear startsWith filter + sort")
    void randomizedVsLinear() {
        Random rnd = new Random(5);
        String alphabet = "abcAB .-1";
        Trie t = new Trie();
        List<TrieEntry> all = new ArrayList<>();
        for (int i = 0; i < 400; i++) {
            StringBuilder sb = new StringBuilder();
            int len = 1 + rnd.nextInt(6);
            for (int k = 0; k < len; k++) sb.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
            String text = sb.toString();
            if (text.isBlank()) continue;
            String type = rnd.nextBoolean() ? "ZONE" : "CATEGORY";
            TrieEntry e = new TrieEntry(text, type, i);
            if (t.insert(e)) all.add(e);
        }
        for (int q = 0; q < 200; q++) {
            StringBuilder sb = new StringBuilder();
            int len = 1 + rnd.nextInt(3);
            for (int k = 0; k < len; k++) sb.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
            String prefix = sb.toString();
            if (prefix.isBlank()) continue;
            String lp = prefix.toLowerCase();
            List<TrieEntry> expected = new ArrayList<>();
            for (TrieEntry e : all) if (e.text().toLowerCase().startsWith(lp)) expected.add(e);
            expected.sort((a, b) -> {
                int c = a.text().toLowerCase().compareTo(b.text().toLowerCase());
                if (c != 0) return c;
                c = a.type().compareTo(b.type());
                return c != 0 ? c : Long.compare(a.refId(), b.refId());
            });
            int limit = 1 + rnd.nextInt(15);
            List<TrieEntry> exp = expected.subList(0, Math.min(limit, expected.size()));
            assertEquals(exp, t.search(prefix, limit).suggestions(), "prefix '" + prefix + "'");
        }
    }
}
