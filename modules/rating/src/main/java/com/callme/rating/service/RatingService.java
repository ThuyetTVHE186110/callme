package com.callme.rating.service;

import com.callme.common.security.AuthenticatedAccount;
import com.callme.rating.dto.EditRatingRequest;
import com.callme.rating.dto.RatingResponse;
import com.callme.rating.dto.ReviewPatternFlagResponse;
import com.callme.rating.dto.SubmitRatingRequest;

import java.util.List;
import java.util.UUID;

public interface RatingService {

    UUID submit(SubmitRatingRequest request, AuthenticatedAccount rater);

    /** CLAUDE.md F.2 — only the original rater, only within {@link com.callme.rating.entity.Rating#EDIT_WINDOW}. */
    void edit(UUID ratingId, EditRatingRequest request, AuthenticatedAccount requester);

    List<RatingResponse> getByTrip(UUID tripId);

    /** CLAUDE.md F.1 — admin/CSKH review queue of safety/behaviour complaints, kept separate from ordinary ratings. */
    List<RatingResponse> listComplaints(AuthenticatedAccount requester);

    /** CLAUDE.md F.3 — admin/CSKH review queue of auto-raised review-bombing/retaliation patterns. */
    List<ReviewPatternFlagResponse> listReviewPatternFlags(AuthenticatedAccount requester);
}
