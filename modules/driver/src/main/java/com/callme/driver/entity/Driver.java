package com.callme.driver.entity;

import com.callme.common.exception.ConflictException;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "drivers", indexes = {
        // CLAUDE.md G.4 — backs DriverRepository's bounding-box pre-filter: leads
        // with the columns matching always filters on first (online, on_trip), so the
        // database can skip straight to the geographic range scan instead of touching
        // every driver row in existence.
        @Index(name = "idx_drivers_availability_location",
                columnList = "online, on_trip, last_known_latitude, last_known_longitude")
})
@Getter
@NoArgsConstructor(force = true)
public class Driver {

    @Id
    @GeneratedValue
    private UUID id;

    private String displayName;

    private boolean online;

    /**
     * CLAUDE.md line 59 — a driver currently on a trip must not be matched to a new
     * booking. Tracked here (rather than derived by querying trip) so the
     * `DriverAvailabilityPort` query stays a single-table read.
     */
    private boolean onTrip;

    private double lastKnownLatitude;

    private double lastKnownLongitude;

    /**
     * CLAUDE.md B.2 — "tài xế ảo": `online = true` in the DB but the device lost
     * connection / stopped reporting GPS. Without this timestamp, matching would
     * happily dispatch a customer to someone who isn't actually reachable. Null
     * until the first location report arrives after going online.
     */
    private Instant lastLocationUpdatedAt;

    /**
     * CLAUDE.md B.3 — count of times this driver was matched/reserved but went dark
     * instead of heading to pickup (detected by the trip module's sweep). Feeds
     * {@code DriverMatchingPort}'s candidate ordering — "hạ điểm ưu tiên hiển thị của
     * tài xế đó cho các lần sau" — so a chronically unresponsive driver sinks to the
     * back of the queue rather than being matched again at the customer's expense.
     */
    private int noResponseStrikes;

    /**
     * CLAUDE.md §4.5 — "lý lịch tư pháp hợp lệ" is the first of three conditions gating
     * {@link #goOnline}. A new driver starts {@link BackgroundCheckStatus#PENDING} —
     * not eligible — until an admin records the result via {@link #recordVerification}.
     */
    @Enumerated(EnumType.STRING)
    private BackgroundCheckStatus backgroundCheckStatus;

    /** CLAUDE.md §4.5 — "bằng lái phù hợp loại xe sẽ lái... còn hạn". Null until first verified. */
    private LocalDate licenseExpiryDate;

    /** CLAUDE.md §4.2 — "bảo hiểm trách nhiệm còn hiệu lực" must be verified before a driver can go online. Null until first verified. */
    private LocalDate insuranceValidUntil;

    /** CLAUDE.md §4.5 — "tái xác minh định kỳ (6-12 tháng)". Null until the first verification. */
    private Instant lastReverificationAt;

    /** Optimistic lock — guards against two concurrent bookings assigning the same driver (CLAUDE.md B.1). */
    @Version
    private long version;

    /**
     * CLAUDE.md §4.5 — "tái xác minh định kỳ (6-12 tháng)". The lower bound of that range
     * is chosen for a high-risk domain where drivers operate unfamiliar vehicles
     * (CLAUDE.md §1) — more frequent re-checks beat the upper bound's lighter load.
     */
    public static final Duration REVERIFICATION_INTERVAL = Duration.ofDays(180);

    public Driver(String displayName) {
        this.displayName = displayName;
        this.online = false;
        this.onTrip = false;
        this.backgroundCheckStatus = BackgroundCheckStatus.PENDING;
    }

    /**
     * CLAUDE.md §4.5 invariant — "Driver không thể chuyển online = true nếu bất kỳ xác
     * minh nào đã hết hạn". Throws {@link IllegalStateException} (mapped to 409) listing
     * every unmet condition, rather than silently refusing, so the driver/admin knows
     * exactly what to fix.
     */
    public void goOnline(Instant now) {
        var reasons = ineligibilityReasons(now);
        if (!reasons.isEmpty()) {
            throw new IllegalStateException("Tài xế chưa đủ điều kiện hoạt động: " + String.join("; ", reasons));
        }
        this.online = true;
    }

    /** CLAUDE.md §4.5/§4.2 — true only when background check, license, insurance and reverification are all current. */
    public boolean isEligibleToGoOnline(Instant now) {
        return ineligibilityReasons(now).isEmpty();
    }

    /**
     * The unmet §4.5/§4.2 conditions, in human-readable form — empty when fully
     * eligible. Public so the expiry sweep can tell the driver WHY they were forced
     * offline (the same list {@link #goOnline} would refuse them with).
     */
    public List<String> ineligibilityReasons(Instant now) {
        var today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        var reasons = new ArrayList<String>();
        if (this.backgroundCheckStatus != BackgroundCheckStatus.APPROVED) {
            reasons.add("lý lịch tư pháp chưa được duyệt");
        }
        if (this.licenseExpiryDate == null || this.licenseExpiryDate.isBefore(today)) {
            reasons.add("bằng lái đã hết hạn hoặc chưa xác minh");
        }
        if (this.insuranceValidUntil == null || this.insuranceValidUntil.isBefore(today)) {
            reasons.add("bảo hiểm trách nhiệm đã hết hạn hoặc chưa xác minh");
        }
        if (this.lastReverificationAt == null || this.lastReverificationAt.isBefore(now.minus(REVERIFICATION_INTERVAL))) {
            reasons.add("đã quá hạn tái xác minh định kỳ");
        }
        return reasons;
    }

    /**
     * CLAUDE.md §4.5 — records the outcome of a periodic reverification round (background
     * check, license, insurance all checked together, as a real reverification round
     * would). Resets {@link #lastReverificationAt} to {@code now}, restarting the
     * {@link #REVERIFICATION_INTERVAL} countdown.
     */
    public void recordVerification(BackgroundCheckStatus backgroundCheckStatus, LocalDate licenseExpiryDate, LocalDate insuranceValidUntil, Instant now) {
        this.backgroundCheckStatus = backgroundCheckStatus;
        this.licenseExpiryDate = licenseExpiryDate;
        this.insuranceValidUntil = insuranceValidUntil;
        this.lastReverificationAt = now;
    }

    public void goOffline() {
        this.online = false;
    }

    public void updateLocation(double latitude, double longitude, Instant reportedAt) {
        this.lastKnownLatitude = latitude;
        this.lastKnownLongitude = longitude;
        this.lastLocationUpdatedAt = reportedAt;
    }

    /** True when this driver's last GPS fix is fresh enough to trust for matching (CLAUDE.md B.2). */
    public boolean hasFreshLocation(Instant now, Duration maxAge) {
        return this.lastLocationUpdatedAt != null
                && !this.lastLocationUpdatedAt.isBefore(now.minus(maxAge));
    }

    /** Reserves this driver for a trip — fails fast if a concurrent assignment already claimed them (CLAUDE.md B.1). */
    public void beginTrip() {
        if (this.onTrip) {
            throw new ConflictException("Tài xế đang trong một chuyến đi khác: " + this.id);
        }
        this.onTrip = true;
    }

    /** Releases this driver back into the matching pool once their trip ends (completed or cancelled). */
    public void endTrip() {
        this.onTrip = false;
    }

    /** CLAUDE.md B.3 — records that this driver was matched but went unresponsive; lowers their future matching priority. */
    public void recordNoResponseStrike() {
        this.noResponseStrikes++;
    }
}
