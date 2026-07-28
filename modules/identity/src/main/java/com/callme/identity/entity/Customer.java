package com.callme.identity.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customers")
@Getter
@NoArgsConstructor(force = true)
public class Customer {

    @Id
    @GeneratedValue
    private UUID id;

    private String fullName;

    private String phoneNumber;

    private String email;

    private double lastKnownLatitude;

    private double lastKnownLongitude;

    /**
     * Lets the assigned driver see where the customer currently is (e.g. while
     * waiting to be picked up). Null until the first location report — customers
     * don't push GPS as a rule (their phone stays in their pocket, unlike a driver's
     * mounted device), so this is expected to stay empty for most trips.
     */
    private Instant lastLocationUpdatedAt;

    public Customer(String fullName, String phoneNumber, String email) {
        this.fullName = fullName;
        this.phoneNumber = phoneNumber;
        this.email = email;
    }

    public void updateProfile(String fullName, String email) {
        this.fullName = fullName;
        this.email = email;
    }

    public void updateLocation(double latitude, double longitude, Instant reportedAt) {
        this.lastKnownLatitude = latitude;
        this.lastKnownLongitude = longitude;
        this.lastLocationUpdatedAt = reportedAt;
    }
}
