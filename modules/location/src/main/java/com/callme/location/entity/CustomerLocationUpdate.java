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
@Table(name = "customer_location_updates")
@Getter
@NoArgsConstructor(force = true)
public class CustomerLocationUpdate {

    @Id
    @GeneratedValue
    private UUID id;

    private UUID customerId;

    private double latitude;

    private double longitude;

    private Instant recordedAt;

    public CustomerLocationUpdate(UUID customerId, double latitude, double longitude) {
        this.customerId = customerId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.recordedAt = Instant.now();
    }
}
