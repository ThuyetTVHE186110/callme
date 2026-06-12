package com.callme.common.port;

/**
 * CLAUDE.md §4.8 — outbound SMS delivery for one-time codes. Published in `common`
 * so the identity module's OTP flow never knows which gateway is behind it: the
 * provider (eSMS — chosen for prod; Twilio/SpeedSMS as alternatives) is an
 * infrastructure detail selected via `app.sms.provider`, and dev/test run on a
 * logging fake. The port carries only the minimum — who and what code; message
 * composition/branding belongs to the implementation, not the caller.
 */
public interface SmsOtpPort {

    void sendOtp(String phoneNumber, String otpCode);
}
