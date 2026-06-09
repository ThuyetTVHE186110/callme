package com.callme.location.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "location_updates")
@Getter
@NoArgsConstructor(force = true)
public class LocationUpdate {

    @Id
    @GeneratedValue
    private UUID id;

    private UUID driverId;

    private double latitude;

    private double longitude;

    private Instant recordedAt;

    public LocationUpdate(UUID driverId, double latitude, double longitude) {
        this.driverId = driverId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.recordedAt = Instant.now();
    }
}
