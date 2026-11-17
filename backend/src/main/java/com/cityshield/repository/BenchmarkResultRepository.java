package com.cityshield.repository;

import com.cityshield.entity.*;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BenchmarkResultRepository extends JpaRepository<BenchmarkResult, Long> {
    List<BenchmarkResult> findByAlgorithmOrderByInputSizeAsc(String a);
    void deleteByAlgorithm(String a);
}
