-- CLAUDE.md F.3 — "review bombing / đánh giá trả đũa giữa khách và tài xế". Auto-raised
-- by RatingServiceImpl.flagRetaliationPatternIfJustCrossed the moment the count of low
-- scores (<= LOW_SCORE_THRESHOLD) the same rater has handed the same ratee, across
-- separate completed trips, first crosses RETALIATION_PATTERN_COUNT; queued here as a
-- flat worklist for CSKH to weigh — could be a genuinely bad repeated pairing or a
-- personal vendetta, either way a human call, never an automatic reputation penalty.
-- Mirrors sos_alerts / route_deviation_flags.

create table review_pattern_flags (
    id                uuid not null,
    rater_user_id     uuid,
    ratee_user_id     uuid,
    low_score_count   bigint not null,
    flagged_at        timestamp(6) with time zone,
    primary key (id)
);
-- ReviewPatternFlagRepository.findAllByOrderByFlaggedAtDesc — CSKH worklist sorted by recency.
create index idx_review_pattern_flags_flagged_at on review_pattern_flags (flagged_at desc);
