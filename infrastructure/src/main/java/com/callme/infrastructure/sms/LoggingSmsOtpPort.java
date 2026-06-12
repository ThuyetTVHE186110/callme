package com.callme.infrastructure.sms;

import com.callme.common.port.SmsOtpPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CLAUDE.md §4.8 — the dev/test SMS "gateway": logs the code instead of sending it,
 * and retains the latest code per phone so integration tests (and a developer poking
 * the API locally) can complete the verify step without a real SMS arriving.
 *
 * <p>Active only when {@code app.sms.provider=logging} (the default outside prod —
 * prod pins {@code esms}, so the app refuses to boot there until the real eSMS
 * adapter exists rather than silently dumping live OTPs into log files; same
 * fail-loud posture as the missing-JWT_SECRET rule). Logging real codes is exactly
 * why this class must never be the prod bean.
 */
@Component
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "logging", matchIfMissing = true)
public class LoggingSmsOtpPort implements SmsOtpPort {

    private static final Logger log = LoggerFactory.getLogger(LoggingSmsOtpPort.class);

    private final Map<String, String> lastCodeByPhone = new ConcurrentHashMap<>();

    @Override
    public void sendOtp(String phoneNumber, String otpCode) {
        lastCodeByPhone.put(phoneNumber, otpCode);
        log.info("[FAKE SMS] OTP {} for phone {} (logging provider — dev/test only)", otpCode, phoneNumber);
    }

    /** Test/dev hook — the most recent code "sent" to this phone, or null. */
    public String lastCodeFor(String phoneNumber) {
        return lastCodeByPhone.get(phoneNumber);
    }
}
