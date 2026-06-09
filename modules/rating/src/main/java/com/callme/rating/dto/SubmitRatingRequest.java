package com.callme.rating.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * raterUserId/rateeUserId are intentionally absent — both are derived server-side from
 * the authenticated principal and the trip's real participant record (see RatingServiceImpl),
 * closing CLAUDE.md edge case F.3 (a rating must correspond to an actual completed trip).
 *
 * `complaint` (CLAUDE.md F.1) lets the rater flag this as a serious safety/behaviour
 * report rather than an ordinary star rating — pulling it into the CSKH review queue
 * instead of just averaging silently into a reputation score.
 */
public record SubmitRatingRequest(
        @NotNull(message = "Mã chuyến đi không được để trống") UUID tripId,
        @Min(value = 1, message = "Điểm đánh giá phải từ 1 đến 5") @Max(value = 5, message = "Điểm đánh giá phải từ 1 đến 5") int score,
        @Size(max = 1000, message = "Nhận xét quá dài") String comment,
        boolean complaint) {
}
