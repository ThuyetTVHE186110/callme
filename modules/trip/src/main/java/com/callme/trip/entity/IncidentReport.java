package com.callme.trip.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * CLAUDE.md §4.2 — "tai nạn / va chạm trong khi tài xế cầm lái xe khách". A flat,
 * append-only record of an incident report, mirroring {@link EmergencyAbortReport} /
 * {@link SosAlert} / {@link RouteDeviationFlag}: anyone involved can raise one, and
 * CSKH works through {@link IncidentInvestigationStatus#REPORTED} reports at their
 * own pace, recording the eventual liability finding via {@link #resolve}.
 */
@Entity
@Table(name = "incident_reports")
@Getter
@NoArgsConstructor(force = true)
public class IncidentReport {

    @Id
    @GeneratedValue
    private UUID id;

    private UUID tripId;
    private UUID reportedByProfileId;
    private UUID customerId;
    private UUID driverId;
    private String description;

    @Enumerated(EnumType.STRING)
    private IncidentInvestigationStatus investigationStatus;

    private Instant reportedAt;

    private String resolutionNote;
    private Instant resolvedAt;

    public IncidentReport(UUID tripId, UUID reportedByProfileId, UUID customerId, UUID driverId,
                           String description, Instant reportedAt) {
        this.tripId = tripId;
        this.reportedByProfileId = reportedByProfileId;
        this.customerId = customerId;
        this.driverId = driverId;
        this.description = description;
        this.investigationStatus = IncidentInvestigationStatus.REPORTED;
        this.reportedAt = reportedAt;
    }

    /**
     * CSKH records the outcome of their investigation. Only a {@code RESOLVED_*}
     * status is a valid resolution — {@link IncidentInvestigationStatus#REPORTED} and
     * {@link IncidentInvestigationStatus#UNDER_INVESTIGATION} describe an open report,
     * not a conclusion.
     */
    public void resolve(IncidentInvestigationStatus status, String resolutionNote, Instant now) {
        if (status != IncidentInvestigationStatus.RESOLVED_NO_FAULT
                && status != IncidentInvestigationStatus.RESOLVED_DRIVER_FAULT
                && status != IncidentInvestigationStatus.RESOLVED_COMPANY_LIABLE) {
            throw new IllegalArgumentException("Trạng thái giải quyết không hợp lệ: " + status);
        }
        this.investigationStatus = status;
        this.resolutionNote = resolutionNote;
        this.resolvedAt = now;
    }
}
