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
 * CLAUDE.md E.2 — "tài xế hủy giữa chừng khi đã ở trạng thái IN_PROGRESS... cần quy
 * trình đặc biệt: tài xế phải đưa xe + khách đến nơi an toàn trước khi được phép kết
 * thúc bất thường, có thể cần điều phối tài xế thay thế đến tiếp ứng tại chỗ". A flat
 * append-only record — same shape and reasoning as {@link SosAlert}/{@code RouteDeviationFlag}:
 * the moment it matters is the moment it's raised, and what CSKH needs (the safe
 * drop-off point, the driver's note, both profiles, the trip's full context) is
 * pulled from here, not inferred from a state machine. Whether a replacement driver
 * gets sent to {@code safeLocation} is a judgment call CSKH makes per case — never
 * automatic, since the situation that produced this report is by definition unusual.
 */
@Entity
@Table(name = "emergency_abort_reports")
@Getter
@NoArgsConstructor(force = true)
public class EmergencyAbortReport {

    @Id
    @GeneratedValue
    private UUID id;

    private UUID tripId;

    private UUID driverId;

    private UUID customerId;

    private double safeLocationLatitude;

    private double safeLocationLongitude;

    private String note;

    private Instant reportedAt;

    public EmergencyAbortReport(UUID tripId, UUID driverId, UUID customerId,
                                double safeLocationLatitude, double safeLocationLongitude,
                                String note, Instant reportedAt) {
        this.tripId = tripId;
        this.driverId = driverId;
        this.customerId = customerId;
        this.safeLocationLatitude = safeLocationLatitude;
        this.safeLocationLongitude = safeLocationLongitude;
        this.note = note;
        this.reportedAt = reportedAt;
    }
}
