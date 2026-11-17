package com.cityshield.service;

import static com.cityshield.util.Json.obj;
import static com.cityshield.util.Json.round;

import com.cityshield.entity.AlgorithmExecutionLog;
import com.cityshield.repository.AlgorithmExecutionLogRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Records every algorithm run in algorithm_execution_logs and builds the {@code execution} response object. */
@Service
public class ExecutionLogService {
    private final AlgorithmExecutionLogRepository repo;

    public ExecutionLogService(AlgorithmExecutionLogRepository repo) {
        this.repo = repo;
    }

    /** Persists a log row and returns {@code {algorithm, durationMs, inputSize}}. */
    public Map<String, Object> record(String algorithm, long startNanos, int inputSize, String summary) {
        double ms = round((System.nanoTime() - startNanos) / 1_000_000.0, 3);
        AlgorithmExecutionLog row = new AlgorithmExecutionLog();
        row.algorithm = algorithm;
        row.runType = "API";
        row.inputSize = inputSize;
        row.durationMs = ms;
        row.summary = summary == null ? null : summary.length() > 300 ? summary.substring(0, 300) : summary;
        row.runAt = LocalDateTime.now().withNano(0);
        repo.save(row);
        return obj("algorithm", algorithm, "durationMs", ms, "inputSize", inputSize);
    }

    public List<Map<String, Object>> list(String algorithm, int limit) {
        List<AlgorithmExecutionLog> rows = algorithm == null || algorithm.isBlank()
                ? repo.findAllByOrderByRunAtDesc() : repo.findByAlgorithmOrderByRunAtDesc(algorithm.toUpperCase());
        List<Map<String, Object>> out = new ArrayList<>();
        for (AlgorithmExecutionLog r : rows) {
            if (out.size() >= Math.max(1, Math.min(limit, 500))) {
                break;
            }
            out.add(obj("id", r.logId, "algorithm", r.algorithm, "runType", r.runType, "inputSize", r.inputSize,
                    "durationMs", r.durationMs, "summary", r.summary, "runAt", r.runAt));
        }
        return out;
    }
}
