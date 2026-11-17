package com.cityshield.controller;

import com.cityshield.service.AnalyticsService;
import java.util.Locale;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Percentile analytics with seeded randomized Quickselect. */
@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {
    public record PercentileRequest(String metric, Double percentile, Long seed, String scope) {
    }

    public record SweepRequest(String metric, Long seed, String scope) {
    }

    private final AnalyticsService service;

    public AnalyticsController(AnalyticsService service) {
        this.service = service;
    }

    private static String up(String s, String def) {
        return s == null || s.isBlank() ? def : s.trim().toUpperCase(Locale.ROOT);
    }

    @PostMapping("/percentile")
    public Map<String, Object> percentile(@RequestBody(required = false) PercentileRequest b) {
        PercentileRequest r = b == null ? new PercentileRequest(null, null, null, null) : b;
        return service.percentile(up(r.metric(), "PRIORITY"), r.percentile() == null ? 90.0 : r.percentile(),
                r.seed() == null ? 42L : r.seed(), up(r.scope(), "ACTIVE"));
    }

    @PostMapping("/percentile-sweep")
    public Map<String, Object> sweep(@RequestBody(required = false) SweepRequest b) {
        SweepRequest r = b == null ? new SweepRequest(null, null, null) : b;
        return service.sweep(up(r.metric(), "PRIORITY"), r.seed() == null ? 42L : r.seed(), up(r.scope(), "ACTIVE"));
    }
}
