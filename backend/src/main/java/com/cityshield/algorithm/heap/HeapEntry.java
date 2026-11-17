package com.cityshield.algorithm.heap;

/**
 * Immutable element stored in {@link IncidentMaxHeap}.
 *
 * <p>Ordering inside the heap: higher {@code priority} first; on equal priority the earlier
 * {@code reportedAt} wins; on equal reportedAt the lower {@code id} wins. Since ids are unique
 * the order is total, so ranking is fully deterministic.</p>
 *
 * @param id         unique identifier (e.g. incident database id)
 * @param priority   priority score, must be finite (higher = more urgent)
 * @param reportedAt report time as a monotonically comparable number (e.g. epoch millis)
 */
public record HeapEntry(long id, double priority, long reportedAt) {
    /** Validates that the priority is a finite number. */
    public HeapEntry {
        if (Double.isNaN(priority) || Double.isInfinite(priority)) {
            throw new IllegalArgumentException("priority must be a finite number but was " + priority);
        }
    }
}
