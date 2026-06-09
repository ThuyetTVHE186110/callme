package com.callme.rating.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * CLAUDE.md F.3 — "review bombing / đánh giá trả đũa giữa khách và tài xế". A single
 * low score is just dissatisfaction; a *pattern* of low scores from the same rater
 * toward the same ratee, repeated across separate trips, is the signal worth a human
 * look — it could be a genuinely bad match repeatedly paired together, or it could be
 * a personal vendetta dressed up as feedback. Either reading needs a person to weigh
 * the actual comments and trip history, so — exactly like {@link com.callme.trip.entity.RouteDeviationFlag}
 * and {@code SosAlert} — this is a flat, append-only worklist entry, never an
 * automatic penalty on the ratee's reputation score.
 */
@Entity
@Table(name = "review_pattern_flags")
@Getter
@NoArgsConstructor(force = true)
public class ReviewPatternFlag {

    @Id
    @GeneratedValue
    private UUID id;

    private UUID raterUserId;

    private UUID rateeUserId;

    private long lowScoreCount;

    private Instant flaggedAt;

    public ReviewPatternFlag(UUID raterUserId, UUID rateeUserId, long lowScoreCount, Instant flaggedAt) {
        this.raterUserId = raterUserId;
        this.rateeUserId = rateeUserId;
        this.lowScoreCount = lowScoreCount;
        this.flaggedAt = flaggedAt;
    }
}
