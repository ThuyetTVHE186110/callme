package com.callme.infrastructure.sms;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * HTTP budgets for the outbound SMS gateway call (OWASP A10 / CLAUDE.md §4.8):
 * a hung gateway must fail the request in seconds — OTP issuing runs inside the
 * registration/reset transaction, and an unbounded read would pin that transaction
 * (and its DB connection) open for as long as the socket dangles.
 *
 * <p>The builder is provided as a bean here — plain spring-web, no
 * spring-boot-restclient module needed — with the request factory (timeouts) already
 * applied. Adapter classes ({@link SpeedSmsOtpPort}, {@link ESmsOtpPort}) only add
 * provider-specific auth/headers on top, deliberately NOT overriding the request
 * factory: that separation is what lets tests bind {@code MockRestServiceServer} to
 * their own builder without the adapter clobbering the mock's factory. If a future
 * integration needs different budgets, switch to named beans then.
 */
@Configuration
class SmsHttpConfig {

    private static final int CONNECT_TIMEOUT_MS = 3_000;
    private static final int READ_TIMEOUT_MS = 5_000;

    @Bean
    RestClient.Builder smsGatewayRestClientBuilder() {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        requestFactory.setReadTimeout(READ_TIMEOUT_MS);
        return RestClient.builder().requestFactory(requestFactory);
    }
}
