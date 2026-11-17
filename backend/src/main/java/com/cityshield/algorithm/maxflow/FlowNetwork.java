package com.cityshield.algorithm.maxflow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Mutable builder for a directed flow network with {@code long} capacities.
 *
 * <p>Nodes are dense integer indices {@code 0..nodeCount-1} created by {@link #addNode(String)}.
 * Edges are identified by their insertion index ("forward edge id"). Callers create a super-source
 * or super-sink simply by adding an extra node and connecting it with ordinary edges.
 *
 * <p>Space: O(V + E). {@code addNode}/{@code addEdge} are O(1) amortised.
 */
public final class FlowNetwork {

    private final List<String> labels = new ArrayList<>();
    private final List<Integer> from = new ArrayList<>();
    private final List<Integer> to = new ArrayList<>();
    private final List<Long> capacity = new ArrayList<>();

    /**
     * Adds a node.
     *
     * @param label human readable label (may be {@code null}, then the index is used)
     * @return the new node index
     */
    public int addNode(String label) {
        labels.add(label == null ? String.valueOf(labels.size()) : label);
        return labels.size() - 1;
    }

    /**
     * Adds a directed edge. Parallel edges and zero capacities are allowed.
     * Time O(1).
     *
     * @param u        tail node
     * @param v        head node
     * @param capacity non-negative capacity
     * @return the forward edge id (0-based insertion order)
     * @throws IllegalArgumentException for an unknown node, a negative capacity or a self-loop
     */
    public int addEdge(int u, int v, long capacity) {
        requireNode(u);
        requireNode(v);
        if (capacity < 0) {
            throw new IllegalArgumentException("Negative capacity: " + capacity);
        }
        if (u == v) {
            throw new IllegalArgumentException("Self-loop on node " + u);
        }
        from.add(u);
        to.add(v);
        this.capacity.add(capacity);
        return from.size() - 1;
    }

    /** @return number of nodes. */
    public int nodeCount() {
        return labels.size();
    }

    /** @return number of forward edges added so far. */
    public int edgeCount() {
        return from.size();
    }

    /** @return label of node {@code n}. */
    public String label(int n) {
        requireNode(n);
        return labels.get(n);
    }

    /** @return tail of forward edge {@code e}. */
    public int edgeFrom(int e) {
        requireEdge(e);
        return from.get(e);
    }

    /** @return head of forward edge {@code e}. */
    public int edgeTo(int e) {
        requireEdge(e);
        return to.get(e);
    }

    /** @return capacity of forward edge {@code e}. */
    public long edgeCapacity(int e) {
        requireEdge(e);
        return capacity.get(e);
    }

    /** @return immutable copy of all labels, indexed by node. */
    public List<String> labels() {
        return Collections.unmodifiableList(new ArrayList<>(labels));
    }

    boolean hasNode(int n) {
        return n >= 0 && n < labels.size();
    }

    private void requireNode(int n) {
        if (!hasNode(n)) {
            throw new IllegalArgumentException("Unknown node: " + n);
        }
    }

    private void requireEdge(int e) {
        if (e < 0 || e >= from.size()) {
            throw new IllegalArgumentException("Unknown edge: " + e);
        }
    }
}
