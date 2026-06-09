package com.callme.rating.entity;

import com.callme.common.exception.ForbiddenException;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ratings")
@Getter
@NoArgsConstructor(force = true)
public class Rating {

    /**
     * CLAUDE.md F.2 — how long after submission a rater (often still intoxicated when
     * they first rated) may revise their score/comment. Long enough to "sober up and
     * reconsider", short enough that ratings still settle into something stable.
     */
    public static final Duration EDIT_WINDOW = Duration.ofHours(24);

    @Id
    @GeneratedValue
    private UUID id;

    private UUID tripId;

    /** Whoever submits the rating — customer rating the driver, or vice versa. */
    private UUID raterUserId;

    private UUID rateeUserId;

    private int score;

    private String comment;

    /**
     * CLAUDE.md F.1 — "report vi phạm an toàn / thái độ" must follow a different path
     * than an ordinary star rating: a low score alone is just dissatisfaction, but a
     * flagged complaint pulls the trip into a CSKH review queue (CLAUDE.md F.1 —
     * {@link com.callme.rating.repository.RatingRepository#findByComplaintTrue}) for a
     * human to actually intervene rather than silently averaging into a reputation score.
     */
    private boolean complaint;

    private Instant createdAt;

    private Instant updatedAt;

    public Rating(UUID tripId, UUID raterUserId, UUID rateeUserId, int score, String comment, boolean complaint) {
        validateScore(score);
        this.tripId = tripId;
        this.raterUserId = raterUserId;
        this.rateeUserId = rateeUserId;
        this.score = score;
        this.comment = comment;
        this.complaint = complaint;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    /**
     * CLAUDE.md F.2 — only the original rater, and only inside {@link #EDIT_WINDOW}
     * from the original submission (not from the last edit — otherwise a rater could
     * keep the window open indefinitely by repeatedly tweaking).
     */
    public void edit(UUID requesterUserId, int score, String comment, boolean complaint, Instant now) {
        if (!this.raterUserId.equals(requesterUserId)) {
            throw new ForbiddenException("Bạn chỉ có thể chỉnh sửa đánh giá của chính mình");
        }
        if (now.isAfter(this.createdAt.plus(EDIT_WINDOW))) {
            throw new ForbiddenException("Đã quá thời hạn chỉnh sửa đánh giá (" + EDIT_WINDOW.toHours() + " giờ kể từ lúc gửi)");
        }
        validateScore(score);
        this.score = score;
        this.comment = comment;
        this.complaint = complaint;
        this.updatedAt = now;
    }

    private static void validateScore(int score) {
        if (score < 1 || score > 5) {
            throw new IllegalArgumentException("score must be between 1 and 5: " + score);
        }
    }
}
