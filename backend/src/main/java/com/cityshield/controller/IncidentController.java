package com.cityshield.controller;

import com.cityshield.dto.IncidentRequest;
import com.cityshield.dto.StatusRequest;
import com.cityshield.service.IncidentService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
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

/** Module 2: incident CRUD and status workflow. */
@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentController {
    private final IncidentService service;

    public IncidentController(IncidentService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, Object> list(@RequestParam(required = false) String status,
                                    @RequestParam(required = false) String category,
                                    @RequestParam(required = false) Long zoneId,
                                    @RequestParam(required = false) String q,
                                    @RequestParam(defaultValue = "priority") String sort,
                                    @RequestParam(defaultValue = "desc") String dir,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        return service.list(status, category, zoneId, q, sort, dir, page, size);
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable long id) {
        return service.detail(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@Valid @RequestBody IncidentRequest body) {
        return service.create(body);
    }

    @PutMapping("/{id}")
    public Map<String, Object> update(@PathVariable long id, @Valid @RequestBody IncidentRequest body) {
        return service.update(id, body);
    }

    @PatchMapping("/{id}/status")
    public Map<String, Object> status(@PathVariable long id, @Valid @RequestBody StatusRequest body) {
        return service.changeStatus(id, body.status(), body.note());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> archive(@PathVariable long id) {
        return service.archive(id);
    }
}
