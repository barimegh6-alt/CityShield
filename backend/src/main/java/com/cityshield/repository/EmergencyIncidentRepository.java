package com.cityshield.repository;

import com.cityshield.entity.*;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmergencyIncidentRepository extends JpaRepository<EmergencyIncident, Long> {
    List<EmergencyIncident> findByStatusIn(Collection<String> statuses);
    long countByStatusIn(Collection<String> statuses);
    long countByStatus(String status);
    boolean existsByIncidentCode(String code);
}
