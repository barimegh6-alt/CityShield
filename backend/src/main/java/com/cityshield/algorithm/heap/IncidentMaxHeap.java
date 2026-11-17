package com.cityshield.algorithm.heap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Custom array-based binary MAX-heap with an index map (id -&gt; array position), so that
 * priority changes and removal of arbitrary ids run in O(log n) instead of O(n).
 *
 * <p>Does not use {@code java.util.PriorityQueue}. Order: higher priority first, ties broken by
 * earlier {@code reportedAt}, then lower id (see {@link HeapEntry}). The heap is not thread-safe;
 * callers must synchronise externally.</p>
 *
 * <p>Complexities (n = size): insert O(log n); extractMax O(log n); peek O(1);
 * updatePriority O(log n); remove O(log n); buildHeap O(n) (Floyd bottom-up);
 * topN(k) O(n + k log n) time and O(n) extra space. Space of the heap itself: O(n).</p>
 */
public final class IncidentMaxHeap {

    private static final int INITIAL_CAPACITY = 16;

    private HeapEntry[] data = new HeapEntry[INITIAL_CAPACITY];
    private int size;
    private final Map<Long, Integer> index = new HashMap<>();

    private long insertions;
    private long extractions;
    private long comparisons;
    private long swaps;

    /** Creates an empty heap. */
    public IncidentMaxHeap() {
    }

    /**
     * Inserts an entry. Time O(log n) (amortised, array growth), space O(1).
     *
     * @param entry entry to add, not null, id must not already be present
     * @throws IllegalArgumentException if entry is null or its id is already in the heap
     */
    public void insert(HeapEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("entry must not be null");
        }
        if (index.containsKey(entry.id())) {
            throw new IllegalArgumentException("id " + entry.id() + " is already in the heap");
        }
        ensureCapacity(size + 1);
        data[size] = entry;
        index.put(entry.id(), size);
        size++;
        insertions++;
        siftUp(size - 1);
    }

    /**
     * Returns the highest-priority entry without removing it. Time O(1).
     *
     * @return the top entry, or {@code null} if the heap is empty
     */
    public HeapEntry peek() {
        return size == 0 ? null : data[0];
    }

    /**
     * Removes and returns the highest-priority entry. Time O(log n).
     *
     * @return the removed top entry
     * @throws NoSuchElementException if the heap is empty
     */
    public HeapEntry extractMax() {
        if (size == 0) {
            throw new NoSuchElementException("heap is empty");
        }
        HeapEntry top = data[0];
        removeAt(0);
        extractions++;
        return top;
    }

    /**
     * Removes the entry with the given id from anywhere in the heap. Time O(log n).
     *
     * @param id identifier to remove
     * @return the removed entry, or {@code null} if the id was not present
     */
    public HeapEntry remove(long id) {
        Integer pos = index.get(id);
        if (pos == null) {
            return null;
        }
        HeapEntry removed = data[pos];
        removeAt(pos);
        return removed;
    }

    /**
     * Changes the priority of an existing entry (keeping its reportedAt) and restores the heap
     * property by sifting up or down. Time O(log n).
     *
     * @param id          identifier of the entry
     * @param newPriority new finite priority
     * @return {@code true} if the id was present and updated, {@code false} otherwise
     * @throws IllegalArgumentException if newPriority is NaN or infinite
     */
    public boolean updatePriority(long id, double newPriority) {
        Integer pos = index.get(id);
        if (pos == null) {
            return false;
        }
        HeapEntry old = data[pos];
        data[pos] = new HeapEntry(id, newPriority, old.reportedAt());
        siftUp(pos);
        siftDown(index.get(id));
        return true;
    }

    /**
     * Replaces the heap content by the given entries using bottom-up heapify (Floyd).
     * Time O(n), space O(n). Counters are NOT reset and insertions are not incremented;
     * call {@link #resetStats()} if a clean measurement is wanted.
     *
     * @param entries entries to load (not null, no null elements, ids unique)
     * @throws IllegalArgumentException on null input, null element or duplicate id
     */
    public void buildHeap(List<HeapEntry> entries) {
        if (entries == null) {
            throw new IllegalArgumentException("entries must not be null");
        }
        HeapEntry[] fresh = new HeapEntry[Math.max(INITIAL_CAPACITY, entries.size())];
        Map<Long, Integer> freshIndex = new HashMap<>();
        int n = 0;
        for (HeapEntry e : entries) {
            if (e == null) {
                throw new IllegalArgumentException("entries must not contain null");
            }
            if (freshIndex.put(e.id(), n) != null) {
                throw new IllegalArgumentException("duplicate id " + e.id());
            }
            fresh[n++] = e;
        }
        data = fresh;
        size = n;
        index.clear();
        index.putAll(freshIndex);
        for (int i = size / 2 - 1; i >= 0; i--) {
            siftDown(i);
        }
    }

    /**
     * Non-destructive ranked listing: copies the heap and extracts up to {@code limit} entries
     * from the copy. The original heap and its counters are untouched.
     * Time O(n + k log n), space O(n).
     *
     * @param limit maximum number of entries, must be &gt;= 0
     * @return entries from highest to lowest priority (ties: earlier reportedAt, lower id)
     * @throws IllegalArgumentException if limit is negative
     */
    public List<HeapEntry> topN(int limit) {
        if (limit < 0) {
            throw new IllegalArgumentException("limit must be >= 0 but was " + limit);
        }
        IncidentMaxHeap copy = new IncidentMaxHeap();
        copy.data = java.util.Arrays.copyOf(data, Math.max(INITIAL_CAPACITY, size));
        copy.size = size;
        copy.index.putAll(index);
        int take = Math.min(limit, size);
        List<HeapEntry> out = new ArrayList<>(take);
        for (int i = 0; i < take; i++) {
            out.add(copy.extractMax());
        }
        return out;
    }

    /**
     * Returns whether the id is in the heap. Time O(1).
     *
     * @param id identifier
     * @return true if present
     */
    public boolean contains(long id) {
        return index.containsKey(id);
    }

    /**
     * Returns the entry for an id. Time O(1).
     *
     * @param id identifier
     * @return the entry or {@code null} if absent
     */
    public HeapEntry get(long id) {
        Integer pos = index.get(id);
        return pos == null ? null : data[pos];
    }

    /**
     * Number of entries. Time O(1).
     *
     * @return size
     */
    public int size() {
        return size;
    }

    /**
     * Whether the heap is empty. Time O(1).
     *
     * @return true if size is 0
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /** Removes all entries. Counters are kept. Time O(n). */
    public void clear() {
        java.util.Arrays.fill(data, 0, size, null);
        size = 0;
        index.clear();
    }

    /**
     * Returns the current operation counters.
     *
     * @return immutable snapshot
     */
    public HeapStats stats() {
        return new HeapStats(insertions, extractions, comparisons, swaps);
    }

    /** Sets all operation counters to zero. */
    public void resetStats() {
        insertions = 0;
        extractions = 0;
        comparisons = 0;
        swaps = 0;
    }

    /**
     * Verifies the max-heap property and index map consistency (used by tests and diagnostics).
     * Time O(n).
     *
     * @return true if the structure is valid
     */
    public boolean isValidHeap() {
        if (index.size() != size) {
            return false;
        }
        for (int i = 0; i < size; i++) {
            if (index.get(data[i].id()) == null || index.get(data[i].id()) != i) {
                return false;
            }
            if (i > 0 && before(data[i], data[(i - 1) / 2])) {
                return false;
            }
        }
        return true;
    }

    // ---- internals -------------------------------------------------------------------------

    /** Pure ordering test (not counted): true if a must be closer to the top than b. */
    private static boolean before(HeapEntry a, HeapEntry b) {
        if (a.priority() != b.priority()) {
            return a.priority() > b.priority();
        }
        if (a.reportedAt() != b.reportedAt()) {
            return a.reportedAt() < b.reportedAt();
        }
        return a.id() < b.id();
    }

    private boolean countedBefore(HeapEntry a, HeapEntry b) {
        comparisons++;
        return before(a, b);
    }

    private void removeAt(int pos) {
        HeapEntry removed = data[pos];
        index.remove(removed.id());
        size--;
        if (pos == size) {
            data[size] = null;
            return;
        }
        data[pos] = data[size];
        data[size] = null;
        index.put(data[pos].id(), pos);
        siftUp(pos);
        siftDown(index.get(data[pos].id()));
    }

    private void siftUp(int i) {
        while (i > 0) {
            int parent = (i - 1) / 2;
            if (countedBefore(data[i], data[parent])) {
                swap(i, parent);
                i = parent;
            } else {
                break;
            }
        }
    }

    private void siftDown(int i) {
        while (true) {
            int left = 2 * i + 1;
            int right = left + 1;
            int best = i;
            if (left < size && countedBefore(data[left], data[best])) {
                best = left;
            }
            if (right < size && countedBefore(data[right], data[best])) {
                best = right;
            }
            if (best == i) {
                return;
            }
            swap(i, best);
            i = best;
        }
    }

    private void swap(int i, int j) {
        HeapEntry tmp = data[i];
        data[i] = data[j];
        data[j] = tmp;
        index.put(data[i].id(), i);
        index.put(data[j].id(), j);
        swaps++;
    }

    private void ensureCapacity(int needed) {
        if (needed > data.length) {
            data = java.util.Arrays.copyOf(data, Math.max(needed, data.length * 2));
        }
    }
}
