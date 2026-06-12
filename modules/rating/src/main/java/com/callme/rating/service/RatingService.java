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

    /**
     * Ratings of one trip — visible only to that trip's two participants (each side
     * sees what was said about/by them) and admins. Previously readable by any
     * logged-in user holding a tripId (scores, comments, both parties' ids) — the one
     * endpoint that deviated from the participant-only rule everywhere else (OWASP A01).
     */
    List<RatingResponse> getByTrip(UUID tripId, AuthenticatedAccount requester);

    /** CLAUDE.md F.1 — admin/CSKH review queue of safety/behaviour complaints, kept separate from ordinary ratings. */
    List<RatingResponse> listComplaints(AuthenticatedAccount requester);

    /** CLAUDE.md F.3 — admin/CSKH review queue of auto-raised review-bombing/retaliation patterns. */
    List<ReviewPatternFlagResponse> listReviewPatternFlags(AuthenticatedAccount requester);
}
