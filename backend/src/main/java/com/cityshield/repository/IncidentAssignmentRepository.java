package com.cityshield.repository;

import com.cityshield.entity.*;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentAssignmentRepository extends JpaRepository<IncidentAssignment, Long> {
    List<IncidentAssignment> findByStatus(String status);
    List<IncidentAssignment> findByIncidentId(Long id);
    List<IncidentAssignment> findByIncidentIdAndStatus(Long id, String status);
}
