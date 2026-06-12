package com.callme.payment.service.impl;

import com.callme.common.exception.ForbiddenException;
import com.callme.common.security.AccountRole;
import com.callme.common.security.AuthenticatedAccount;
import com.callme.payment.entity.Payment;
import com.callme.payment.entity.PaymentMethod;
import com.callme.payment.entity.PaymentStatus;
import com.callme.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * CLAUDE.md D.1/§4.3 — settlement attestation sides with the party who actually
 * received value: CASH is the driver's word (they collected it), IN_APP the
 * customer's. And the driver — the one standing at the kerb when the customer
 * refuses to pay — must be able to report the failure, or the D.1 CSKH queue can
 * never be fed by the scenario it was designed for.
 */
class PaymentServiceImplTest {

    private static final UUID PAYMENT_ID = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID DRIVER_ID = UUID.randomUUID();

    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final PaymentServiceImpl service = new PaymentServiceImpl(paymentRepository);

    private final AuthenticatedAccount customer = new AuthenticatedAccount(UUID.randomUUID(), CUSTOMER_ID, AccountRole.CUSTOMER);
    private final AuthenticatedAccount driver = new AuthenticatedAccount(UUID.randomUUID(), DRIVER_ID, AccountRole.DRIVER);

    private Payment pendingPayment() {
        var payment = new Payment(UUID.randomUUID(), CUSTOMER_ID, DRIVER_ID, BigDecimal.valueOf(150_000), "VND");
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        return payment;
    }

    @Test
    void theCollectingDriverConfirmsCash() {
        var payment = pendingPayment();

        service.confirm(PAYMENT_ID, PaymentMethod.CASH, driver);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(payment.getMethod()).isEqualTo(PaymentMethod.CASH);
    }

    /** A customer self-declaring "đã đưa tiền mặt" would leave every dispute with no counterparty attestation. */
    @Test
    void theCustomerCannotSelfConfirmCash() {
        var payment = pendingPayment();

        assertThatThrownBy(() -> service.confirm(PAYMENT_ID, PaymentMethod.CASH, customer))
                .isInstanceOf(ForbiddenException.class);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void thePayingCustomerConfirmsInApp() {
        var payment = pendingPayment();

        service.confirm(PAYMENT_ID, PaymentMethod.IN_APP, customer);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    void theDriverCannotConfirmOnBehalfOfTheCustomersWallet() {
        pendingPayment();

        assertThatThrownBy(() -> service.confirm(PAYMENT_ID, PaymentMethod.IN_APP, driver))
                .isInstanceOf(ForbiddenException.class);
    }

    /** CLAUDE.md D.1 — the kerb-side refusal: the driver reports it, the payment lands FAILED on the road to the CSKH queue. */
    @Test
    void theDriverCanReportTheCustomerRefusedToPay() {
        var payment = pendingPayment();

        service.markFailed(PAYMENT_ID, driver);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }
}
