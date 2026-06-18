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

import java.util.Map;

/**
 * CLAUDE.md §4.8 — eSMS (esms.vn) OTP adapter. Active when
 * {@code app.sms.provider=esms}. Requires {@code ESMS_API_KEY} and
 * {@code ESMS_SECRET_KEY} (both fail-loud, no defaults).
 *
 * <p>Uses {@code SmsType=2} (OTP short-code — no brandname registration needed)
 * and {@code IsUnicode=0} (GSM-7 / unaccented content fits 160 chars per SMS).
 * Set {@code ESMS_SANDBOX=1} for staging: eSMS accepts the request, logs it in
 * the dashboard, but does not actually deliver the SMS — zero credit consumed,
 * no carrier approval needed, OTP still visible in the eSMS portal.
 *
 * <p>OWASP A10: URL is a fixed constant, no user input reaches it.
 * Timeouts come from the injected builder ({@link SmsHttpConfig}).
 * No retry in adapter — same posture as {@link SpeedSmsOtpPort}.
 */
@Component
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "esms")
public class ESmsOtpPort implements SmsOtpPort {

    private static final Logger log = LoggerFactory.getLogger(ESmsOtpPort.class);

    private static final String SEND_URL =
            "https://rest.esms.vn/MainService.svc/json/SendMultipleMessage_V4_post_json/";
    private static final String SUCCESS_CODE = "100";

    private static final String MESSAGE_TEMPLATE =
            "CallMe: Ma xac thuc cua ban la %s. Ma het han sau 5 phut. KHONG chia se ma nay voi bat ky ai.";

    private final RestClient restClient;
    private final String apiKey;
    private final String secretKey;
    private final String smsType;
    private final String sandbox;

    /** eSMS response envelope — PascalCase field names match the gateway's JSON. */
    record ESmsResponse(String CodeResult, String ErrorMessage, String SMSID) {}

    public ESmsOtpPort(RestClient.Builder restClientBuilder,
                       @Value("${app.sms.esms.api-key}") String apiKey,
                       @Value("${app.sms.esms.secret-key}") String secretKey,
                       @Value("${app.sms.esms.sms-type:2}") String smsType,
                       @Value("${app.sms.esms.sandbox:0}") String sandbox) {
        this.restClient = restClientBuilder.build();
        this.apiKey = apiKey;
        this.secretKey = secretKey;
        this.smsType = smsType;
        this.sandbox = sandbox;
    }

    @Override
    public void sendOtp(String phoneNumber, String otpCode) {
        ESmsResponse response;
        try {
            response = restClient.post()
                    .uri(SEND_URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "ApiKey",    apiKey,
                            "SecretKey", secretKey,
                            "Content",   MESSAGE_TEMPLATE.formatted(otpCode),
                            "Phone",     phoneNumber,
                            "SmsType",   smsType,
                            "IsUnicode", "0",
                            "Sandbox",   sandbox))
                    .retrieve()
                    .body(ESmsResponse.class);
        } catch (RestClientException ex) {
            throw new SmsDeliveryException("eSMS gateway call failed", ex);
        }

        if (response == null || !SUCCESS_CODE.equals(response.CodeResult())) {
            String code = response == null ? "<empty body>" : response.CodeResult();
            String msg  = response == null ? "<empty body>" : response.ErrorMessage();
            log.error("eSMS rejected OTP send to {} — code={} message={}",
                    maskPhone(phoneNumber), code, msg);
            throw new SmsDeliveryException("eSMS rejected the message (code=" + code + ")");
        }

        if ("1".equals(sandbox)) {
            log.info("OTP SMS queued in eSMS SANDBOX for phone {}", maskPhone(phoneNumber));
        } else {
            log.info("OTP SMS accepted by eSMS for phone {}", maskPhone(phoneNumber));
        }
    }

    private static String maskPhone(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 3) return "***";
        return "***" + phoneNumber.substring(phoneNumber.length() - 3);
    }
}
