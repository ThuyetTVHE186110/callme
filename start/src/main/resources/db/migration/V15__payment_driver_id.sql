-- CLAUDE.md D.1 — the driver is the party physically collecting cash, so they must
-- be able to see the payment and report "khách từ chối thanh toán / không đủ tiền
-- mặt". Recording driver_id on the payment lets the service authorize the driver as
-- a participant (view, mark-failed, confirm CASH) without a cross-module trip lookup.
alter table payments
    add column driver_id uuid;

-- Backfill from the trip the payment was opened for (1:1 via the unique trip_id index).
update payments p
set driver_id = t.driver_id
from trips t
where t.id = p.trip_id
  and p.driver_id is null;
