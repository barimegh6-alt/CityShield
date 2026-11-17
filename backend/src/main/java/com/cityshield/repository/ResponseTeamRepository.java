package com.cityshield.repository;

import com.cityshield.entity.*;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResponseTeamRepository extends JpaRepository<ResponseTeam, Long> {
    List<ResponseTeam> findByAvailability(String availability);
}
