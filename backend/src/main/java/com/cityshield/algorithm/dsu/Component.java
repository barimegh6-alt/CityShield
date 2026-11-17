package com.cityshield.algorithm.dsu;

import java.util.List;

/**
 * One connected component of the open road network.
 *
 * @param id        component number (0-based, ordered by smallest member zone index)
 * @param size      number of zones
 * @param members   zone indices in ascending order (immutable)
 * @param isLargest true for exactly one component: the largest (ties: lowest id)
 */
public record Component(int id, int size, List<Integer> members, boolean isLargest) {
    /** Makes the member list immutable. */
    public Component {
        members = List.copyOf(members);
    }
}
