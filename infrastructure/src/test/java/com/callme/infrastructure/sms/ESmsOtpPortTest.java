package com.callme.infrastructure.sms;

import com.callme.common.exception.SmsDeliveryException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ESmsOtpPortTest {

    private static final String SEND_URL =
            "https://rest.esms.vn/MainService.svc/json/SendMultipleMessage_V4_post_json/";
    private static final String API_KEY    = "test-api-key";
    private static final String SECRET_KEY = "test-secret-key";
    private static final String PHONE      = "0901234567";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final ESmsOtpPort port = new ESmsOtpPort(builder, API_KEY, SECRET_KEY, "2", "0");

    @Test
    void sendsDocumentedPayload() {
        server.expect(requestTo(SEND_URL))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.ApiKey").value(API_KEY))
                .andExpect(jsonPath("$.SecretKey").value(SECRET_KEY))
                .andExpect(jsonPath("$.Phone").value(PHONE))
                .andExpect(jsonPath("$.SmsType").value("2"))
                .andExpect(jsonPath("$.IsUnicode").value("0"))
                .andExpect(jsonPath("$.Sandbox").value("0"))
                .andExpect(jsonPath("$.Content").value(org.hamcrest.Matchers.containsString("123456")))
                .andRespond(withSuccess(
                        "{\"CodeResult\":\"100\",\"ErrorMessage\":\"success\",\"SMSID\":\"abc123\"}",
                        MediaType.APPLICATION_JSON));

        assertThatCode(() -> port.sendOtp(PHONE, "123456")).doesNotThrowAnyException();
        server.verify();
    }

    @Test
    void gatewayRejectionRaisesSmsDeliveryException() {
        server.expect(requestTo(SEND_URL))
                .andRespond(withSuccess(
                        "{\"CodeResult\":\"99\",\"ErrorMessage\":\"ApiKey không tồn tại\",\"SMSID\":\"\"}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> port.sendOtp(PHONE, "123456"))
                .isInstanceOf(SmsDeliveryException.class);
    }

    @Test
    void httpErrorRaisesSmsDeliveryException() {
        server.expect(requestTo(SEND_URL))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> port.sendOtp(PHONE, "123456"))
                .isInstanceOf(SmsDeliveryException.class);
    }
}
