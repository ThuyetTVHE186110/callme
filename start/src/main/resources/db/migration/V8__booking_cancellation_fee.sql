-- CLAUDE.md E.1 — "khách hủy sau khi tài xế đã xác nhận và đang di chuyển đến điểm
-- đón nên có phí hủy... nhưng cần ngưỡng thời gian hợp lý (hủy trong 1 phút đầu vẫn
-- miễn phí)". confirmed_at anchors that grace-period clock at the moment a driver is
-- actually matched (Booking.confirmWithDriver) — not at booking creation, since a
-- customer backing out before any driver is dispatched costs nobody anything
-- (CLAUDE.md A.5, still unconditionally free — see Booking.cancel).
--
-- The fee itself is recorded as a flat amount directly on the booking rather than
-- routed through `payments` (which is wired 1:1 to a completed trip's fare —
-- PaymentRepository.findByTripId is a unique index) — this leaves CSKH/đối soát a
-- clear, queryable trail to collect against without inventing a parallel charge
-- pathway for what should be a rare case.

alter table bookings
    add column confirmed_at             timestamp(6) with time zone,
    add column cancellation_fee_amount  numeric(38, 2),
    add column cancellation_fee_currency varchar(255);
