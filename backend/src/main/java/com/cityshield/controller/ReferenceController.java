package com.cityshield.controller;

import com.cityshield.exception.ApiException;
import com.cityshield.service.NetworkService;
import com.cityshield.service.ReferenceService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Health and read-only reference data, plus the road open/blocked toggle. */
@RestController
@RequestMapping("/api/v1")
public class ReferenceController {
    public record RoadStatusRequest(String status) {
    }

    private final ReferenceService reference;
    private final NetworkService network;

    public ReferenceController(ReferenceService reference, NetworkService network) {
        this.reference = reference;
        this.network = network;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return reference.health();
    }

    @GetMapping("/zones")
    public List<Map<String, Object>> zones() {
        return reference.zones();
    }

    @GetMapping("/roads")
    public List<Map<String, Object>> roads() {
        return reference.roads();
    }

    @PatchMapping("/roads/{id}/status")
    public Map<String, Object> roadStatus(@PathVariable long id, @RequestBody RoadStatusRequest body) {
        if (body == null || body.status() == null) {
            throw ApiException.badRequest("status is required (OPEN or BLOCKED)");
        }
        return network.setRoadStatus(id, body.status());
    }

    @GetMapping("/centres")
    public List<Map<String, Object>> centres() {
        return reference.centres();
    }

    @GetMapping("/hospitals")
    public List<Map<String, Object>> hospitals() {
        return reference.hospitals();
    }

    @GetMapping("/resource-types")
    public List<Map<String, Object>> resourceTypes() {
        return reference.resourceTypes();
    }

    @GetMapping("/relief-destinations")
    public List<Map<String, Object>> destinations() {
        return reference.destinations();
    }

    @GetMapping("/admin/db-check")
    public Map<String, Object> dbCheck() {
        return reference.dbCheck();
    }
}
