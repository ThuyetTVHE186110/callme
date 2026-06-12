package com.callme.rating.controller;

import com.callme.common.response.ApiResponse;
import com.callme.common.security.AuthenticatedAccount;
import com.callme.rating.dto.EditRatingRequest;
import com.callme.rating.dto.RatingResponse;
import com.callme.rating.dto.ReviewPatternFlagResponse;
import com.callme.rating.dto.SubmitRatingRequest;
import com.callme.rating.service.RatingService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ratings")
public class RatingController {

    private final RatingService ratingService;

    public RatingController(RatingService ratingService) {
        this.ratingService = ratingService;
    }

    @PostMapping
    public ApiResponse<UUID> submit(@Valid @RequestBody SubmitRatingRequest request, @AuthenticationPrincipal AuthenticatedAccount account) {
        return ApiResponse.ok(ratingService.submit(request, account));
    }

    /** CLAUDE.md F.2 — revise a rating within the edit window (the rater may have been intoxicated when first submitting). */
    @PutMapping("/{ratingId}")
    public ApiResponse<Void> edit(@PathVariable UUID ratingId, @Valid @RequestBody EditRatingRequest request,
                                  @AuthenticationPrincipal AuthenticatedAccount account) {
        ratingService.edit(ratingId, request, account);
        return ApiResponse.ok(null);
    }

    /** Participant-only (or admin) — mirrors every other per-trip read in the system (OWASP A01). */
    @GetMapping("/by-trip/{tripId}")
    public ApiResponse<List<RatingResponse>> getByTrip(@PathVariable UUID tripId, @AuthenticationPrincipal AuthenticatedAccount account) {
        return ApiResponse.ok(ratingService.getByTrip(tripId, account));
    }

    /** CLAUDE.md F.1 — CSKH/admin queue of safety/behaviour complaints, separate from the ordinary rating feed. */
    @GetMapping("/complaints")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<RatingResponse>> listComplaints(@AuthenticationPrincipal AuthenticatedAccount account) {
        return ApiResponse.ok(ratingService.listComplaints(account));
    }

    /** CLAUDE.md F.3 — CSKH/admin queue of auto-raised review-bombing/retaliation patterns, never an automatic penalty on the ratee. */
    @GetMapping("/review-pattern-flags")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<ReviewPatternFlagResponse>> listReviewPatternFlags(@AuthenticationPrincipal AuthenticatedAccount account) {
        return ApiResponse.ok(ratingService.listReviewPatternFlags(account));
    }
}
