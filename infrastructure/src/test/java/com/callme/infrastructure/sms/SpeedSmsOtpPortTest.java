package com.callme.infrastructure.sms;

import com.callme.common.exception.SmsDeliveryException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * CLAUDE.md §4.8 — the SpeedSMS adapter's contract with the gateway: Basic auth
 * (token as username, literal "x" password), the documented JSON shape, and the two
 * failure modes that must surface as {@link SmsDeliveryException} so the caller's
 * transaction rolls back — an HTTP error, and a 200 whose body says "error" (out of
 * credit, bad token...), which a naive adapter would happily swallow.
 */
class SpeedSmsOtpPortTest {

    private static final String SEND_URL = "https://api.speedsms.vn/index.php/sms/send";
    private static final String TOKEN = "test-access-token";
    private static final String PHONE = "0901234567";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final SpeedSmsOtpPort port = new SpeedSmsOtpPort(builder, TOKEN, 4, "");

    private static String expectedBasicAuth() {
        return "Basic " + Base64.getEncoder().encodeToString((TOKEN + ":x").getBytes());
    }

    @Test
    void sendsTheDocumentedPayloadWithBasicAuth() {
        server.expect(requestTo(SEND_URL))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("Authorization", expectedBasicAuth()))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.to[0]").value(PHONE))
                .andExpect(jsonPath("$.sms_type").value(4))
                .andExpect(jsonPath("$.content").value(org.hamcrest.Matchers.containsString("123456")))
                .andRespond(withSuccess("{\"status\":\"success\",\"code\":\"00\",\"data\":{\"tranId\":1,\"totalSMS\":1,\"totalPrice\":500,\"invalidPhone\":[]}}",
                        MediaType.APPLICATION_JSON));

        assertThatCode(() -> port.sendOtp(PHONE, "123456")).doesNotThrowAnyException();
        server.verify();
    }

    /** A 200 with an error body (bad token, out of credit) is still a delivery failure — it must roll the caller back. */
    @Test
    void aGatewayLevelRejectionRaisesSmsDeliveryException() {
        server.expect(requestTo(SEND_URL))
                .andRespond(withSuccess("{\"status\":\"error\",\"code\":\"007\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> port.sendOtp(PHONE, "123456"))
                .isInstanceOf(SmsDeliveryException.class);
    }

    @Test
    void anHttpErrorRaisesSmsDeliveryException() {
        server.expect(requestTo(SEND_URL))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> port.sendOtp(PHONE, "123456"))
                .isInstanceOf(SmsDeliveryException.class);
    }
}
