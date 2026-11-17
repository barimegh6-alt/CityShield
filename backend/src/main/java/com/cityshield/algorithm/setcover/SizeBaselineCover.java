package com.cityshield.algorithm.setcover;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Naive baseline: take sets by descending raw size (ignoring cost and overlap) until the universe is
 * covered. Used only for comparison against {@link GreedySetCover}. Time O(m^2 + m*s).
 */
public final class SizeBaselineCover {
    private SizeBaselineCover() {
    }

    public static CoverResult solve(int universeSize, List<CoverSet> sets, Set<Long> forcedIds) {
        GreedySetCover.validate(universeSize, sets);
        boolean[] covered = new boolean[universeSize];
        List<CoverStep> steps = new ArrayList<>();
        List<Long> ids = new ArrayList<>();
        boolean[] used = new boolean[sets.size()];
        int count = 0;
        double cost = 0;
        int n = 0;
        for (int i = 0; i < sets.size(); i++) {
            CoverSet s = sets.get(i);
            if (forcedIds != null && forcedIds.contains(s.id())) {
                used[i] = true;
                List<Integer> fresh = GreedySetCover.newlyCovered(s, covered);
                fresh.forEach(e -> covered[e] = true);
                count += fresh.size();
                steps.add(new CoverStep(++n, s.id(), fresh, 0, fresh.size(), true));
                ids.add(s.id());
            }
        }
        while (count < universeSize) {
            int best = -1;
            for (int i = 0; i < sets.size(); i++) {
                if (!used[i] && (best < 0 || sets.get(i).elements().length > sets.get(best).elements().length)) {
                    best = i;
                }
            }
            if (best < 0) {
                break;
            }
            used[best] = true;
            CoverSet s = sets.get(best);
            List<Integer> fresh = GreedySetCover.newlyCovered(s, covered);
            if (fresh.isEmpty()) {
                continue;
            }
            fresh.forEach(e -> covered[e] = true);
            count += fresh.size();
            cost += s.cost();
            steps.add(new CoverStep(++n, s.id(), fresh, s.cost(), fresh.size() / Math.max(s.cost(), 1e-9), false));
            ids.add(s.id());
        }
        return new CoverResult(steps, ids, count, GreedySetCover.uncovered(covered), cost, 0);
    }
}
