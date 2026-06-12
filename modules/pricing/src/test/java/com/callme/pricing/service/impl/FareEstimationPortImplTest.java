package com.callme.pricing.service.impl;

import com.callme.common.port.dto.FareTimeBand;
import com.callme.common.shared.GeoPoint;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CLAUDE.md §4.4 — verifies the time-banded base fare model (mirrors 滴滴代驾/e代驾:
 * a higher all-inclusive starting price + fewer included km later at night, instead
 * of a flat base fare with a separate night-time multiplier), the remote-area
 * surcharge that layers on top of it, and "phí chờ khách" (free first 10 minutes at
 * pickup, then a flat per-minute fee, capped).
 */
class FareEstimationPortImplTest {

    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final FareEstimationPortImpl service = new FareEstimationPortImpl();

    private static final GeoPoint PICKUP = new GeoPoint(10.7769, 106.7009);
    /** ~10km due north of PICKUP — past DAY/EVENING/LATE_NIGHT's 8km included distance. */
    private static final GeoPoint DESTINATION_10KM = new GeoPoint(10.8669, 106.7009);
    /** ~25km due north — past the 20km remote-area threshold. */
    private static final GeoPoint DESTINATION_25KM = new GeoPoint(11.0019, 106.7009);

    private Instant atHour(int hour) {
        return ZonedDateTime.of(2026, 6, 10, hour, 0, 0, 0, VIETNAM_ZONE).toInstant();
    }

    @Test
    void dayBandChargesTheBaseFarePlusDistanceBeyondTheIncludedKm() {
        var quote = service.estimate(PICKUP, DESTINATION_10KM, atHour(12), Duration.ZERO);
        var breakdown = quote.breakdown();

        assertThat(breakdown.timeBand()).isEqualTo(FareTimeBand.DAY);
        assertThat(breakdown.includedKm()).isEqualTo(8);
        assertThat(breakdown.baseFare().amount()).isEqualByComparingTo(BigDecimal.valueOf(80_000));

        double billableKm = Math.max(0, quote.distanceKm() - 8);
        var expectedDistanceCharge = BigDecimal.valueOf(12_000).multiply(BigDecimal.valueOf(billableKm))
                .setScale(0, RoundingMode.HALF_UP);
        assertThat(breakdown.distanceCharge().amount()).isEqualByComparingTo(expectedDistanceCharge);
        assertThat(breakdown.remoteAreaSurcharge().amount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(breakdown.waitingFee().amount()).isEqualByComparingTo(BigDecimal.ZERO);

        var expectedTotal = BigDecimal.valueOf(80_000).add(expectedDistanceCharge);
        assertThat(breakdown.total().amount()).isEqualByComparingTo(expectedTotal);
        assertThat(quote.amount().amount()).isEqualByComparingTo(expectedTotal);
    }

    @Test
    void overnightBandHasAHigherBaseFareAndFewerIncludedKmThanDay() {
        var dayQuote = service.estimate(PICKUP, DESTINATION_10KM, atHour(12), Duration.ZERO);
        var overnightQuote = service.estimate(PICKUP, DESTINATION_10KM, atHour(2), Duration.ZERO);

        assertThat(overnightQuote.breakdown().timeBand()).isEqualTo(FareTimeBand.OVERNIGHT);
        assertThat(overnightQuote.breakdown().includedKm()).isEqualTo(6);
        assertThat(overnightQuote.breakdown().baseFare().amount()).isEqualByComparingTo(BigDecimal.valueOf(160_000));

        // Same trip, but the deeper-night band's higher base fare and smaller included
        // distance both push the total up — this is the "night surcharge" now.
        assertThat(overnightQuote.amount().amount()).isGreaterThan(dayQuote.amount().amount());
    }

    @Test
    void distanceBeyondTheRemoteAreaThresholdAddsASurchargeOnTopOfTheBand() {
        var quote = service.estimate(PICKUP, DESTINATION_25KM, atHour(12), Duration.ZERO);
        var breakdown = quote.breakdown();

        assertThat(quote.distanceKm()).isGreaterThan(20.0);
        double extraKm = quote.distanceKm() - 20.0;
        var expectedRemoteSurcharge = BigDecimal.valueOf(5_000).multiply(BigDecimal.valueOf(extraKm))
                .setScale(0, RoundingMode.HALF_UP);

        assertThat(breakdown.remoteAreaSurcharge().amount()).isEqualByComparingTo(expectedRemoteSurcharge);
        assertThat(breakdown.remoteAreaSurcharge().amount()).isGreaterThan(BigDecimal.ZERO);

        var expectedTotal = breakdown.baseFare().amount()
                .add(breakdown.distanceCharge().amount())
                .add(expectedRemoteSurcharge)
                .add(breakdown.waitingFee().amount());
        assertThat(breakdown.total().amount()).isEqualByComparingTo(expectedTotal);
    }

    @Test
    void theFourTimeBandsCoverAllHoursOfTheDayWithoutGaps() {
        assertThat(timeBandAt(6)).isEqualTo(FareTimeBand.DAY);
        assertThat(timeBandAt(18)).isEqualTo(FareTimeBand.DAY);
        assertThat(timeBandAt(19)).isEqualTo(FareTimeBand.EVENING);
        assertThat(timeBandAt(22)).isEqualTo(FareTimeBand.EVENING);
        assertThat(timeBandAt(23)).isEqualTo(FareTimeBand.LATE_NIGHT);
        assertThat(timeBandAt(0)).isEqualTo(FareTimeBand.OVERNIGHT);
        assertThat(timeBandAt(5)).isEqualTo(FareTimeBand.OVERNIGHT);
    }

    private FareTimeBand timeBandAt(int hour) {
        return service.estimate(PICKUP, PICKUP, atHour(hour), Duration.ZERO).breakdown().timeBand();
    }

    @Test
    void aZeroDistanceTripIsJustTheBandsBaseFare() {
        var quote = service.estimate(PICKUP, PICKUP, atHour(12), Duration.ZERO);

        assertThat(quote.distanceKm()).isEqualTo(0.0);
        assertThat(quote.breakdown().distanceCharge().amount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(quote.breakdown().remoteAreaSurcharge().amount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(quote.breakdown().waitingFee().amount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(quote.amount().amount()).isEqualByComparingTo(BigDecimal.valueOf(80_000));
    }

    @Test
    void waitingUpToTenMinutesIsFree() {
        var quote = service.estimate(PICKUP, PICKUP, atHour(12), Duration.ofMinutes(10));

        assertThat(quote.breakdown().waitingFee().amount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(quote.amount().amount()).isEqualByComparingTo(BigDecimal.valueOf(80_000));
    }

    @Test
    void waitingBeyondTenMinutesIsChargedThreeThousandVndPerExtraMinute() {
        var quote = service.estimate(PICKUP, PICKUP, atHour(12), Duration.ofMinutes(25));
        var breakdown = quote.breakdown();

        // 25 min waited - 10 min free = 15 chargeable minutes.
        var expectedWaitingFee = BigDecimal.valueOf(3_000).multiply(BigDecimal.valueOf(15));
        assertThat(breakdown.waitingFee().amount()).isEqualByComparingTo(expectedWaitingFee);

        var expectedTotal = breakdown.baseFare().amount().add(expectedWaitingFee);
        assertThat(breakdown.total().amount()).isEqualByComparingTo(expectedTotal);
        assertThat(quote.amount().amount()).isEqualByComparingTo(expectedTotal);
    }

    @Test
    void waitingFeeIsCappedAtOneHundredEightyChargeableMinutes() {
        var atCap = service.estimate(PICKUP, PICKUP, atHour(12), Duration.ofMinutes(10 + 180));
        var beyondCap = service.estimate(PICKUP, PICKUP, atHour(12), Duration.ofMinutes(10 + 180 + 60));

        var expectedCappedFee = BigDecimal.valueOf(3_000).multiply(BigDecimal.valueOf(180));
        assertThat(atCap.breakdown().waitingFee().amount()).isEqualByComparingTo(expectedCappedFee);
        assertThat(beyondCap.breakdown().waitingFee().amount()).isEqualByComparingTo(expectedCappedFee);
    }
}
