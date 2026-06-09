package com.callme.rating.service.impl;

import com.callme.common.port.TripParticipantsPort;
import com.callme.common.port.dto.TripParticipants;
import com.callme.common.security.AccountRole;
import com.callme.common.security.AuthenticatedAccount;
import com.callme.rating.dto.SubmitRatingRequest;
import com.callme.rating.entity.Rating;
import com.callme.rating.entity.ReviewPatternFlag;
import com.callme.rating.repository.RatingRepository;
import com.callme.rating.repository.ReviewPatternFlagRepository;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CLAUDE.md F.3 — "review bombing / đánh giá trả đũa giữa khách và tài xế". Verifies
 * the auto-raised {@link ReviewPatternFlag} fires exactly once, the moment (not
 * before, not again after) the same rater's count of low scores toward the same
 * ratee crosses the configured threshold — mirroring the "crossed-threshold window"
 * technique {@code TripServiceImpl.sweepStaleGpsTrips} uses for C.7, here expressed
 * as count-equality instead of a time window.
 */
class RatingServiceImplTest {

    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID DRIVER_ID = UUID.randomUUID();
    private static final UUID TRIP_ID = UUID.randomUUID();

    private final RatingRepository ratingRepository = mock(RatingRepository.class);
    private final ReviewPatternFlagRepository reviewPatternFlagRepository = mock(ReviewPatternFlagRepository.class);
    private final TripParticipantsPort tripParticipantsPort = mock(TripParticipantsPort.class);
    private final RatingServiceImpl service = new RatingServiceImpl(ratingRepository, reviewPatternFlagRepository, tripParticipantsPort);

    private final AuthenticatedAccount customer = new AuthenticatedAccount(UUID.randomUUID(), CUSTOMER_ID, AccountRole.CUSTOMER);

    private void stubCompletedTripBetweenCustomerAndDriver() {
        when(tripParticipantsPort.findParticipants(TRIP_ID))
                .thenReturn(java.util.Optional.of(new TripParticipants(CUSTOMER_ID, DRIVER_ID, true)));
        when(ratingRepository.save(any(Rating.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private SubmitRatingRequest lowScoreRequest(int score) {
        return new SubmitRatingRequest(TRIP_ID, score, "tệ", false);
    }

    @Test
    void doesNotFlagBeforeTheCountReachesThePatternThreshold() {
        stubCompletedTripBetweenCustomerAndDriver();
        // This submission would be the 2nd low score from this rater toward this ratee — below the threshold of 3.
        when(ratingRepository.countByRaterUserIdAndRateeUserIdAndScoreLessThanEqual(eq(CUSTOMER_ID), eq(DRIVER_ID), eq(2))).thenReturn(2L);

        service.submit(lowScoreRequest(1), customer);

        verify(reviewPatternFlagRepository, never()).save(any());
    }

    @Test
    void flagsExactlyWhenTheCountFirstCrossesTheThreshold() {
        stubCompletedTripBetweenCustomerAndDriver();
        when(ratingRepository.countByRaterUserIdAndRateeUserIdAndScoreLessThanEqual(eq(CUSTOMER_ID), eq(DRIVER_ID), eq(2))).thenReturn(3L);

        service.submit(lowScoreRequest(2), customer);

        verify(reviewPatternFlagRepository).save(any(ReviewPatternFlag.class));
    }

    /** Crossed-threshold-window: once already flagged, subsequent low scores from the same pair must not raise duplicate flags. */
    @Test
    void doesNotReflagOnceThePatternHasAlreadyBeenRaised() {
        stubCompletedTripBetweenCustomerAndDriver();
        when(ratingRepository.countByRaterUserIdAndRateeUserIdAndScoreLessThanEqual(eq(CUSTOMER_ID), eq(DRIVER_ID), eq(2))).thenReturn(4L);

        service.submit(lowScoreRequest(1), customer);

        verify(reviewPatternFlagRepository, never()).save(any());
    }

    @Test
    void doesNotCountOrFlagOnOrdinaryNonLowScores() {
        stubCompletedTripBetweenCustomerAndDriver();

        service.submit(lowScoreRequest(4), customer);

        verify(ratingRepository, never()).countByRaterUserIdAndRateeUserIdAndScoreLessThanEqual(any(), any(), anyInt());
        verify(reviewPatternFlagRepository, never()).save(any());
    }
}
