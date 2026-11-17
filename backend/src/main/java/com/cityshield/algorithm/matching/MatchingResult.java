package com.cityshield.algorithm.matching;

import java.util.List;

/**
 * Immutable result of {@link KuhnMatcher#match}.
 *
 * @param matchOfSlot    team index assigned to each slot, or {@code -1} if the slot is unmatched
 * @param matchOfTeam    slot index assigned to each team, or {@code -1} if the team is free
 * @param size           matching cardinality
 * @param unmatchedSlots indices of unmatched slots in processing (priority) order
 * @param dfsVisits      total number of DFS invocations (left vertices entered) over all augmentation attempts
 */
public record MatchingResult(int[] matchOfSlot,
                             int[] matchOfTeam,
                             int size,
                             List<Integer> unmatchedSlots,
                             long dfsVisits) {
    public MatchingResult {
        matchOfSlot = matchOfSlot.clone();
        matchOfTeam = matchOfTeam.clone();
        unmatchedSlots = List.copyOf(unmatchedSlots);
    }

    @Override
    public int[] matchOfSlot() {
        return matchOfSlot.clone();
    }

    @Override
    public int[] matchOfTeam() {
        return matchOfTeam.clone();
    }
}
