package com.callme.trip.repository;

import com.callme.trip.entity.RouteDeviationFlag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RouteDeviationFlagRepository extends JpaRepository<RouteDeviationFlag, UUID> {

    List<RouteDeviationFlag> findAllByOrderByFlaggedAtDesc();
}
