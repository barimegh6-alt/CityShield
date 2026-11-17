package com.cityshield.algorithm.dsu;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Analyses city road connectivity with {@link DisjointSetUnion}.
 *
 * <p>Only OPEN edges (not in the blocked set) are unioned. Critical roads are open edges whose
 * removal increases the component count (bridges); each is detected by rebuilding a DSU without
 * that edge. Time O(E * (V + E) * alpha) for the critical-road search, O((V + E) alpha) for the
 * rest; space O(V + E). Self-loops and parallel roads are handled (never critical).</p>
 */
public final class ConnectivityAnalyzer {

    private ConnectivityAnalyzer() {
    }

    /**
     * Runs the analysis.
     *
     * @param zoneCount  number of zones (indices 0..zoneCount-1), &gt;= 0
     * @param edges      all roads (not null, no null elements, endpoints in range)
     * @param blockedIds ids of blocked roads; null is treated as empty; unknown ids are ignored
     * @return components, isolated zones, critical roads and DSU counters
     * @throws IllegalArgumentException on negative zoneCount, null edges or out-of-range endpoint
     */
    public static ConnectivityResult analyze(int zoneCount, List<Edge> edges, Set<Long> blockedIds) {
        if (zoneCount < 0) {
            throw new IllegalArgumentException("zoneCount must be >= 0 but was " + zoneCount);
        }
        if (edges == null) {
            throw new IllegalArgumentException("edges must not be null");
        }
        List<Edge> open = new ArrayList<>();
        for (Edge e : edges) {
            if (e == null) {
                throw new IllegalArgumentException("edges must not contain null");
            }
            if (e.a() < 0 || e.a() >= zoneCount || e.b() < 0 || e.b() >= zoneCount) {
                throw new IllegalArgumentException("edge " + e.id() + " has an endpoint outside [0, " + zoneCount + ")");
            }
            if (blockedIds == null || !blockedIds.contains(e.id())) {
                open.add(e);
            }
        }

        DisjointSetUnion dsu = new DisjointSetUnion(zoneCount);
        for (Edge e : open) {
            dsu.union(e.a(), e.b());
        }

        // Build components ordered by smallest member.
        int[] zoneComponent = new int[zoneCount];
        int[] rootToComp = new int[zoneCount];
        java.util.Arrays.fill(rootToComp, -1);
        List<List<Integer>> members = new ArrayList<>();
        for (int z = 0; z < zoneCount; z++) {
            int root = dsu.find(z);
            if (rootToComp[root] == -1) {
                rootToComp[root] = members.size();
                members.add(new ArrayList<>());
            }
            zoneComponent[z] = rootToComp[root];
            members.get(rootToComp[root]).add(z);
        }
        int largest = -1;
        for (int c = 0; c < members.size(); c++) {
            if (largest == -1 || members.get(c).size() > members.get(largest).size()) {
                largest = c;
            }
        }
        List<Component> components = new ArrayList<>();
        List<Integer> isolated = new ArrayList<>();
        for (int c = 0; c < members.size(); c++) {
            List<Integer> m = members.get(c);
            components.add(new Component(c, m.size(), m, c == largest));
            if (m.size() == 1) {
                isolated.add(m.get(0));
            }
        }
        long unions = dsu.getUnionCount();
        long finds = dsu.getFindCount();

        // Critical roads: removal of the edge increases the component count.
        int baseCount = dsu.getComponentCount();
        List<Long> critical = new ArrayList<>();
        for (int skip = 0; skip < open.size(); skip++) {
            Edge candidate = open.get(skip);
            if (candidate.a() == candidate.b()) {
                continue;
            }
            DisjointSetUnion trial = new DisjointSetUnion(zoneCount);
            for (int i = 0; i < open.size(); i++) {
                if (i != skip) {
                    trial.union(open.get(i).a(), open.get(i).b());
                }
            }
            if (trial.getComponentCount() > baseCount) {
                critical.add(candidate.id());
            }
        }
        return new ConnectivityResult(zoneCount, open.size(), baseCount, components, zoneComponent,
                isolated, critical, unions, finds);
    }
}
