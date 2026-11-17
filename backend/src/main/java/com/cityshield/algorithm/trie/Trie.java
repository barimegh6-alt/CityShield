package com.cityshield.algorithm.trie;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Case-insensitive Trie of {@link TrieEntry} values for autocomplete.
 *
 * <p>Every character (letters, digits, spaces, punctuation) is a trie edge, stored in a
 * {@code HashMap<Character, Node>}; characters are folded with {@link Character#toLowerCase(char)}.
 * Several entries may share the same text (different type or refId). Results are deterministic:
 * children are visited in ascending character order, entries with identical text are ordered by
 * type then refId.</p>
 *
 * <p>Complexity (m = prefix length, k = nodes in the visited subtree): insert O(L) for text
 * length L; contains O(L); prefix search O(m + k) (stops early once {@code limit} matches are
 * found); space O(total characters). Not thread-safe.</p>
 */
public final class Trie {

    private static final class Node {
        final Map<Character, Node> children = new HashMap<>();
        final List<TrieEntry> entries = new ArrayList<>();
    }

    private final Node root = new Node();
    private int entryCount;
    private int lastNodesVisited;

    /**
     * Inserts an entry. Exact duplicates (same text ignoring case, same type, same refId) are ignored.
     *
     * @param text  text to index, not blank
     * @param type  entry type, not blank
     * @param refId referenced id
     * @return true if the entry was added, false if it already existed
     * @throws IllegalArgumentException if text or type is blank
     */
    public boolean insert(String text, String type, long refId) {
        return insert(new TrieEntry(text, type, refId));
    }

    /**
     * Inserts an entry object. See {@link #insert(String, String, long)}.
     *
     * @param entry entry, not null
     * @return true if added, false if duplicate
     * @throws IllegalArgumentException if entry is null
     */
    public boolean insert(TrieEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("entry must not be null");
        }
        Node node = root;
        String text = entry.text();
        for (int i = 0; i < text.length(); i++) {
            char c = Character.toLowerCase(text.charAt(i));
            Node next = node.children.get(c);
            if (next == null) {
                next = new Node();
                node.children.put(c, next);
            }
            node = next;
        }
        for (TrieEntry existing : node.entries) {
            if (existing.type().equals(entry.type()) && existing.refId() == entry.refId()) {
                return false;
            }
        }
        node.entries.add(entry);
        entryCount++;
        return true;
    }

    /**
     * Case-insensitive exact-text lookup.
     *
     * @param text text to look for (null or empty returns false)
     * @return true if at least one entry has exactly this text ignoring case
     */
    public boolean contains(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        Node node = descend(text);
        return node != null && !node.entries.isEmpty();
    }

    /**
     * Prefix search without type filter. See {@link #search(String, int, Set)}.
     *
     * @param prefix non-blank prefix
     * @param limit  maximum number of suggestions, &gt;= 1
     * @return suggestions and nodes visited
     */
    public TrieSearchResult search(String prefix, int limit) {
        return search(prefix, limit, null);
    }

    /**
     * Prefix search. Spaces inside the prefix are significant (they are characters) but a
     * prefix that is null, empty or only whitespace is rejected.
     *
     * @param prefix non-blank prefix
     * @param limit  maximum number of suggestions, must be &gt;= 1
     * @param types  allowed types (compared ignoring case); null or empty means no filter
     * @return up to {@code limit} entries in alphabetical order and the nodes visited
     * @throws IllegalArgumentException if prefix is blank or limit &lt; 1
     */
    public TrieSearchResult search(String prefix, int limit, Set<String> types) {
        if (prefix == null || prefix.isBlank()) {
            throw new IllegalArgumentException("prefix must not be blank");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be >= 1 but was " + limit);
        }
        lastNodesVisited = 0;
        Set<String> filter = null;
        if (types != null && !types.isEmpty()) {
            filter = new java.util.HashSet<>();
            for (String t : types) {
                if (t != null) {
                    filter.add(t.toLowerCase(java.util.Locale.ROOT));
                }
            }
        }
        List<TrieEntry> out = new ArrayList<>();
        Node start = descend(prefix);
        if (start != null) {
            collect(start, filter, limit, out);
        }
        return new TrieSearchResult(out, lastNodesVisited);
    }

    /**
     * Total number of stored entries.
     *
     * @return entry count
     */
    public int size() {
        return entryCount;
    }

    /**
     * Nodes visited by the most recent {@code search} or {@code contains} call.
     *
     * @return node visit count
     */
    public int getLastNodesVisited() {
        return lastNodesVisited;
    }

    private Node descend(String s) {
        lastNodesVisited = 1; // root
        Node node = root;
        for (int i = 0; i < s.length(); i++) {
            node = node.children.get(Character.toLowerCase(s.charAt(i)));
            if (node == null) {
                return null;
            }
            lastNodesVisited++;
        }
        return node;
    }

    private void collect(Node node, Set<String> filter, int limit, List<TrieEntry> out) {
        if (out.size() >= limit) {
            return;
        }
        for (TrieEntry e : sortedEntries(node.entries)) {
            if (out.size() >= limit) {
                return;
            }
            if (filter == null || filter.contains(e.type().toLowerCase(java.util.Locale.ROOT))) {
                out.add(e);
            }
        }
        char[] keys = sortedKeys(node.children.keySet());
        for (char k : keys) {
            if (out.size() >= limit) {
                return;
            }
            lastNodesVisited++;
            collect(node.children.get(k), filter, limit, out);
        }
    }

    /** Insertion sort of the (small) child key set by character code. */
    private static char[] sortedKeys(Set<Character> keys) {
        char[] a = new char[keys.size()];
        int n = 0;
        for (char c : keys) {
            int j = n++;
            while (j > 0 && a[j - 1] > c) {
                a[j] = a[j - 1];
                j--;
            }
            a[j] = c;
        }
        return a;
    }

    /** Insertion sort of entries sharing the same lowercase text: by type, then refId. */
    private static List<TrieEntry> sortedEntries(List<TrieEntry> entries) {
        List<TrieEntry> sorted = new ArrayList<>(entries.size());
        for (TrieEntry e : entries) {
            int j = sorted.size();
            sorted.add(e);
            while (j > 0 && compare(sorted.get(j - 1), e) > 0) {
                sorted.set(j, sorted.get(j - 1));
                j--;
            }
            sorted.set(j, e);
        }
        return sorted;
    }

    private static int compare(TrieEntry a, TrieEntry b) {
        int c = a.type().compareTo(b.type());
        if (c != 0) {
            return c;
        }
        return Long.compare(a.refId(), b.refId());
    }
}
