package com.callme.common.shared;

/**
 * CLAUDE.md E.3 — "hủy do bất khả kháng... cần phân loại lý do hủy để không tính
 * phí sai cho bên không có lỗi". Persisted on both Booking and Trip cancellations
 * so a later fee/dispute-resolution policy has the audit trail it needs to tell
 * "khách đổi ý" apart from "tai nạn/sự cố y tế/thiên tai" — charging a cancellation
 * fee in the latter case would be exactly the kind of unfair outcome CLAUDE.md warns
 * against. Lives in `common` because both the booking and trip modules need it and
 * neither owns the concept.
 */
public enum CancellationReason {
    /** The customer changed their mind / no longer needs the ride. */
    CUSTOMER_REQUEST,
    /** The driver backed out (e.g. before pickup — CLAUDE.md B.4). */
    DRIVER_REQUEST,
    /**
     * CSKH/admin determined the cancellation was outside either party's control
     * (accident, medical emergency, natural disaster...) — the only lane through
     * which "bất khả kháng" is recorded today, since there is no self-service way
     * for a participant to declare it (that would invite abuse of fee waivers).
     */
    FORCE_MAJEURE,
    /** The system cancelled this as a consequence of something else (e.g. an orphaned trip when its booking was cancelled), not a deliberate choice by either participant. */
    SYSTEM_CASCADE,
    /**
     * CLAUDE.md C.1 — driver waited at the pickup point past the grace period and the
     * customer never appeared (too drunk to wake up, wandered off, someone else booked
     * on their behalf...). Distinct from {@code CUSTOMER_REQUEST}: the customer didn't
     * choose to cancel, so a no-show fee policy — not an ordinary cancellation fee —
     * governs what (if anything) they're charged.
     */
    CUSTOMER_NO_SHOW,
    /**
     * CLAUDE.md B.3 — adapted to this system's atomic-reservation matching (there is
     * no broadcast-and-await-accept window to time out: the driver is reserved the
     * instant they're matched, see {@code DriverMatchingPort}). The equivalent failure
     * here is a matched driver who goes dark instead of heading to pickup — recorded
     * distinctly from {@code DRIVER_REQUEST} so it can both excuse the customer from
     * any fee AND feed the driver's no-response strike count (lowers future matching
     * priority — CLAUDE.md B.3 "hạ điểm ưu tiên hiển thị của tài xế đó").
     */
    DRIVER_UNRESPONSIVE,
    /**
     * CLAUDE.md C.3 — "xe của khách hỏng hóc / không khởi động được": squarely the
     * customer's risk (it's their vehicle), not the driver's fault and not a system
     * artefact. Recorded distinctly so neither side is charged a cancellation fee for
     * it and a later "đổi sang taxi thường / gọi cứu hộ" remediation flow has a clean
     * audit trail of why the original "lái xe hộ" trip never happened.
     */
    VEHICLE_BREAKDOWN,
    /**
     * CLAUDE.md E.2 — "tài xế hủy giữa chừng khi đã ở trạng thái IN_PROGRESS": the
     * domain's most serious cancellation. The driver is physically holding the
     * customer's car with the customer aboard — they cannot simply "bail" the way
     * {@code DRIVER_REQUEST}/{@link #DRIVER_REQUEST} implies. Reachable only through
     * the dedicated abort flow that first requires the driver to attest they brought
     * the vehicle and customer to a declared safe location — recorded distinctly so
     * neither a casual-bail penalty nor an ordinary cancellation fee is ever applied
     * to what is, first and foremost, a safety incident CSKH must follow up on.
     */
    DRIVER_EMERGENCY_ABORT
}
