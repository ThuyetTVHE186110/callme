package com.callme.notification.event;

import com.callme.common.event.BookingConfirmedEvent;
import com.callme.common.event.GpsSignalLostEvent;
import com.callme.common.event.SosRaisedEvent;
import com.callme.common.event.TripAbortedMidwayEvent;
import com.callme.common.event.TripCompletedEvent;
import com.callme.notification.entity.NotificationType;
import com.callme.notification.service.NotificationService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

/**
 * Fans key domain events out to both parties as in-app notifications. Depends only on
 * the shared event types from `common` — never on booking's or trip's internals.
 */
@Component
public class DomainEventListener {

    private final NotificationService notificationService;

    public DomainEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        notificationService.notify(event.customerId(), NotificationType.BOOKING_CONFIRMED,
                "Đã tìm thấy tài xế cho chuyến của bạn — tài xế đang trên đường đến điểm đón.");
        notificationService.notify(event.driverId(), NotificationType.BOOKING_CONFIRMED,
                "Bạn vừa được gán một chuyến lái xe hộ mới — vui lòng di chuyển đến điểm đón khách.");
    }

    /**
     * CLAUDE.md C.6 — alert the *other* participant immediately, regardless of which
     * of the two raised it: a customer in danger needs their driver to know right now,
     * and vice versa. The CSKH/admin worklist (`GET /api/trips/sos`) is the channel
     * support actually monitors — this in-app push is the same-second heads-up to
     * whoever is physically on the other side of the situation.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSosRaised(SosRaisedEvent event) {
        String message = "⚠ Đã có báo động khẩn cấp trên chuyến đi của bạn — tổng đài đang được thông báo, vui lòng giữ an toàn.";
        UUID otherParty = event.raisedByProfileId().equals(event.customerId()) ? event.driverId() : event.customerId();
        notificationService.notify(otherParty, NotificationType.SOS_RAISED, message);
    }

    /**
     * CLAUDE.md C.7 — the driver's GPS trail went cold mid-trip (they're physically
     * holding the customer's car — a safety concern, not a billing nuisance). Both
     * participants get a heads-up: the customer because their driver/car may be in
     * trouble, the driver because their own app may be the one losing signal and they
     * should know support is now watching this trip closely.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGpsSignalLost(GpsSignalLostEvent event) {
        notificationService.notify(event.customerId(), NotificationType.GPS_SIGNAL_LOST,
                "Mất tín hiệu định vị của tài xế trong chuyến đi của bạn — tổng đài đang theo dõi sát chuyến này.");
        notificationService.notify(event.driverId(), NotificationType.GPS_SIGNAL_LOST,
                "Ứng dụng đang mất tín hiệu định vị của bạn — vui lòng kiểm tra kết nối mạng/GPS để chuyến đi được ghi nhận chính xác.");
    }

    /**
     * CLAUDE.md E.2 — the driver ended an IN_PROGRESS trip after declaring they'd
     * already brought the car and customer to a safe spot. Both sides need to know
     * right away: the customer that their ride ended early (and where they now are),
     * the driver that this is now on CSKH's radar — the worklist (`GET /api/trips/emergency-aborts`)
     * is where support actually follows up on whether a replacement driver is needed.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTripAbortedMidway(TripAbortedMidwayEvent event) {
        notificationService.notify(event.customerId(), NotificationType.TRIP_ABORTED_MIDWAY,
                "Chuyến đi của bạn đã kết thúc sớm giữa đường — tài xế đã đưa bạn và xe đến nơi an toàn. Tổng đài đã được thông báo và sẽ liên hệ hỗ trợ.");
        notificationService.notify(event.driverId(), NotificationType.TRIP_ABORTED_MIDWAY,
                "Báo cáo kết thúc khẩn cấp giữa chuyến của bạn đã được ghi nhận — tổng đài sẽ xem xét và hỗ trợ điều phối tiếp theo.");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTripCompleted(TripCompletedEvent event) {
        notificationService.notify(event.customerId(), NotificationType.TRIP_COMPLETED,
                "Chuyến đi đã hoàn tất. Cước phí: " + event.finalFare().amount() + " " + event.finalFare().currency().getCurrencyCode());
        notificationService.notify(event.driverId(), NotificationType.TRIP_COMPLETED,
                "Bạn đã hoàn thành chuyến lái xe hộ. Vui lòng xác nhận thanh toán với khách.");
    }
}
