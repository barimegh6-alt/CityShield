package com.cityshield.algorithm.setcover;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Exact minimum-cost cover by exhaustive bitmask enumeration, used to measure how far the greedy
 * answer is from optimal on small instances.
 *
 * <p>The target is to cover the largest coverable part of the universe (the union of all sets) at minimum
 * total cost of non-forced sets. Time O(2^m * n) for m non-forced sets; space O(n).</p>
 */
public final class ExactSetCover {
    /** Largest number of non-forced candidate sets accepted. */
    public static final int MAX_SETS = 24;

    private ExactSetCover() {
    }

    public static CoverResult solve(int universeSize, List<CoverSet> sets, Set<Long> forcedIds) {
        GreedySetCover.validate(universeSize, sets);
        List<CoverSet> forced = new ArrayList<>();
        List<CoverSet> optional = new ArrayList<>();
        for (CoverSet s : sets) {
            if (forcedIds != null && forcedIds.contains(s.id())) {
                forced.add(s);
            } else {
                optional.add(s);
            }
        }
        int m = optional.size();
        if (m > MAX_SETS) {
            throw new IllegalArgumentException("exact solver supports at most " + MAX_SETS + " candidate sets but got " + m);
        }
        boolean[] base = new boolean[universeSize];
        int baseCount = mark(base, forced);
        boolean[] reachable = base.clone();
        int reachableCount = baseCount + mark(reachable, optional);

        double bestCost = Double.POSITIVE_INFINITY;
        long bestMask = 0;
        int bestSize = Integer.MAX_VALUE;
        long checked = 0;
        for (long mask = 0; mask < (1L << m); mask++) {
            checked++;
            double cost = 0;
            boolean[] cov = base.clone();
            int count = baseCount;
            for (int i = 0; i < m; i++) {
                if ((mask >> i & 1L) != 0) {
                    cost += optional.get(i).cost();
                    count += mark(cov, List.of(optional.get(i)));
                }
            }
            if (count < reachableCount) {
                continue;
            }
            int size = Long.bitCount(mask);
            if (cost < bestCost - 1e-12 || (Math.abs(cost - bestCost) <= 1e-12 && size < bestSize)) {
                bestCost = cost;
                bestMask = mask;
                bestSize = size;
            }
        }
        List<Long> ids = new ArrayList<>();
        List<CoverStep> steps = new ArrayList<>();
        boolean[] cov = new boolean[universeSize];
        int n = 0;
        for (CoverSet s : forced) {
            List<Integer> fresh = GreedySetCover.newlyCovered(s, cov);
            fresh.forEach(e -> cov[e] = true);
            steps.add(new CoverStep(++n, s.id(), fresh, 0, fresh.size(), true));
            ids.add(s.id());
        }
        for (int i = 0; i < m; i++) {
            if ((bestMask >> i & 1L) != 0) {
                CoverSet s = optional.get(i);
                List<Integer> fresh = GreedySetCover.newlyCovered(s, cov);
                fresh.forEach(e -> cov[e] = true);
                steps.add(new CoverStep(++n, s.id(), fresh, s.cost(), fresh.size() / Math.max(s.cost(), 1e-9), false));
                ids.add(s.id());
            }
        }
        int coveredCount = 0;
        for (boolean b : cov) {
            if (b) {
                coveredCount++;
            }
        }
        return new CoverResult(steps, ids, coveredCount, GreedySetCover.uncovered(cov), bestCost, checked);
    }

    private static int mark(boolean[] covered, List<CoverSet> sets) {
        int added = 0;
        for (CoverSet s : sets) {
            for (int e : s.elements()) {
                if (!covered[e]) {
                    covered[e] = true;
                    added++;
                }
            }
        }
        return added;
    }
}
