package com.cityshield.algorithm.matching;

import java.util.ArrayList;
import java.util.List;

/**
 * Kuhn's augmenting-path algorithm for maximum bipartite matching.
 *
 * <p>Left side = slots (incident slots, in priority order), right side = teams. The caller supplies,
 * for each slot, the list of eligible team indices (optionally ordered by preference, e.g. ascending
 * distance).
 *
 * <h2>Why processing slots in priority order gives preference</h2>
 * Slots are tried one after another in the given order. Each attempt searches for an augmenting path
 * starting at that slot, and an augmenting path only <em>adds</em> one matched vertex on each side:
 * every left vertex that was matched before stays matched afterwards (its partner may change, but it
 * never becomes free). Consequently a later (lower priority) slot can never push an earlier slot into
 * the unmatched state. The set of matched left vertices after processing slots 0..i is exactly the
 * set a greedy-by-priority algorithm would pick on the transversal matroid of the graph: slot i is
 * added iff the set of already matched slots plus i is still matchable. Therefore the final matched
 * set is a maximum-cardinality, lexicographically priority-greedy basis: no higher-priority slot is
 * left unmatched in favour of a lower-priority one when both cannot be served. Preference inside an
 * adjacency list only affects <em>which</em> team is tried first; a team may later be re-assigned
 * along an augmenting path, but the slot that loses it always receives another team.
 *
 * <p>Guarantees: maximum cardinality (Berge's lemma: no augmenting path remains for any slot after it
 * was processed and the matching only grows) and every team is matched to at most one slot.
 *
 * <p>Complexity: O(S * E) time (S slots, E adjacency entries) using a visited stamp that avoids O(T)
 * clearing per attempt; O(S + T) additional space plus recursion depth at most min(S, T).
 */
public final class KuhnMatcher {

    private KuhnMatcher() {
    }

    /**
     * Convenience overload for list-based adjacency.
     *
     * @param adjacency eligible team indices per slot
     * @param teamCount number of teams
     * @return matching result
     * @throws IllegalArgumentException on invalid input (see {@link #match(int[][], int)})
     */
    public static MatchingResult match(List<? extends List<Integer>> adjacency, int teamCount) {
        if (adjacency == null) {
            throw new IllegalArgumentException("adjacency is null");
        }
        int[][] a = new int[adjacency.size()][];
        for (int i = 0; i < a.length; i++) {
            List<Integer> row = adjacency.get(i);
            if (row == null) {
                throw new IllegalArgumentException("adjacency row " + i + " is null");
            }
            a[i] = new int[row.size()];
            for (int j = 0; j < a[i].length; j++) {
                Integer x = row.get(j);
                if (x == null) {
                    throw new IllegalArgumentException("null team index in row " + i);
                }
                a[i][j] = x;
            }
        }
        return match(a, teamCount);
    }

    /**
     * Computes a maximum matching, processing slots 0, 1, 2, ... in order. The input is not modified.
     * Time O(S*E), space O(S+T).
     *
     * @param adjacency {@code adjacency[s]} = eligible team indices for slot {@code s}, in order of preference
     * @param teamCount number of teams (right vertices), at least 0
     * @return matching, unmatched slots and DFS statistics
     * @throws IllegalArgumentException if {@code teamCount < 0}, the adjacency is null, or a team index is out of range
     */
    public static MatchingResult match(int[][] adjacency, int teamCount) {
        if (adjacency == null) {
            throw new IllegalArgumentException("adjacency is null");
        }
        if (teamCount < 0) {
            throw new IllegalArgumentException("teamCount must be >= 0");
        }
        for (int s = 0; s < adjacency.length; s++) {
            if (adjacency[s] == null) {
                throw new IllegalArgumentException("adjacency row " + s + " is null");
            }
            for (int t : adjacency[s]) {
                if (t < 0 || t >= teamCount) {
                    throw new IllegalArgumentException("Team index " + t + " out of range in slot " + s);
                }
            }
        }
        Run run = new Run(adjacency, teamCount);
        int size = 0;
        List<Integer> unmatched = new ArrayList<>();
        for (int s = 0; s < adjacency.length; s++) {
            run.stamp++;
            if (run.tryAugment(s)) {
                size++;
            } else {
                unmatched.add(s);
            }
        }
        // A slot that failed to augment can never become matched later (Kuhn property), and matched
        // slots never become unmatched, so 'unmatched' is final.
        return new MatchingResult(run.matchOfSlot, run.matchOfTeam, size, unmatched, run.visits);
    }

    private static final class Run {
        final int[][] adj;
        final int[] matchOfSlot;
        final int[] matchOfTeam;
        final int[] visitedStamp;
        int stamp;
        long visits;

        Run(int[][] adj, int teamCount) {
            this.adj = adj;
            this.matchOfSlot = new int[adj.length];
            this.matchOfTeam = new int[teamCount];
            this.visitedStamp = new int[teamCount];
            java.util.Arrays.fill(matchOfSlot, -1);
            java.util.Arrays.fill(matchOfTeam, -1);
        }

        boolean tryAugment(int slot) {
            visits++;
            for (int team : adj[slot]) {
                if (visitedStamp[team] == stamp) {
                    continue;
                }
                visitedStamp[team] = stamp;
                int other = matchOfTeam[team];
                if (other == -1 || tryAugment(other)) {
                    matchOfTeam[team] = slot;
                    matchOfSlot[slot] = team;
                    return true;
                }
            }
            return false;
        }
    }
}
