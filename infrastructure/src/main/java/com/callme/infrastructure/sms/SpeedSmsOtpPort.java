package com.callme.infrastructure.sms;

import com.callme.common.exception.SmsDeliveryException;
import com.callme.common.port.SmsOtpPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CLAUDE.md §4.8 — the first REAL SMS gateway: SpeedSMS (chosen for the free
 * integration credit; ZNS-first cost optimisation and/or eSMS are documented later
 * steps). Active only when {@code app.sms.provider=speedsms}; requires
 * {@code SPEEDSMS_ACCESS_TOKEN} (no default — boots loudly or not at all).
 *
 * <p>Defaults to {@code sms_type=4} (SpeedSMS's shared "Notify" sender), which works
 * on a fresh account with the free credit and NO registered brandname — exactly the
 * staging/soft-launch posture. Once a brandname is registered with the carriers,
 * switch via {@code SPEEDSMS_SMS_TYPE=3} + {@code SPEEDSMS_SENDER=<brand>} without
 * touching code.
 *
 * <p>OWASP A10 note — this is the codebase's first outbound HTTP call: the URL is a
 * fixed constant (no user input ever reaches it), and connect/read timeouts come from
 * the injected builder ({@link SmsHttpConfig} — kept OUT of this constructor so
 * {@code MockRestServiceServer.bindTo(builder)} in tests isn't clobbered by a
 * request-factory override) — a hung gateway can't pin the registration transaction
 * open indefinitely. Deliberately NO retry here: OTP issuing runs inside the caller's
 * transaction, and the user-facing 503 ("thử lại sau") plus the resend endpoint
 * already provide the retry path with a human pacing it.
 *
 * <p>Unlike {@link LoggingSmsOtpPort}, this class must never log the code itself.
 */
@Component
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "speedsms")
public class SpeedSmsOtpPort implements SmsOtpPort {

    private static final Logger log = LoggerFactory.getLogger(SpeedSmsOtpPort.class);

    private static final String SEND_URL = "https://api.speedsms.vn/index.php/sms/send";
    private static final String SUCCESS_STATUS = "success";

    /** Unaccented on purpose: fits the 160-char single-SMS GSM budget (accented Vietnamese drops it to 70). */
    private static final String MESSAGE_TEMPLATE =
            "CallMe: Ma xac thuc cua ban la %s. Ma het han sau 5 phut. KHONG chia se ma nay voi bat ky ai.";

    private final RestClient restClient;
    private final int smsType;
    private final String sender;

    /** Gateway response envelope — Jackson ignores unknown fields by default. */
    record SpeedSmsSendResponse(String status, String code, String message) {
    }

    public SpeedSmsOtpPort(RestClient.Builder restClientBuilder,
                           @Value("${app.sms.speedsms.access-token}") String accessToken,
                           @Value("${app.sms.speedsms.sms-type:4}") int smsType,
                           @Value("${app.sms.speedsms.sender:}") String sender) {
        this.restClient = restClientBuilder
                // SpeedSMS Basic auth: the access token is the username, password is the literal "x".
                .defaultHeaders(headers -> headers.setBasicAuth(accessToken, "x"))
                .build();
        this.smsType = smsType;
        // sms_type=4 is SpeedSMS's shared "Notify" brand — sender must be the literal
        // string "Notify". A blank sender causes "sender not found" rejection.
        this.sender = (sender == null || sender.isBlank()) ? "Notify" : sender;
    }

    @Override
    public void sendOtp(String phoneNumber, String otpCode) {
        var body = new HashMap<String, Object>();
        body.put("to", List.of(phoneNumber));
        body.put("content", MESSAGE_TEMPLATE.formatted(otpCode));
        body.put("sms_type", smsType);
        body.put("sender", sender);

        SpeedSmsSendResponse response;
        try {
            response = restClient.post()
                    .uri(SEND_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(SpeedSmsSendResponse.class);
        } catch (RestClientException ex) {
            // Covers non-2xx statuses, timeouts and connection failures alike.
            throw new SmsDeliveryException("SpeedSMS gateway call failed", ex);
        }

        if (response == null || !SUCCESS_STATUS.equals(response.status())) {
            // The gateway answered 200 but refused the message (bad token, out of
            // credit, blocked number...). Log status+code for ops; never surface to client.
            String gatewayStatus  = response == null ? "<empty body>" : response.status();
            String gatewayCode    = response == null ? "<empty body>" : response.code();
            String gatewayMessage = response == null ? "<empty body>" : response.message();
            log.error("SpeedSMS rejected OTP send to {} — status={} code={} message={}",
                    maskPhone(phoneNumber), gatewayStatus, gatewayCode, gatewayMessage);
            throw new SmsDeliveryException("SpeedSMS rejected the message (status=" + gatewayStatus + ")");
        }
        log.info("OTP SMS accepted by SpeedSMS for phone {}", maskPhone(phoneNumber));
    }

    private static String maskPhone(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 3) {
            return "***";
        }
        return "***" + phoneNumber.substring(phoneNumber.length() - 3);
    }
}
