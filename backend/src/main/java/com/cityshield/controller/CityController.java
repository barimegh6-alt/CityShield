package com.cityshield.controller;

import com.cityshield.exception.ApiException;
import com.cityshield.service.BenchmarkService;
import com.cityshield.service.CoverageService;
import com.cityshield.service.DashboardService;
import com.cityshield.service.ExecutionLogService;
import com.cityshield.service.NetworkService;
import com.cityshield.service.RouteService;
import com.cityshield.service.SearchService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Modules 6-8: connectivity, coverage, routes, search lab, dashboard, benchmarks, execution logs. */
@RestController
@RequestMapping("/api/v1")
public class CityController {
    public record ConnectivityRequest(List<Long> blockedRoadIds, Boolean includeBlockedInDatabase) {
    }

    public record CoverRequest(Double radiusKm, String universe, Integer maxNewCentres) {
    }

    public record TspRequest(Long depotCentreId, List<Long> destinationIds, Boolean compareHeldKarp) {
    }

    public record IncidentSearchRequest(String pattern, List<String> fields, Boolean caseSensitive, Integer limit) {
    }

    public record BenchmarkRequest(String algorithm, Integer repetitions) {
    }

    private final NetworkService network;
    private final CoverageService coverage;
    private final RouteService routes;
    private final SearchService search;
    private final DashboardService dashboard;
    private final BenchmarkService benchmarks;
    private final ExecutionLogService executions;

    public CityController(NetworkService network, CoverageService coverage, RouteService routes, SearchService search,
                          DashboardService dashboard, BenchmarkService benchmarks, ExecutionLogService executions) {
        this.network = network;
        this.coverage = coverage;
        this.routes = routes;
        this.search = search;
        this.dashboard = dashboard;
        this.benchmarks = benchmarks;
        this.executions = executions;
    }

    @GetMapping("/network/graph")
    public Map<String, Object> graph() {
        return network.graph();
    }

    @PostMapping("/network/connectivity")
    public Map<String, Object> connectivity(@RequestBody(required = false) ConnectivityRequest b) {
        ConnectivityRequest r = b == null ? new ConnectivityRequest(null, null) : b;
        return network.connectivity(r.blockedRoadIds(), r.includeBlockedInDatabase() == null || r.includeBlockedInDatabase());
    }

    @PostMapping("/coverage/set-cover")
    public Map<String, Object> setCover(@RequestBody(required = false) CoverRequest b) {
        CoverRequest r = b == null ? new CoverRequest(null, null, null) : b;
        return coverage.setCover(r.radiusKm(), r.universe(), r.maxNewCentres());
    }

    @PostMapping("/routes/tsp")
    public Map<String, Object> tsp(@RequestBody TspRequest b) {
        return routes.tsp(b.depotCentreId(), b.destinationIds(), b.compareHeldKarp() == null || b.compareHeldKarp());
    }

    @GetMapping("/search/autocomplete")
    public Map<String, Object> autocomplete(@RequestParam(defaultValue = "") String q,
                                            @RequestParam(required = false) String types,
                                            @RequestParam(defaultValue = "10") int limit) {
        if (q.isBlank()) {
            throw ApiException.badRequest("q must not be empty");
        }
        return search.autocomplete(q, types, limit);
    }

    @PostMapping("/search/incidents")
    public Map<String, Object> searchIncidents(@RequestBody IncidentSearchRequest b) {
        if (b.pattern() == null || b.pattern().isEmpty()) {
            throw ApiException.badRequest("pattern must not be empty");
        }
        return search.searchIncidents(b.pattern(), b.fields() == null ? List.of("DESCRIPTION") : b.fields(),
                Boolean.TRUE.equals(b.caseSensitive()), b.limit() == null ? 50 : b.limit());
    }

    @GetMapping("/dashboard/summary")
    public Map<String, Object> dashboardSummary() {
        return dashboard.summary();
    }

    @PostMapping("/benchmarks/run")
    public Map<String, Object> runBenchmarks(@RequestBody(required = false) BenchmarkRequest b) {
        BenchmarkRequest r = b == null ? new BenchmarkRequest(null, null) : b;
        return benchmarks.run(r.algorithm(), r.repetitions());
    }

    @GetMapping("/benchmarks/results")
    public List<Map<String, Object>> benchmarkResults(@RequestParam(required = false) String algorithm) {
        return benchmarks.results(algorithm);
    }

    @GetMapping("/executions")
    public List<Map<String, Object>> executions(@RequestParam(required = false) String algorithm,
                                                @RequestParam(defaultValue = "50") int limit) {
        return executions.list(algorithm, limit);
    }
}
