package com.callme.rating.repository;

import com.callme.rating.entity.Rating;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RatingRepository extends JpaRepository<Rating, UUID> {

    List<Rating> findByTripId(UUID tripId);

    List<Rating> findByRateeUserId(UUID rateeUserId);

    boolean existsByTripIdAndRaterUserId(UUID tripId, UUID raterUserId);

    /** CLAUDE.md F.1 — backs the admin/CSKH review queue of safety/behaviour complaints, kept separate from ordinary star ratings. */
    List<Rating> findByComplaintTrueOrderByCreatedAtDesc();

    /**
     * CLAUDE.md F.3 — counts how many times this rater has handed this exact ratee a
     * low score across separate trips (one rating per trip per rater is already
     * enforced by {@link #existsByTripIdAndRaterUserId}, so repetition here can only
     * mean repeated *pairings*) — the raw signal {@code RatingServiceImpl} compares
     * against {@code RETALIATION_PATTERN_COUNT} to auto-raise a review-pattern flag.
     */
    long countByRaterUserIdAndRateeUserIdAndScoreLessThanEqual(UUID raterUserId, UUID rateeUserId, int score);
}
