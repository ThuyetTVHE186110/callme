package com.callme.rating.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * CLAUDE.md F.2 — lets a rater revise their score/comment within {@link com.callme.rating.entity.Rating#EDIT_WINDOW}
 * of the original submission, since they were often still intoxicated when they first rated.
 */
public record EditRatingRequest(
        @Min(value = 1, message = "Điểm đánh giá phải từ 1 đến 5") @Max(value = 5, message = "Điểm đánh giá phải từ 1 đến 5") int score,
        @Size(max = 1000, message = "Nhận xét quá dài") String comment,
        boolean complaint) {
}
