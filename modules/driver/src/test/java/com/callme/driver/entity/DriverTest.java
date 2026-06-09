package com.callme.driver.entity;

import com.callme.common.exception.ConflictException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CLAUDE.md B.1 (race-safe reservation), B.2 ("tài xế ảo" / stale-location detection)
 * and B.3 (no-response strikes) — pure entity-level invariants, no Spring context needed.
 */
class DriverTest {

    private static final Instant NOW = Instant.parse("2026-06-07T15:00:00Z");

    private Driver newDriver() {
        var driver = new Driver("Nguyễn Văn A");
        driver.goOnline();
        return driver;
    }

    @Nested
    class Reservation {

        @Test
        void claimsAFreeDriverForATrip() {
            var driver = newDriver();
            driver.beginTrip();
            assertThat(driver.isOnTrip()).isTrue();
        }

        @Test
        void refusesToClaimADriverAlreadyOnATrip() {
            var driver = newDriver();
            driver.beginTrip();
            assertThatThrownBy(driver::beginTrip).isInstanceOf(ConflictException.class);
        }

        @Test
        void releasesTheDriverBackIntoThePoolWhenTheirTripEnds() {
            var driver = newDriver();
            driver.beginTrip();
            driver.endTrip();
            assertThat(driver.isOnTrip()).isFalse();
            driver.beginTrip();
            assertThat(driver.isOnTrip()).isTrue();
        }
    }

    /** CLAUDE.md B.2 — "tài xế ảo": online in the DB but the device stopped reporting GPS. */
    @Nested
    class LocationFreshness {

        private static final Duration MAX_AGE = Duration.ofMinutes(5);

        @Test
        void hasNoFreshLocationBeforeTheFirstReport() {
            var driver = newDriver();
            assertThat(driver.hasFreshLocation(NOW, MAX_AGE)).isFalse();
        }

        @Test
        void treatsARecentFixAsFresh() {
            var driver = newDriver();
            driver.updateLocation(10.0, 106.0, NOW);
            assertThat(driver.hasFreshLocation(NOW.plus(MAX_AGE).minusSeconds(1), MAX_AGE)).isTrue();
        }

        @Test
        void treatsAStaleFixAsNotFresh() {
            var driver = newDriver();
            driver.updateLocation(10.0, 106.0, NOW);
            assertThat(driver.hasFreshLocation(NOW.plus(MAX_AGE).plusSeconds(1), MAX_AGE)).isFalse();
        }
    }

    /** CLAUDE.md B.3 — a matched driver who goes dark sinks in future matching priority instead of being silently re-offered. */
    @Nested
    class NoResponseStrikes {

        @Test
        void startsWithNoStrikes() {
            assertThat(newDriver().getNoResponseStrikes()).isZero();
        }

        @Test
        void accumulatesAStrikePerUnresponsiveMatch() {
            var driver = newDriver();
            driver.recordNoResponseStrike();
            driver.recordNoResponseStrike();
            assertThat(driver.getNoResponseStrikes()).isEqualTo(2);
        }
    }
}
