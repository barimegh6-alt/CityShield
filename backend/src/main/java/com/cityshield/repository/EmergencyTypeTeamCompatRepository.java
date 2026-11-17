package com.cityshield.repository;

import com.cityshield.entity.*;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmergencyTypeTeamCompatRepository extends JpaRepository<EmergencyTypeTeamCompat, Long> {
    List<EmergencyTypeTeamCompat> findByTypeId(Long id);
}
