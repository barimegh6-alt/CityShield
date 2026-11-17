package com.cityshield.service;

import static com.cityshield.util.Json.obj;

import com.cityshield.benchmark.BenchmarkHarness;
import com.cityshield.benchmark.BenchmarkRow;
import com.cityshield.entity.BenchmarkResult;
import com.cityshield.exception.ApiException;
import com.cityshield.repository.BenchmarkResultRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Runs the pure-Java benchmark harness on request and stores the measured rows (Module 8). */
@Service
public class BenchmarkService {
    private final BenchmarkResultRepository repo;
    private final ExecutionLogService exec;

    public BenchmarkService(BenchmarkResultRepository repo, ExecutionLogService exec) {
        this.repo = repo;
        this.exec = exec;
    }

    public static Map<String, Object> machine() {
        return obj("os", System.getProperty("os.name"), "javaVersion", System.getProperty("java.version"),
                "cpus", Runtime.getRuntime().availableProcessors());
    }

    private static Map<String, Object> row(BenchmarkRow r) {
        return obj("algorithm", r.algorithm(), "variant", r.variant(), "inputSize", r.inputSize(), "medianMs", r.medianMs(),
                "minMs", r.minMs(), "maxMs", r.maxMs(), "repetitions", r.repetitions(), "note", r.note());
    }

    private static Map<String, Object> row(BenchmarkResult r) {
        return obj("algorithm", r.algorithm, "variant", r.variant, "inputSize", r.inputSize, "medianMs", r.medianMs,
                "minMs", r.minMs, "maxMs", r.maxMs, "repetitions", r.repetitions, "note", r.note, "runAt", r.runAt);
    }

    /** Serialised: concurrent benchmark runs would disturb each other's timings. */
    @Transactional
    public synchronized Map<String, Object> run(String algorithm, Integer repetitions) {
        String a = algorithm == null || algorithm.isBlank() ? "ALL" : algorithm.toUpperCase(Locale.ROOT);
        if (!"ALL".equals(a) && !BenchmarkHarness.ALGORITHMS.contains(a)) {
            throw ApiException.badRequest("algorithm must be ALL or one of " + BenchmarkHarness.ALGORITHMS);
        }
        int reps = repetitions == null ? 5 : repetitions;
        if (reps < 1 || reps > 25) {
            throw ApiException.badRequest("repetitions must be between 1 and 25");
        }
        long t0 = System.nanoTime();
        List<BenchmarkRow> rows = new BenchmarkHarness(reps).run(a);
        LocalDateTime now = LocalDateTime.now().withNano(0);
        String m = System.getProperty("os.name") + ", Java " + System.getProperty("java.version") + ", "
                + Runtime.getRuntime().availableProcessors() + " CPUs";
        for (String name : BenchmarkHarness.ALGORITHMS) {
            if ("ALL".equals(a) || a.equals(name)) {
                repo.deleteByAlgorithm(name);
            }
        }
        repo.flush();
        List<Map<String, Object>> out = new ArrayList<>();
        for (BenchmarkRow r : rows) {
            BenchmarkResult e = new BenchmarkResult();
            e.algorithm = r.algorithm();
            e.variant = r.variant();
            e.inputSize = r.inputSize();
            e.medianMs = r.medianMs();
            e.minMs = r.minMs();
            e.maxMs = r.maxMs();
            e.repetitions = r.repetitions();
            e.note = r.note() != null && r.note().length() > 250 ? r.note().substring(0, 250) : r.note();
            e.machine = m;
            e.runAt = now;
            repo.save(e);
            out.add(row(r));
        }
        exec.record("BENCHMARK", t0, rows.size(), a + ": " + rows.size() + " rows, " + reps + " repetitions");
        return obj("rows", out, "machine", machine(), "generatedAt", now.toString());
    }

    public List<Map<String, Object>> results(String algorithm) {
        List<Map<String, Object>> out = new ArrayList<>();
        List<String> names = algorithm == null || algorithm.isBlank() ? BenchmarkHarness.ALGORITHMS
                : List.of(algorithm.toUpperCase(Locale.ROOT));
        for (String n : names) {
            for (BenchmarkResult r : repo.findByAlgorithmOrderByInputSizeAsc(n)) {
                out.add(row(r));
            }
        }
        return out;
    }
}
