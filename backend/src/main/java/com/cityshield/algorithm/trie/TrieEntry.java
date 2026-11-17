package com.cityshield.algorithm.trie;

/**
 * A searchable entry stored in the {@link Trie}.
 *
 * @param text  display text with original casing
 * @param type  entry type, e.g. ZONE, HOSPITAL, CENTRE, CATEGORY
 * @param refId identifier of the referenced domain object
 */
public record TrieEntry(String text, String type, long refId) {
    /** Validates that text and type are non-blank. */
    public TrieEntry {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text must not be blank");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type must not be blank");
        }
    }
}
