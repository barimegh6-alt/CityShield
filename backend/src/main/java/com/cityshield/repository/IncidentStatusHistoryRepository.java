package com.cityshield.repository;

import com.cityshield.entity.*;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentStatusHistoryRepository extends JpaRepository<IncidentStatusHistory, Long> {
    List<IncidentStatusHistory> findByIncidentIdOrderByChangedAtAsc(Long id);
}
