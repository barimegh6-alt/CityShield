package com.cityshield.repository;

import com.cityshield.entity.*;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentResourceRequirementRepository extends JpaRepository<IncidentResourceRequirement, Long> {
    List<IncidentResourceRequirement> findByIncidentIdIn(Collection<Long> ids);
    List<IncidentResourceRequirement> findByIncidentId(Long id);
    void deleteByIncidentId(Long id);
}
