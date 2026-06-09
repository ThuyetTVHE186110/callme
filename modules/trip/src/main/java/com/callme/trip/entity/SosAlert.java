package com.callme.trip.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * CLAUDE.md C.6 — an emergency raised by a trip participant (khách bị quấy rối,
 * tài xế gặp nguy hiểm...). Deliberately a flat append-only record, not a state
 * machine: the only thing that matters in the moment is that it exists, who raised
 * it, and when — support pulls the trip's full context (location trail, both
 * profiles) from there. Independent of {@link Trip}'s own lifecycle — an emergency
 * can happen whether the trip is STARTED, IN_PROGRESS, or even moments from ending.
 */
@Entity
@Table(name = "sos_alerts")
@Getter
@NoArgsConstructor(force = true)
public class SosAlert {

    @Id
    @GeneratedValue
    private UUID id;

    private UUID tripId;

    private UUID raisedByProfileId;

    private String note;

    private Instant raisedAt;

    public SosAlert(UUID tripId, UUID raisedByProfileId, String note, Instant raisedAt) {
        this.tripId = tripId;
        this.raisedByProfileId = raisedByProfileId;
        this.note = note;
        this.raisedAt = raisedAt;
    }
}
