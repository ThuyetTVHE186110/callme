package com.callme.payment.entity;

import com.callme.common.exception.ConflictException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CLAUDE.md flow step 10 + edge cases D.1 (refusal/no cash), D.3 (in-app retry),
 * D.4 (post-completion refund). Pure state-machine rules on the aggregate root —
 * no Spring context required to pin down the transitions and their guards.
 */
class PaymentTest {

    private Payment newPayment() {
        return new Payment(UUID.randomUUID(), UUID.randomUUID(), BigDecimal.valueOf(150_000), "VND");
    }

    @Test
    void opensAsPendingCashByDefault() {
        var payment = newPayment();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getMethod()).isEqualTo(PaymentMethod.CASH);
        assertThat(payment.getRetryCount()).isZero();
    }

    @Nested
    class Confirmation {

        @Test
        void confirmingRecordsTheActualMethodUsedAndCompletes() {
            var payment = newPayment();

            payment.confirm(PaymentMethod.IN_APP);

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
            assertThat(payment.getMethod()).isEqualTo(PaymentMethod.IN_APP);
        }

        @Test
        void cannotConfirmTwice() {
            var payment = newPayment();
            payment.confirm(PaymentMethod.CASH);

            assertThatThrownBy(() -> payment.confirm(PaymentMethod.CASH))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        void cannotConfirmAFailedPayment() {
            var payment = newPayment();
            payment.markFailed();

            assertThatThrownBy(() -> payment.confirm(PaymentMethod.CASH))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    /** CLAUDE.md D.3 — bounded retry: a failed in-app charge can come back to PENDING, but not forever. */
    @Nested
    class Retry {

        @Test
        void retryingAFailedPaymentReturnsItToPendingAndCountsTheAttempt() {
            var payment = newPayment();
            payment.markFailed();

            payment.retry();

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(payment.getRetryCount()).isEqualTo(1);
        }

        @Test
        void cannotRetryAPaymentThatIsNotFailed() {
            var payment = newPayment();

            assertThatThrownBy(payment::retry).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void escalatesToCskhOnceMaxRetriesAreExhausted() {
            var payment = newPayment();

            // Three retries are allowed (PENDING -> ... -> FAILED -> retry, three times)...
            for (int i = 0; i < 3; i++) {
                payment.markFailed();
                payment.retry();
            }
            assertThat(payment.getRetryCount()).isEqualTo(3);

            // ...the fourth must hand off to support rather than let the customer hammer "thử lại" forever.
            payment.markFailed();
            assertThatThrownBy(payment::retry)
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("tổng đài");
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
            assertThat(payment.getRetryCount()).isEqualTo(3);
        }
    }

    /** CLAUDE.md D.4 — refunds only make sense once money has actually changed hands. */
    @Nested
    class Refund {

        @Test
        void refundingACompletedPaymentMarksItRefunded() {
            var payment = newPayment();
            payment.confirm(PaymentMethod.CASH);

            payment.refund();

            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        }

        @Test
        void cannotRefundAPaymentThatWasNeverCompleted() {
            var payment = newPayment();

            assertThatThrownBy(payment::refund).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void cannotRefundTwice() {
            var payment = newPayment();
            payment.confirm(PaymentMethod.CASH);
            payment.refund();

            assertThatThrownBy(payment::refund).isInstanceOf(IllegalStateException.class);
        }
    }
}
