package com.cityshield.repository;

import com.cityshield.entity.*;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlgorithmExecutionLogRepository extends JpaRepository<AlgorithmExecutionLog, Long> {
    List<AlgorithmExecutionLog> findByAlgorithmOrderByRunAtDesc(String a);
    List<AlgorithmExecutionLog> findAllByOrderByRunAtDesc();
}
