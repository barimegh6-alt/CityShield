package com.cityshield.algorithm.segmenttree;

/**
 * Recursive segment tree over a {@code long[]} supporting range SUM queries and point updates.
 *
 * <p>Build O(n); query O(log n); point set / add O(log n); space O(n) (4n node array).
 * Overflow and negative values are the caller's responsibility: the tree never rejects a
 * negative result. The number of nodes visited by the latest operation is exposed through
 * {@link #getLastNodesVisited()}. Not thread-safe.</p>
 */
public final class SegmentTree {

    private final int n;
    private final long[] tree;
    private int lastNodesVisited;

    /**
     * Builds the tree from a copy of the given values.
     *
     * @param values initial values, not null and not empty
     * @throws IllegalArgumentException if values is null or empty
     */
    public SegmentTree(long[] values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("values must not be null or empty");
        }
        this.n = values.length;
        this.tree = new long[4 * n];
        build(1, 0, n - 1, values);
    }

    private void build(int node, int lo, int hi, long[] v) {
        if (lo == hi) {
            tree[node] = v[lo];
            return;
        }
        int mid = (lo + hi) >>> 1;
        build(2 * node, lo, mid, v);
        build(2 * node + 1, mid + 1, hi, v);
        tree[node] = tree[2 * node] + tree[2 * node + 1];
    }

    /**
     * Sum of elements in the inclusive range [from, to].
     *
     * @param from first index
     * @param to   last index
     * @return the sum
     * @throws IllegalArgumentException if from &gt; to or an index is outside [0, size)
     */
    public long sum(int from, int to) {
        validateRange(from, to);
        lastNodesVisited = 0;
        return query(1, 0, n - 1, from, to);
    }

    private long query(int node, int lo, int hi, int from, int to) {
        lastNodesVisited++;
        if (from <= lo && hi <= to) {
            return tree[node];
        }
        int mid = (lo + hi) >>> 1;
        long result = 0;
        if (from <= mid) {
            result += query(2 * node, lo, mid, from, to);
        }
        if (to > mid) {
            result += query(2 * node + 1, mid + 1, hi, from, to);
        }
        return result;
    }

    /**
     * Sets element {@code index} to {@code value}.
     *
     * @param index position
     * @param value new value
     * @throws IllegalArgumentException if index is out of range
     */
    public void set(int index, long value) {
        validateIndex(index);
        lastNodesVisited = 0;
        update(1, 0, n - 1, index, value, false);
    }

    /**
     * Adds {@code delta} to element {@code index}. A negative resulting value is NOT rejected.
     *
     * @param index position
     * @param delta amount to add (may be negative)
     * @throws IllegalArgumentException if index is out of range
     */
    public void add(int index, long delta) {
        validateIndex(index);
        lastNodesVisited = 0;
        update(1, 0, n - 1, index, delta, true);
    }

    private void update(int node, int lo, int hi, int index, long value, boolean delta) {
        lastNodesVisited++;
        if (lo == hi) {
            tree[node] = delta ? tree[node] + value : value;
            return;
        }
        int mid = (lo + hi) >>> 1;
        if (index <= mid) {
            update(2 * node, lo, mid, index, value, delta);
        } else {
            update(2 * node + 1, mid + 1, hi, index, value, delta);
        }
        tree[node] = tree[2 * node] + tree[2 * node + 1];
    }

    /**
     * Reads a single element. O(log n); also updates lastNodesVisited.
     *
     * @param index position
     * @return current value
     */
    public long get(int index) {
        validateIndex(index);
        return sum(index, index);
    }

    /**
     * Sum of all elements. O(1); does not touch lastNodesVisited.
     *
     * @return total
     */
    public long total() {
        return tree[1];
    }

    /**
     * Number of elements.
     *
     * @return size
     */
    public int size() {
        return n;
    }

    /**
     * Nodes visited by the most recent sum/get/set/add call (0 before any call).
     *
     * @return node visit count
     */
    public int getLastNodesVisited() {
        return lastNodesVisited;
    }

    private void validateIndex(int index) {
        if (index < 0 || index >= n) {
            throw new IllegalArgumentException("index " + index + " out of range [0, " + (n - 1) + "]");
        }
    }

    private void validateRange(int from, int to) {
        if (from > to) {
            throw new IllegalArgumentException("invalid range: from (" + from + ") > to (" + to + ")");
        }
        if (from < 0 || to >= n) {
            throw new IllegalArgumentException("range [" + from + ", " + to + "] out of bounds [0, " + (n - 1) + "]");
        }
    }
}
