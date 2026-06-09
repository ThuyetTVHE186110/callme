package com.callme.driver.entity;

import com.callme.common.exception.ConflictException;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
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

    /** Optimistic lock — guards against two concurrent bookings assigning the same driver (CLAUDE.md B.1). */
    @Version
    private long version;

    public Driver(String displayName) {
        this.displayName = displayName;
        this.online = false;
        this.onTrip = false;
    }

    public void goOnline() {
        this.online = true;
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
