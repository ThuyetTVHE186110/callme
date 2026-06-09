package com.callme.pricing.service.impl;

import com.callme.common.port.FareEstimationPort;
import com.callme.common.port.dto.FareBreakdown;
import com.callme.common.port.dto.FareQuote;
import com.callme.common.shared.GeoPoint;
import com.callme.common.shared.Money;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Implements the cross-module contract published in `common`. Owns the quoting
 * strategy as its own bounded context — booking and trip ask "how much", never
 * "how do you compute it".
 *
 * Cước = cước cơ bản + đơn giá/km × quãng đường, cộng thêm phụ phí "giờ khuya" và
 * "khu vực xa" — đúng những gì CLAUDE.md §4.4 chốt cho MVP (phí "chờ khách" là phần
 * mở rộng tiếp theo, cần Trip ghi nhận mốc thời gian đến nơi vs. đón thực tế trước).
 * Vehicle-type multipliers remain a natural extension point once that data exists.
 */
@Service
public class FareEstimationPortImpl implements FareEstimationPort {

    private static final BigDecimal BASE_FARE_VND = BigDecimal.valueOf(30_000);
    private static final BigDecimal PER_KM_RATE_VND = BigDecimal.valueOf(12_000);
    private static final double EARTH_RADIUS_KM = 6371.0;

    /** CLAUDE.md §4.4 — "phụ phí giờ khuya": +20% trên tổng cước cơ bản + quãng đường + khu vực xa. */
    private static final BigDecimal NIGHT_SURCHARGE_RATE = BigDecimal.valueOf(0.20);
    private static final int NIGHT_START_HOUR = 22;
    private static final int NIGHT_END_HOUR = 6;
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /** CLAUDE.md §4.4 — "phụ phí khu vực xa": phần quãng đường vượt ngưỡng chịu đơn giá cao hơn. */
    private static final double REMOTE_AREA_THRESHOLD_KM = 20.0;
    private static final BigDecimal REMOTE_AREA_EXTRA_PER_KM_VND = BigDecimal.valueOf(5_000);

    @Override
    public FareQuote estimate(GeoPoint pickup, GeoPoint destination, Instant atTime) {
        double distanceKm = haversineKm(pickup, destination);

        BigDecimal baseFare = BASE_FARE_VND;
        BigDecimal distanceCharge = round(PER_KM_RATE_VND.multiply(BigDecimal.valueOf(distanceKm)));
        BigDecimal remoteAreaSurcharge = remoteAreaSurcharge(distanceKm);

        BigDecimal subtotal = baseFare.add(distanceCharge).add(remoteAreaSurcharge);
        BigDecimal nightSurcharge = isNightTime(atTime) ? round(subtotal.multiply(NIGHT_SURCHARGE_RATE)) : BigDecimal.ZERO;
        BigDecimal total = subtotal.add(nightSurcharge);

        var breakdown = new FareBreakdown(
                Money.vnd(baseFare),
                Money.vnd(distanceCharge),
                Money.vnd(nightSurcharge),
                Money.vnd(remoteAreaSurcharge),
                Money.vnd(total));
        return new FareQuote(Money.vnd(total), distanceKm, breakdown);
    }

    private BigDecimal remoteAreaSurcharge(double distanceKm) {
        if (distanceKm <= REMOTE_AREA_THRESHOLD_KM) {
            return BigDecimal.ZERO;
        }
        double extraKm = distanceKm - REMOTE_AREA_THRESHOLD_KM;
        return round(REMOTE_AREA_EXTRA_PER_KM_VND.multiply(BigDecimal.valueOf(extraKm)));
    }

    /** Khung giờ đêm 22:00–06:00 vắt qua nửa đêm — so sánh "hoặc muộn hoặc sớm" thay vì một khoảng liên tục. */
    private boolean isNightTime(Instant atTime) {
        int hour = ZonedDateTime.ofInstant(atTime, VIETNAM_ZONE).getHour();
        return hour >= NIGHT_START_HOUR || hour < NIGHT_END_HOUR;
    }

    private BigDecimal round(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP);
    }

    private double haversineKm(GeoPoint from, GeoPoint to) {
        double dLat = Math.toRadians(to.latitude() - from.latitude());
        double dLng = Math.toRadians(to.longitude() - from.longitude());
        double lat1 = Math.toRadians(from.latitude());
        double lat2 = Math.toRadians(to.latitude());

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.sin(dLng / 2) * Math.sin(dLng / 2) * Math.cos(lat1) * Math.cos(lat2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }
}
