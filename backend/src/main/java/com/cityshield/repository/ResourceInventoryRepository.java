package com.cityshield.repository;

import com.cityshield.entity.*;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResourceInventoryRepository extends JpaRepository<ResourceInventory, Long> {
    List<ResourceInventory> findByResourceTypeId(Long id);
    ResourceInventory findByZoneIdAndResourceTypeId(Long zoneId, Long typeId);
}
