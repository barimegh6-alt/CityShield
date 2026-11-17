package com.cityshield.controller;

import com.cityshield.exception.ApiException;
import com.cityshield.service.ResourceService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Module 5: inventory (segment tree) and relief distribution (Edmonds-Karp, preview then commit). */
@RestController
@RequestMapping("/api/v1")
public class ResourceController {
    public record RangeRequest(Long resourceTypeId, Integer fromIndex, Integer toIndex, Boolean compareLinear) {
    }

    public record AdjustRequest(Long zoneId, Long resourceTypeId, Integer delta, String reason) {
    }

    public record EdgeRequest(Integer capacity) {
    }

    public record PlanRequest(Long resourceTypeId, List<Long> blockedRoadIds) {
    }

    public record CommitRequest(Long resourceTypeId, List<Long> blockedRoadIds, Long expectedMaxFlow, Boolean confirm) {
    }

    private final ResourceService service;

    public ResourceController(ResourceService service) {
        this.service = service;
    }

    private static <T> T need(T v, String name) {
        if (v == null) {
            throw ApiException.badRequest(name + " is required");
        }
        return v;
    }

    @GetMapping("/inventory")
    public List<Map<String, Object>> inventory(@RequestParam(defaultValue = "1") long resourceTypeId) {
        return service.inventory(resourceTypeId);
    }

    @GetMapping("/inventory/summary")
    public List<Map<String, Object>> summary() {
        return service.summary();
    }

    @PostMapping("/inventory/range-query")
    public Map<String, Object> range(@RequestBody RangeRequest b) {
        return service.rangeQuery(need(b.resourceTypeId(), "resourceTypeId"), need(b.fromIndex(), "fromIndex"),
                need(b.toIndex(), "toIndex"), b.compareLinear() == null || b.compareLinear());
    }

    @PostMapping("/inventory/adjust")
    public Map<String, Object> adjust(@RequestBody AdjustRequest b) {
        return service.adjust(need(b.zoneId(), "zoneId"), need(b.resourceTypeId(), "resourceTypeId"), need(b.delta(), "delta"), b.reason());
    }

    @GetMapping("/distribution/network")
    public Map<String, Object> network(@RequestParam(defaultValue = "1") long resourceTypeId) {
        return service.networkView(resourceTypeId);
    }

    @PutMapping("/distribution/edges/{id}")
    public Map<String, Object> edge(@PathVariable long id, @RequestBody EdgeRequest b) {
        int cap = need(b.capacity(), "capacity");
        if (cap < 0) {
            throw ApiException.badRequest("capacity must be >= 0");
        }
        return service.setEdgeCapacity(id, cap);
    }

    @PostMapping("/distribution/preview")
    public Map<String, Object> preview(@RequestBody PlanRequest b) {
        return service.preview(need(b.resourceTypeId(), "resourceTypeId"), b.blockedRoadIds());
    }

    @PostMapping("/distribution/commit")
    public Map<String, Object> commit(@RequestBody CommitRequest b) {
        return service.commit(need(b.resourceTypeId(), "resourceTypeId"), b.blockedRoadIds(), b.expectedMaxFlow(),
                Boolean.TRUE.equals(b.confirm()));
    }

    @GetMapping("/distribution/allocations")
    public List<Map<String, Object>> allocations(@RequestParam(defaultValue = "50") int limit) {
        return service.allocations(limit);
    }
}
