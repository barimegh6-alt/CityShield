package com.cityshield.algorithm.setcover;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Greedy weighted set-cover approximation.
 *
 * <p>Repeatedly pick the set with the best ratio {@code newlyCovered / cost}. Pre-selected ("forced")
 * sets, such as existing emergency centres, are taken first at zero marginal cost. The classic analysis
 * gives an approximation factor of {@code H(n) <= ln n + 1} for covering the whole universe, but the
 * result is <b>not guaranteed optimal</b>.</p>
 *
 * <p>Time: O(k * m * s) for k picks, m sets of average size s (straightforward implementation);
 * space: O(n + m).</p>
 */
public final class GreedySetCover {
    private static final double EPS = 1e-9;

    private GreedySetCover() {
    }

    /**
     * Runs the greedy algorithm.
     *
     * @param universeSize number of elements (0..universeSize-1) that should be covered
     * @param sets         candidate sets
     * @param forcedIds    ids of sets that are already selected (may be null/empty)
     * @param maxNewSets   maximum number of non-forced sets to add, or null for unlimited
     */
    public static CoverResult solve(int universeSize, List<CoverSet> sets, Set<Long> forcedIds, Integer maxNewSets) {
        validate(universeSize, sets);
        if (maxNewSets != null && maxNewSets < 0) {
            throw new IllegalArgumentException("maxNewSets must be >= 0");
        }
        boolean[] covered = new boolean[universeSize];
        boolean[] used = new boolean[sets.size()];
        List<CoverStep> steps = new ArrayList<>();
        List<Long> selected = new ArrayList<>();
        int coveredCount = 0;
        double totalCost = 0;
        int stepNo = 0;

        if (forcedIds != null) {
            for (int i = 0; i < sets.size(); i++) {
                CoverSet s = sets.get(i);
                if (forcedIds.contains(s.id())) {
                    used[i] = true;
                    List<Integer> fresh = newlyCovered(s, covered);
                    for (int e : fresh) {
                        covered[e] = true;
                    }
                    coveredCount += fresh.size();
                    steps.add(new CoverStep(++stepNo, s.id(), fresh, 0, fresh.size(), true));
                    selected.add(s.id());
                }
            }
        }

        int picked = 0;
        while (coveredCount < universeSize && (maxNewSets == null || picked < maxNewSets)) {
            int best = -1;
            double bestRatio = -1;
            int bestGain = 0;
            for (int i = 0; i < sets.size(); i++) {
                if (used[i]) {
                    continue;
                }
                CoverSet s = sets.get(i);
                int gain = newlyCovered(s, covered).size();
                if (gain == 0) {
                    continue;
                }
                double ratio = gain / Math.max(s.cost(), EPS);
                boolean better = ratio > bestRatio + 1e-12
                        || (Math.abs(ratio - bestRatio) <= 1e-12
                        && (gain > bestGain || (gain == bestGain && best >= 0 && s.id() < sets.get(best).id())));
                if (better) {
                    best = i;
                    bestRatio = ratio;
                    bestGain = gain;
                }
            }
            if (best < 0) {
                break; // nothing more can be covered
            }
            CoverSet s = sets.get(best);
            used[best] = true;
            List<Integer> fresh = newlyCovered(s, covered);
            for (int e : fresh) {
                covered[e] = true;
            }
            coveredCount += fresh.size();
            totalCost += s.cost();
            picked++;
            steps.add(new CoverStep(++stepNo, s.id(), fresh, s.cost(), bestRatio, false));
            selected.add(s.id());
        }
        return new CoverResult(steps, selected, coveredCount, uncovered(covered), totalCost, 0);
    }

    static List<Integer> newlyCovered(CoverSet s, boolean[] covered) {
        boolean[] seen = new boolean[covered.length];
        List<Integer> fresh = new ArrayList<>();
        for (int e : s.elements()) {
            if (!covered[e] && !seen[e]) {
                seen[e] = true;
                fresh.add(e);
            }
        }
        // ascending order via counting over the boolean marks
        List<Integer> sorted = new ArrayList<>(fresh.size());
        for (int i = 0; i < seen.length && sorted.size() < fresh.size(); i++) {
            if (seen[i]) {
                sorted.add(i);
            }
        }
        return sorted;
    }

    static List<Integer> uncovered(boolean[] covered) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < covered.length; i++) {
            if (!covered[i]) {
                out.add(i);
            }
        }
        return out;
    }

    static void validate(int universeSize, List<CoverSet> sets) {
        if (universeSize < 0) {
            throw new IllegalArgumentException("universeSize must be >= 0");
        }
        if (sets == null) {
            throw new IllegalArgumentException("sets must not be null");
        }
        for (CoverSet s : sets) {
            for (int e : s.elements()) {
                if (e < 0 || e >= universeSize) {
                    throw new IllegalArgumentException("element " + e + " of set " + s.id()
                            + " is outside [0," + universeSize + ")");
                }
            }
        }
    }
}
