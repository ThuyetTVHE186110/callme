package com.callme.rating.service.impl;

import com.callme.common.exception.ConflictException;
import com.callme.common.exception.ForbiddenException;
import com.callme.common.exception.NotFoundException;
import com.callme.common.port.TripParticipantsPort;
import com.callme.common.security.AuthenticatedAccount;
import com.callme.rating.dto.EditRatingRequest;
import com.callme.rating.dto.RatingResponse;
import com.callme.rating.dto.ReviewPatternFlagResponse;
import com.callme.rating.dto.SubmitRatingRequest;
import com.callme.rating.entity.Rating;
import com.callme.rating.entity.ReviewPatternFlag;
import com.callme.rating.repository.RatingRepository;
import com.callme.rating.repository.ReviewPatternFlagRepository;
import com.callme.rating.service.RatingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class RatingServiceImpl implements RatingService {

    /**
     * CLAUDE.md F.3 — a 1-2 star score is read as "negative" for pattern-detection
     * purposes; 3 is lukewarm-but-not-hostile and shouldn't feed a retaliation count.
     */
    private static final int LOW_SCORE_THRESHOLD = 2;

    /**
     * CLAUDE.md F.3 — how many low scores the same rater must have handed the same
     * ratee, across separate trips, before the pattern is worth a CSKH look. Below
     * this it reads as plausibly-genuine repeated dissatisfaction; at or above it,
     * it could equally be a personal vendetta — either way a human should weigh the
     * actual trip history, not an algorithm.
     */
    private static final long RETALIATION_PATTERN_COUNT = 3;

    private final RatingRepository ratingRepository;
    private final ReviewPatternFlagRepository reviewPatternFlagRepository;
    private final TripParticipantsPort tripParticipantsPort;

    public RatingServiceImpl(RatingRepository ratingRepository,
                             ReviewPatternFlagRepository reviewPatternFlagRepository,
                             TripParticipantsPort tripParticipantsPort) {
        this.ratingRepository = ratingRepository;
        this.reviewPatternFlagRepository = reviewPatternFlagRepository;
        this.tripParticipantsPort = tripParticipantsPort;
    }

    /**
     * CLAUDE.md F.3 (anti-abuse): rather than trusting raterUserId/rateeUserId from the
     * client, we resolve the rater from the authenticated principal and the ratee from
     * the trip's actual recorded participants — so you can only ever rate someone you
     * really shared a completed trip with, exactly once.
     */
    @Override
    public UUID submit(SubmitRatingRequest request, AuthenticatedAccount rater) {
        var participants = tripParticipantsPort.findParticipants(request.tripId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy chuyến đi: " + request.tripId()));

        if (!participants.completed()) {
            throw new ConflictException("Chỉ có thể đánh giá sau khi chuyến đi đã hoàn thành");
        }

        UUID raterUserId = rater.profileId();
        UUID rateeUserId;
        if (rater.ownsProfile(participants.customerId())) {
            rateeUserId = participants.driverId();
        } else if (rater.ownsProfile(participants.driverId())) {
            rateeUserId = participants.customerId();
        } else {
            throw new ForbiddenException("Bạn không tham gia chuyến đi này nên không thể đánh giá");
        }

        if (ratingRepository.existsByTripIdAndRaterUserId(request.tripId(), raterUserId)) {
            throw new ConflictException("Bạn đã đánh giá chuyến đi này rồi");
        }

        var rating = new Rating(request.tripId(), raterUserId, rateeUserId, request.score(), request.comment(), request.complaint());
        var saved = ratingRepository.save(rating);

        flagRetaliationPatternIfJustCrossed(raterUserId, rateeUserId, request.score());

        return saved.getId();
    }

    /**
     * CLAUDE.md F.3 — "review bombing / đánh giá trả đũa". Raised exactly once per
     * pattern, the moment the count of low scores from this rater toward this ratee
     * crosses {@code RETALIATION_PATTERN_COUNT} for the first time — checking equality
     * (not "at least") is what keeps this from re-flagging on every subsequent low
     * score from the same pair, the same "crossed-threshold" trick used by
     * {@code TripServiceImpl.sweepStaleGpsTrips} (CLAUDE.md C.7) to fire its event
     * exactly once without a persisted "already flagged" marker.
     */
    private void flagRetaliationPatternIfJustCrossed(UUID raterUserId, UUID rateeUserId, int score) {
        if (score > LOW_SCORE_THRESHOLD) {
            return;
        }
        long lowScoreCount = ratingRepository.countByRaterUserIdAndRateeUserIdAndScoreLessThanEqual(raterUserId, rateeUserId, LOW_SCORE_THRESHOLD);
        if (lowScoreCount == RETALIATION_PATTERN_COUNT) {
            reviewPatternFlagRepository.save(new ReviewPatternFlag(raterUserId, rateeUserId, lowScoreCount, Instant.now()));
        }
    }

    @Override
    public void edit(UUID ratingId, EditRatingRequest request, AuthenticatedAccount requester) {
        var rating = ratingRepository.findById(ratingId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đánh giá: " + ratingId));
        rating.edit(requester.profileId(), request.score(), request.comment(), request.complaint(), Instant.now());
        ratingRepository.save(rating);
    }

    @Override
    public List<RatingResponse> getByTrip(UUID tripId) {
        return ratingRepository.findByTripId(tripId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<RatingResponse> listComplaints(AuthenticatedAccount requester) {
        if (!requester.isAdmin()) {
            throw new ForbiddenException("Chỉ CSKH/quản trị viên mới có thể xem hàng đợi khiếu nại");
        }
        return ratingRepository.findByComplaintTrueOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    /** CLAUDE.md F.3 — admin/CSKH worklist of auto-raised review-bombing/retaliation patterns, mirroring {@link #listComplaints}. */
    @Override
    public List<ReviewPatternFlagResponse> listReviewPatternFlags(AuthenticatedAccount requester) {
        if (!requester.isAdmin()) {
            throw new ForbiddenException("Chỉ CSKH/quản trị viên mới có thể xem hàng đợi cảnh báo đánh giá trả đũa");
        }
        return reviewPatternFlagRepository.findAllByOrderByFlaggedAtDesc().stream()
                .map(f -> new ReviewPatternFlagResponse(f.getId(), f.getRaterUserId(), f.getRateeUserId(), f.getLowScoreCount(), f.getFlaggedAt()))
                .toList();
    }

    private RatingResponse toResponse(Rating r) {
        return new RatingResponse(r.getId(), r.getTripId(), r.getRaterUserId(), r.getRateeUserId(),
                r.getScore(), r.getComment(), r.isComplaint(), r.getCreatedAt(), r.getUpdatedAt());
    }
}
