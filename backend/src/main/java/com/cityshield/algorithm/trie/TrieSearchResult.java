package com.cityshield.algorithm.trie;

import java.util.List;

/**
 * Immutable result of a prefix search.
 *
 * @param suggestions  matching entries in deterministic alphabetical order
 * @param nodesVisited trie nodes touched (descent along the prefix plus subtree traversal)
 */
public record TrieSearchResult(List<TrieEntry> suggestions, int nodesVisited) {
    /** Makes the list immutable. */
    public TrieSearchResult {
        suggestions = List.copyOf(suggestions);
    }
}
