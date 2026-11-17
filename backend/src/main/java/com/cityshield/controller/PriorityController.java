package com.cityshield.controller;

import static com.cityshield.util.Json.obj;

import com.cityshield.algorithm.heap.HeapEntry;
import com.cityshield.algorithm.heap.HeapStats;
import com.cityshield.algorithm.heap.PriorityCalculator;
import com.cityshield.entity.EmergencyIncident;
import com.cityshield.exception.ApiException;
import com.cityshield.repository.EmergencyIncidentRepository;
import com.cityshield.service.AnalyticsService;
import com.cityshield.service.ExecutionLogService;
import com.cityshield.service.IncidentMapper;
import com.cityshield.service.IncidentService;
import com.cityshield.service.PriorityService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Module 3: binary-heap priority queue of REPORTED incidents. */
@RestController
@RequestMapping("/api/v1/priority")
public class PriorityController {
    private final PriorityService priority;
    private final IncidentService incidentService;
    private final EmergencyIncidentRepository incidents;
    private final IncidentMapper mapper;
    private final AnalyticsService analytics;
    private final ExecutionLogService exec;

    public PriorityController(PriorityService priority, IncidentService incidentService, EmergencyIncidentRepository incidents,
                              IncidentMapper mapper, AnalyticsService analytics, ExecutionLogService exec) {
        this.priority = priority;
        this.incidentService = incidentService;
        this.incidents = incidents;
        this.mapper = mapper;
        this.analytics = analytics;
        this.exec = exec;
    }

    private static Map<String, Object> stats(HeapStats s) {
        return obj("insertions", s.insertions(), "extractions", s.extractions(), "comparisons", s.comparisons(), "swaps", s.swaps());
    }

    @GetMapping("/queue")
    public Map<String, Object> queue(@RequestParam(defaultValue = "25") int limit) {
        if (limit < 1 || limit > 500) {
            throw ApiException.badRequest("limit must be between 1 and 500");
        }
        long t0 = System.nanoTime();
        List<HeapEntry> top = priority.top(limit);
        List<Long> ids = new ArrayList<>();
        for (HeapEntry e : top) {
            ids.add(e.id());
        }
        Map<Long, EmergencyIncident> byId = new HashMap<>();
        incidents.findAllById(ids).forEach(i -> byId.put(i.incidentId, i));
        IncidentMapper.Ctx ctx = mapper.ctx(ids);
        List<Map<String, Object>> items = new ArrayList<>();
        int rank = 1;
        for (HeapEntry e : top) {
            EmergencyIncident i = byId.get(e.id());
            if (i != null) {
                items.add(obj("rank", rank++, "incident", mapper.dto(i, ctx)));
            }
        }
        Double p90 = analytics.priorityThreshold(90);
        return obj("size", priority.size(), "items", items, "heapStats", stats(priority.stats()), "highRiskThreshold", p90,
                "execution", exec.record("HEAP_QUEUE", t0, priority.size(), "top " + items.size() + " of " + priority.size()));
    }

    @GetMapping("/peek")
    public Map<String, Object> peek() {
        HeapEntry e = priority.peek();
        return obj("incident", e == null ? null : incidentService.detail(e.id()));
    }

    @PostMapping("/extract-next")
    public Map<String, Object> extractNext() {
        long t0 = System.nanoTime();
        HeapEntry e = priority.extractMax();
        if (e == null) {
            throw ApiException.conflict("The priority queue is empty - no REPORTED incidents to dispatch");
        }
        try {
            Map<String, Object> inc = incidentService.changeStatus(e.id(), "IN_PROGRESS", "Dispatched from priority queue");
            int remaining = priority.size();
            exec.record("HEAP_EXTRACT", t0, remaining + 1, "extracted incident " + e.id());
            return obj("incident", inc, "remaining", remaining);
        } catch (RuntimeException ex) {
            priority.rebuild(); // the database is the source of truth; undo the in-memory pop
            throw ex;
        }
    }

    @PostMapping("/rebuild")
    public Map<String, Object> rebuild() {
        long t0 = System.nanoTime();
        int n = priority.rebuild();
        return obj("size", n, "execution", exec.record("HEAP_REBUILD", t0, n, "bottom-up heapify of " + n + " incidents"));
    }

    @GetMapping("/formula")
    public Map<String, Object> formula() {
        return obj("formula", "P = 100 x (0.40S + 0.25U + 0.20A + 0.15R)",
                "weights", obj("s", PriorityCalculator.DEFAULT_WEIGHT_SEVERITY, "u", PriorityCalculator.DEFAULT_WEIGHT_URGENCY,
                        "a", PriorityCalculator.DEFAULT_WEIGHT_AFFECTED, "r", PriorityCalculator.DEFAULT_WEIGHT_RESOURCE),
                "definitions", obj("s", "severity / 5", "u", "urgency / 5",
                        "a", "min(affectedPeople, 5000) / 5000",
                        "r", "shortfall of the incident's required units against stock at its own zone, 0 (fully stocked) to 1 (no stock)"),
                "note", "All factors are clamped to 0-1, so the score is always 0-100 and a higher score always means more urgent.");
    }
}
