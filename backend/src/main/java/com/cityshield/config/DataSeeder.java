package com.cityshield.config;

import com.cityshield.util.Csv;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Loads the synthetic CSV files from {@code classpath:seed/} on first start.
 *
 * <p>Safety: runs ONLY when {@code city_zones} is empty and {@code cityshield.seed.enabled=true}; it never
 * deletes or updates existing rows. Tables are loaded in foreign-key order inside one transaction.</p>
 */
@Component
@Order(1)
public class DataSeeder implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    /** Foreign-key-safe load order (file name without .csv = table name). */
    static final String[] ORDER = {"city_zones", "road_connections", "distribution_edges", "emergency_centres", "hospitals",
            "emergency_types", "emergency_type_team_compat", "resource_types", "resource_inventory", "response_teams",
            "emergency_incidents", "incident_resource_requirements", "incident_status_history", "incident_logs",
            "incident_assignments", "relief_destinations"};

    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager tx;
    private final boolean enabled;

    public DataSeeder(JdbcTemplate jdbc, PlatformTransactionManager tx, @Value("${cityshield.seed.enabled:true}") boolean enabled) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.enabled = enabled;
    }

    @Override
    public void run(String... args) {
        if (!enabled) {
            log.info("Seeding disabled (cityshield.seed.enabled=false)");
            return;
        }
        Long zones = jdbc.queryForObject("SELECT COUNT(*) FROM city_zones", Long.class);
        if (zones != null && zones > 0) {
            log.info("Database already contains {} zones - seed skipped, existing data untouched", zones);
            return;
        }
        new TransactionTemplate(tx).executeWithoutResult(status -> {
            for (String table : ORDER) {
                int n = load(table);
                log.info("Seeded {} rows into {}", n, table);
            }
        });
    }

    private int load(String table) {
        List<List<String>> rows;
        try (InputStream in = new ClassPathResource("seed/" + table + ".csv").getInputStream();
             BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            rows = Csv.parse(r);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read seed file seed/" + table + ".csv", e);
        }
        if (rows.size() < 2) {
            return 0;
        }
        List<String> header = rows.get(0);
        String sql = "INSERT INTO " + table + " (" + String.join(",", header) + ") VALUES ("
                + String.join(",", java.util.Collections.nCopies(header.size(), "?")) + ")";
        List<Object[]> batch = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            Object[] args = new Object[header.size()];
            for (int c = 0; c < header.size(); c++) {
                String v = c < row.size() ? row.get(c) : "";
                args[c] = v.isEmpty() ? null : convert(header.get(c), v);
            }
            batch.add(args);
        }
        jdbc.batchUpdate(sql, batch);
        return batch.size();
    }

    /** Date-time columns are ISO strings in the CSV; everything else is passed as a string and cast by the driver. */
    private static Object convert(String column, String v) {
        if (column.endsWith("_at")) {
            return java.sql.Timestamp.valueOf(java.time.LocalDateTime.parse(v));
        }
        return v;
    }
}
