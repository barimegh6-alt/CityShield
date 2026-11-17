package com.cityshield.algorithm.dsu;

import java.util.ArrayList;
import java.util.List;

/**
 * Disjoint Set Union (union-find) over elements {@code 0..n-1} using parent and rank arrays,
 * iterative find with path compression and union by rank.
 *
 * <p>Amortised time per operation O(alpha(n)) (inverse Ackermann, practically constant);
 * space O(n). Not thread-safe. Counters: {@link #getFindCount()} counts every call of
 * {@link #find(int)} including the two internal finds made by {@link #union(int, int)} and
 * {@link #connected(int, int)}; {@link #getUnionCount()} counts successful merges only.</p>
 */
public final class DisjointSetUnion {

    private final int[] parent;
    private final int[] rank;
    private final int[] size;
    private int componentCount;
    private long unionCount;
    private long unionAttempts;
    private long findCount;

    /**
     * Creates n singleton sets.
     *
     * @param n number of elements, must be &gt;= 0
     * @throws IllegalArgumentException if n is negative
     */
    public DisjointSetUnion(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("element count must be >= 0 but was " + n);
        }
        parent = new int[n];
        rank = new int[n];
        size = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
            size[i] = 1;
        }
        componentCount = n;
    }

    /**
     * Finds the representative of x with iterative path compression. Amortised O(alpha(n)).
     *
     * @param x element in [0, n)
     * @return the root of x's set
     * @throws IllegalArgumentException if x is out of range
     */
    public int find(int x) {
        check(x);
        findCount++;
        int root = x;
        while (parent[root] != root) {
            root = parent[root];
        }
        while (parent[x] != root) {
            int next = parent[x];
            parent[x] = root;
            x = next;
        }
        return root;
    }

    /**
     * Merges the sets of a and b using union by rank.
     *
     * @param a first element
     * @param b second element
     * @return true if two different sets were merged, false if already connected
     * @throws IllegalArgumentException if an element is out of range
     */
    public boolean union(int a, int b) {
        unionAttempts++;
        int ra = find(a);
        int rb = find(b);
        if (ra == rb) {
            return false;
        }
        if (rank[ra] < rank[rb]) {
            int t = ra;
            ra = rb;
            rb = t;
        }
        parent[rb] = ra;
        size[ra] += size[rb];
        if (rank[ra] == rank[rb]) {
            rank[ra]++;
        }
        componentCount--;
        unionCount++;
        return true;
    }

    /**
     * Tests whether a and b are in the same set.
     *
     * @param a first element
     * @param b second element
     * @return true if connected
     * @throws IllegalArgumentException if an element is out of range
     */
    public boolean connected(int a, int b) {
        return find(a) == find(b);
    }

    /**
     * Component id of x, i.e. its current root. Ids are stable only until the next union.
     *
     * @param x element
     * @return root element identifying the component
     */
    public int componentId(int x) {
        return find(x);
    }

    /**
     * Size of the set containing x.
     *
     * @param x element
     * @return number of elements in x's set
     */
    public int sizeOf(int x) {
        return size[find(x)];
    }

    /**
     * Number of elements.
     *
     * @return n
     */
    public int elementCount() {
        return parent.length;
    }

    /**
     * Number of disjoint sets. O(1).
     *
     * @return current component count
     */
    public int getComponentCount() {
        return componentCount;
    }

    /**
     * Sizes of all components, in ascending order of their smallest member. O(n alpha(n)).
     *
     * @return list of component sizes
     */
    public List<Integer> componentSizes() {
        int n = parent.length;
        int[] rootToIndex = new int[n];
        java.util.Arrays.fill(rootToIndex, -1);
        List<Integer> sizes = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int r = find(i);
            if (rootToIndex[r] == -1) {
                rootToIndex[r] = sizes.size();
                sizes.add(size[r]);
            }
        }
        return sizes;
    }

    /** @return number of successful merges */
    public long getUnionCount() { return unionCount; }

    /** @return number of union calls including no-op ones */
    public long getUnionAttempts() { return unionAttempts; }

    /** @return number of find calls (including internal ones) */
    public long getFindCount() { return findCount; }

    private void check(int x) {
        if (x < 0 || x >= parent.length) {
            throw new IllegalArgumentException("element " + x + " out of range [0, " + parent.length + ")");
        }
    }
}
