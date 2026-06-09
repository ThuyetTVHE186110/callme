package com.callme.trip.repository;

import com.callme.trip.entity.SosAlert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SosAlertRepository extends JpaRepository<SosAlert, UUID> {

    List<SosAlert> findAllByOrderByRaisedAtDesc();

    List<SosAlert> findByTripIdOrderByRaisedAtDesc(UUID tripId);
}
