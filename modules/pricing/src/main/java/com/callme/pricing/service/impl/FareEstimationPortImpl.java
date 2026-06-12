package com.callme.pricing.service.impl;

import com.callme.common.port.FareEstimationPort;
import com.callme.common.port.dto.FareBreakdown;
import com.callme.common.port.dto.FareQuote;
import com.callme.common.port.dto.FareTimeBand;
import com.callme.common.shared.GeoPoint;
import com.callme.common.shared.Money;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Implements the cross-module contract published in `common`. Owns the quoting
 * strategy as its own bounded context — booking and trip ask "how much", never
 * "how do you compute it".
 *
 * Cước = cước cơ bản theo khung giờ (đã bao gồm sẵn N km đầu) + đơn giá/km cho phần
 * vượt + phụ phí "khu vực xa" + phí chờ khách — mô hình lấy theo các nền tảng đặt
 * tài xế (代驾) Trung Quốc (滴滴代驾/e代驾: giá khởi điểm theo khung giờ, mỗi khung gồm
 * sẵn vài km đầu, phần vượt tính thêm theo km; miễn phí chờ 10 phút đầu tại điểm đón,
 * sau đó tính thêm theo phút, có giới hạn trần), thay cho mô hình cũ "cước cơ bản cố
 * định + hệ số nhân 20% giờ khuya".
 */
@Service
public class FareEstimationPortImpl implements FareEstimationPort {

    private static final BigDecimal PER_KM_RATE_VND = BigDecimal.valueOf(12_000);
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /** One time band's all-inclusive starting price and the distance it already covers. */
    private record TimeBandRate(BigDecimal baseFareVnd, int includedKm) {
    }

    /**
     * CLAUDE.md §4.4 — bốn khung giờ liền kề phủ kín 24h, mỗi khung một giá khởi
     * điểm + số km đã bao gồm (mirrors 滴滴代驾: 30/40/55/70 NDT cho 8 km theo
     * 4 khung giờ; e代驾: khung 00:00-06:59 giảm km bao gồm xuống 6). Khung càng
     * khuya, giá khởi điểm càng cao và số km bao gồm càng ít — phản ánh chi phí cơ
     * hội của tài xế (ít cuốc, khó tìm phương tiện tự túc về) cao hơn vào giờ sâu.
     */
    private static final TimeBandRate DAY_RATE = new TimeBandRate(BigDecimal.valueOf(80_000), 8);
    private static final TimeBandRate EVENING_RATE = new TimeBandRate(BigDecimal.valueOf(100_000), 8);
    private static final TimeBandRate LATE_NIGHT_RATE = new TimeBandRate(BigDecimal.valueOf(130_000), 8);
    private static final TimeBandRate OVERNIGHT_RATE = new TimeBandRate(BigDecimal.valueOf(160_000), 6);

    /** CLAUDE.md §4.4 — "phụ phí khu vực xa": phần quãng đường vượt ngưỡng chịu thêm đơn giá. */
    private static final double REMOTE_AREA_THRESHOLD_KM = 20.0;
    private static final BigDecimal REMOTE_AREA_EXTRA_PER_KM_VND = BigDecimal.valueOf(5_000);

    /**
     * CLAUDE.md §4.4 — "phí chờ khách": miễn phí chờ tại điểm đón trong những phút
     * đầu (mirrors 代驾 chuẩn Trung Quốc — 滴滴代驾/e代驾 đều miễn phí 10 phút đầu),
     * khớp với {@code TripServiceImpl.NO_SHOW_GRACE_PERIOD} — cùng là "khoảng thời
     * gian khách được nợ tài xế trước khi phát sinh hệ quả" (ở đây là phí, ở đó là
     * quyền huỷ no-show), nên hai ngưỡng được giữ bằng nhau dù định nghĩa ở hai module.
     */
    private static final long FREE_WAITING_MINUTES = 10;

    /** Đơn giá phút chờ tính phí — quy đổi tương đương 1 NDT/phút của 代驾 Trung Quốc sang VND. */
    private static final BigDecimal WAITING_FEE_PER_MINUTE_VND = BigDecimal.valueOf(3_000);

    /**
     * CLAUDE.md §4.4 — trần số phút chờ tính phí, mirrors 滴滴代驾 (giới hạn tổng phí
     * chờ ở mức tương đương 180 phút tính phí). Vượt ngưỡng này là vấn đề vận hành
     * (no-show, sự cố) chứ không còn là "chờ" thông thường nữa — CLAUDE.md C.1 đã có
     * `cancelNoShow` xử lý riêng.
     */
    private static final long MAX_CHARGEABLE_WAITING_MINUTES = 180;

    @Override
    public FareQuote estimate(GeoPoint pickup, GeoPoint destination, Instant atTime, Duration waitingTime) {
        double distanceKm = pickup.distanceKm(destination);

        FareTimeBand timeBand = timeBandFor(atTime);
        TimeBandRate rate = rateFor(timeBand);

        BigDecimal baseFare = rate.baseFareVnd();
        double billableKm = Math.max(0, distanceKm - rate.includedKm());
        BigDecimal distanceCharge = round(PER_KM_RATE_VND.multiply(BigDecimal.valueOf(billableKm)));
        BigDecimal remoteAreaSurcharge = remoteAreaSurcharge(distanceKm);
        BigDecimal waitingFee = waitingFee(waitingTime);

        BigDecimal total = baseFare.add(distanceCharge).add(remoteAreaSurcharge).add(waitingFee);

        var breakdown = new FareBreakdown(
                timeBand,
                Money.vnd(baseFare),
                rate.includedKm(),
                Money.vnd(distanceCharge),
                Money.vnd(remoteAreaSurcharge),
                Money.vnd(waitingFee),
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

    private BigDecimal waitingFee(Duration waitingTime) {
        long chargeableMinutes = Math.max(0, waitingTime.toMinutes() - FREE_WAITING_MINUTES);
        chargeableMinutes = Math.min(chargeableMinutes, MAX_CHARGEABLE_WAITING_MINUTES);
        return WAITING_FEE_PER_MINUTE_VND.multiply(BigDecimal.valueOf(chargeableMinutes));
    }

    /** Khung giờ vắt qua nửa đêm (LATE_NIGHT/OVERNIGHT) — so theo giờ trong ngày, không phải khoảng liên tục. */
    private FareTimeBand timeBandFor(Instant atTime) {
        int hour = ZonedDateTime.ofInstant(atTime, VIETNAM_ZONE).getHour();
        if (hour >= 6 && hour < 19) {
            return FareTimeBand.DAY;
        }
        if (hour >= 19 && hour < 23) {
            return FareTimeBand.EVENING;
        }
        if (hour == 23) {
            return FareTimeBand.LATE_NIGHT;
        }
        return FareTimeBand.OVERNIGHT; // hour 0-5
    }

    private TimeBandRate rateFor(FareTimeBand band) {
        return switch (band) {
            case DAY -> DAY_RATE;
            case EVENING -> EVENING_RATE;
            case LATE_NIGHT -> LATE_NIGHT_RATE;
            case OVERNIGHT -> OVERNIGHT_RATE;
        };
    }

    private BigDecimal round(BigDecimal value) {
        return value.setScale(0, RoundingMode.HALF_UP);
    }
}
