package com.callme.notification.event;

import com.callme.common.event.BookingConfirmedEvent;
import com.callme.common.event.DriverForcedOfflineEvent;
import com.callme.common.event.GpsSignalLostEvent;
import com.callme.common.event.IncidentReportedEvent;
import com.callme.common.event.SosRaisedEvent;
import com.callme.common.event.TripAbortedMidwayEvent;
import com.callme.common.event.TripCompletedEvent;
import com.callme.common.event.TripDestinationChangedEvent;
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
        // referenceId = bookingId: customer uses it for GET /api/trips/by-booking/{bookingId};
        // driver uses it as context then calls GET /api/trips/my-active to resolve tripId.
        notificationService.notify(event.customerId(), NotificationType.BOOKING_CONFIRMED,
                "Đã tìm thấy tài xế cho chuyến của bạn — tài xế đang trên đường đến điểm đón.",
                event.bookingId());
        notificationService.notify(event.driverId(), NotificationType.BOOKING_CONFIRMED,
                "Bạn vừa được gán một chuyến lái xe hộ mới — vui lòng di chuyển đến điểm đón khách.",
                event.bookingId());
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
        notificationService.notify(otherParty, NotificationType.SOS_RAISED, message, event.tripId());
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
                "Mất tín hiệu định vị của tài xế trong chuyến đi của bạn — tổng đài đang theo dõi sát chuyến này.",
                event.tripId());
        notificationService.notify(event.driverId(), NotificationType.GPS_SIGNAL_LOST,
                "Ứng dụng đang mất tín hiệu định vị của bạn — vui lòng kiểm tra kết nối mạng/GPS để chuyến đi được ghi nhận chính xác.",
                event.tripId());
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
                "Chuyến đi của bạn đã kết thúc sớm giữa đường — tài xế đã đưa bạn và xe đến nơi an toàn. Tổng đài đã được thông báo và sẽ liên hệ hỗ trợ.",
                event.tripId());
        notificationService.notify(event.driverId(), NotificationType.TRIP_ABORTED_MIDWAY,
                "Báo cáo kết thúc khẩn cấp giữa chuyến của bạn đã được ghi nhận — tổng đài sẽ xem xét và hỗ trợ điều phối tiếp theo.",
                event.tripId());
    }

    /**
     * CLAUDE.md §4.2 — a collision/incident was reported. Both participants are
     * notified that it's now on record and CSKH's liability investigation
     * (`GET /api/trips/incidents`) will follow up — the same "someone is now looking
     * at this" reassurance {@link #onSosRaised}/{@link #onTripAbortedMidway} give.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onIncidentReported(IncidentReportedEvent event) {
        notificationService.notify(event.customerId(), NotificationType.INCIDENT_REPORTED,
                "Một sự cố/va chạm trên chuyến đi của bạn đã được ghi nhận — tổng đài sẽ xem xét và liên hệ nếu cần.",
                event.tripId());
        notificationService.notify(event.driverId(), NotificationType.INCIDENT_REPORTED,
                "Một sự cố/va chạm trên chuyến đi của bạn đã được ghi nhận — tổng đài sẽ xem xét và liên hệ nếu cần.",
                event.tripId());
    }

    /**
     * CLAUDE.md C.5/A.4 — the fare was re-quoted the moment the customer redirected
     * the trip; both parties get the new price in writing (the customer also received
     * it synchronously in the API response). The driver is mid-job in the customer's
     * car — they deserve the same route/money transparency, not a surprise at the end.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTripDestinationChanged(TripDestinationChangedEvent event) {
        String fare = event.newFareEstimate().amount() + " " + event.newFareEstimate().currency().getCurrencyCode();
        notificationService.notify(event.customerId(), NotificationType.DESTINATION_CHANGED,
                "Điểm đến của chuyến đi đã được cập nhật — cước ước tính mới: " + fare + " (cước cuối tính tại thời điểm hoàn thành).",
                event.tripId());
        notificationService.notify(event.driverId(), NotificationType.DESTINATION_CHANGED,
                "Khách đã đổi điểm đến của chuyến đi — cước ước tính mới: " + fare + ".",
                event.tripId());
    }

    /**
     * CLAUDE.md §4.5 — the driver was forced offline because a verification lapsed
     * mid-shift. Telling them exactly why (and therefore what to renew) is the
     * difference between a compliance nudge and a mysteriously dead income stream.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDriverForcedOffline(DriverForcedOfflineEvent event) {
        notificationService.notify(event.driverId(), NotificationType.VERIFICATION_EXPIRED,
                "Bạn đã được chuyển sang trạng thái ngoại tuyến vì hồ sơ xác minh không còn hiệu lực: " + event.reasons()
                        + ". Vui lòng liên hệ tổng đài để tái xác minh trước khi tiếp tục nhận chuyến.");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTripCompleted(TripCompletedEvent event) {
        notificationService.notify(event.customerId(), NotificationType.TRIP_COMPLETED,
                "Chuyến đi đã hoàn tất. Cước phí: " + event.finalFare().amount() + " " + event.finalFare().currency().getCurrencyCode(),
                event.tripId());
        notificationService.notify(event.driverId(), NotificationType.TRIP_COMPLETED,
                "Bạn đã hoàn thành chuyến lái xe hộ. Vui lòng xác nhận thanh toán với khách.",
                event.tripId());
    }
}
