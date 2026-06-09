-- CLAUDE.md C.2 — records the moment the driver attests they verified the customer
-- is the registered owner before taking the wheel. Audit trail for the "lái xe
-- không phép" legal-exposure edge case: proof the check happened, and when.

alter table trips
    add column identity_verified_at timestamp(6) with time zone;
