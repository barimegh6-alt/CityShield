package com.cityshield.repository;

import com.cityshield.entity.*;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceAllocationRepository extends JpaRepository<ResourceAllocation, Long> {
    List<ResourceAllocation> findAllByOrderByCreatedAtDescAllocationIdDesc();
}
