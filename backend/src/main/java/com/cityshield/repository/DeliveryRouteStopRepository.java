package com.cityshield.repository;

import com.cityshield.entity.*;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryRouteStopRepository extends JpaRepository<DeliveryRouteStop, Long> {
    List<DeliveryRouteStop> findByRouteIdOrderByStopOrderAsc(Long id);
}
