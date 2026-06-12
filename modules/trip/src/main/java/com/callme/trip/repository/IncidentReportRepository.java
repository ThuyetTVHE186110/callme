package com.callme.trip.repository;

import com.callme.trip.entity.IncidentReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IncidentReportRepository extends JpaRepository<IncidentReport, UUID> {
    List<IncidentReport> findAllByOrderByReportedAtDesc();
}
