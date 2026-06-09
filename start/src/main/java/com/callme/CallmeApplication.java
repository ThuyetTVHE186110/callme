package com.callme;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

/** {@code @EnableScheduling} backs the periodic sweeps (e.g. CLAUDE.md B.3 — unresponsive-driver detection in the trip module). */
@EnableScheduling
@SpringBootApplication(scanBasePackages = "com.callme")
public class CallmeApplication {

    public static void main(String[] args) {
        // Windows reports the JVM timezone as "Asia/Saigon" (legacy alias) which
        // PostgreSQL rejects in the JDBC startup packet — before Flyway or Hibernate
        // even run, so profile-level config cannot intercept it. Setting the canonical
        // IANA name here, before SpringApplication starts, fixes it at the source.
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        SpringApplication.run(CallmeApplication.class, args);
    }

}
