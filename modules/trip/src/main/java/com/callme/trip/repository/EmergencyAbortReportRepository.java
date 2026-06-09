package com.callme.trip.repository;

import com.callme.trip.entity.EmergencyAbortReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EmergencyAbortReportRepository extends JpaRepository<EmergencyAbortReport, UUID> {

    List<EmergencyAbortReport> findAllByOrderByReportedAtDesc();
}
