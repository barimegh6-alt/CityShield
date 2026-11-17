package com.cityshield.service;

import com.cityshield.algorithm.heap.HeapEntry;
import com.cityshield.algorithm.heap.HeapStats;
import com.cityshield.algorithm.heap.IncidentMaxHeap;
import com.cityshield.entity.EmergencyIncident;
import com.cityshield.repository.EmergencyIncidentRepository;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Keeps the custom binary max-heap of incidents awaiting dispatch (status REPORTED) in memory.
 * It is built lazily from the database and updated on every create, edit and status change.
 * The database stays the source of truth; {@link #rebuild()} resynchronises the heap.
 */
@Service
public class PriorityService {
    private final EmergencyIncidentRepository incidents;
    private final IncidentMapper mapper;
    private IncidentMaxHeap heap = new IncidentMaxHeap();
    private boolean loaded = false;

    public PriorityService(EmergencyIncidentRepository incidents, IncidentMapper mapper) {
        this.incidents = incidents;
        this.mapper = mapper;
    }

    private HeapEntry entry(EmergencyIncident i, IncidentMapper.Ctx c) {
        return new HeapEntry(i.incidentId, mapper.score(i, c).score(), i.reportedAt.toEpochSecond(ZoneOffset.UTC));
    }

    /** Bottom-up heapify of all REPORTED incidents. @return number of incidents loaded. */
    public synchronized int rebuild() {
        List<EmergencyIncident> list = incidents.findByStatusIn(List.of("REPORTED"));
        List<Long> ids = new ArrayList<>();
        for (EmergencyIncident i : list) {
            ids.add(i.incidentId);
        }
        IncidentMapper.Ctx c = mapper.ctx(ids);
        List<HeapEntry> entries = new ArrayList<>();
        for (EmergencyIncident i : list) {
            entries.add(entry(i, c));
        }
        IncidentMaxHeap fresh = new IncidentMaxHeap();
        fresh.buildHeap(entries);
        heap = fresh;
        loaded = true;
        return entries.size();
    }

    private void ensureLoaded() {
        if (!loaded) {
            rebuild();
        }
    }

    /** Called after an incident was created, edited or changed status. */
    public synchronized void sync(EmergencyIncident inc) {
        if (!loaded) {
            return; // first use will load from the database, which already contains the change
        }
        if ("REPORTED".equals(inc.status)) {
            HeapEntry e = entry(inc, mapper.ctx(List.of(inc.incidentId)));
            if (heap.contains(inc.incidentId)) {
                heap.updatePriority(inc.incidentId, e.priority());
            } else {
                heap.insert(e);
            }
        } else {
            heap.remove(inc.incidentId);
        }
    }

    public synchronized List<HeapEntry> top(int limit) {
        ensureLoaded();
        return heap.topN(Math.max(0, Math.min(limit, heap.size())));
    }

    public synchronized HeapEntry peek() {
        ensureLoaded();
        return heap.isEmpty() ? null : heap.peek();
    }

    /** Removes and returns the highest-priority incident entry, or null when empty. */
    public synchronized HeapEntry extractMax() {
        ensureLoaded();
        return heap.isEmpty() ? null : heap.extractMax();
    }

    public synchronized int size() {
        ensureLoaded();
        return heap.size();
    }

    public synchronized HeapStats stats() {
        ensureLoaded();
        return heap.stats();
    }
}
