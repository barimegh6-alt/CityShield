package com.cityshield.controller;

import com.cityshield.exception.ApiException;
import com.cityshield.service.AllocationService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Module 4: response teams and Kuhn bipartite-matching allocation (preview, then commit). */
@RestController
@RequestMapping("/api/v1")
public class AllocationController {
    public record TeamRequest(String name, String teamType, Long baseZoneId, Long centreId, String availability) {
    }

    public record AvailabilityRequest(String availability) {
    }

    public record PreviewRequest(List<Long> incidentIds, Boolean requireReachability, List<Long> blockedRoadIds) {
    }

    public record Assignment(Long incidentId, Long teamId) {
    }

    public record CommitRequest(List<Assignment> assignments) {
    }

    private final AllocationService service;

    public AllocationController(AllocationService service) {
        this.service = service;
    }

    @GetMapping("/teams")
    public List<Map<String, Object>> teams() {
        return service.listTeams();
    }

    @PostMapping("/teams")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createTeam(@RequestBody TeamRequest b) {
        return service.saveTeam(null, b.name(), b.teamType(), b.baseZoneId(), b.centreId(), b.availability());
    }

    @PutMapping("/teams/{id}")
    public Map<String, Object> updateTeam(@PathVariable long id, @RequestBody TeamRequest b) {
        return service.saveTeam(id, b.name(), b.teamType(), b.baseZoneId(), b.centreId(), b.availability());
    }

    @PatchMapping("/teams/{id}/availability")
    public Map<String, Object> availability(@PathVariable long id, @RequestBody AvailabilityRequest b) {
        return service.setAvailability(id, b.availability());
    }

    @GetMapping("/allocation/eligibility/{incidentId}")
    public List<Map<String, Object>> eligibility(@PathVariable long incidentId) {
        return service.eligibility(incidentId);
    }

    @PostMapping("/allocation/preview")
    public Map<String, Object> preview(@RequestBody(required = false) PreviewRequest b) {
        PreviewRequest r = b == null ? new PreviewRequest(null, null, null) : b;
        return service.preview(r.incidentIds(), r.requireReachability() == null || r.requireReachability(), r.blockedRoadIds());
    }

    @PostMapping("/allocation/commit")
    public Map<String, Object> commit(@RequestBody CommitRequest b) {
        if (b == null || b.assignments() == null || b.assignments().isEmpty()) {
            throw ApiException.badRequest("assignments must not be empty");
        }
        List<long[]> pairs = new ArrayList<>();
        for (Assignment a : b.assignments()) {
            if (a == null || a.incidentId() == null || a.teamId() == null) {
                throw ApiException.badRequest("every assignment needs incidentId and teamId");
            }
            pairs.add(new long[]{a.incidentId(), a.teamId()});
        }
        return service.commit(pairs);
    }

    @GetMapping("/allocation/assignments")
    public List<Map<String, Object>> assignments(@RequestParam(defaultValue = "true") boolean activeOnly) {
        return service.listAssignments(activeOnly);
    }

    @PatchMapping("/allocation/assignments/{id}/release")
    public Map<String, Object> release(@PathVariable long id) {
        return service.release(id);
    }
}
