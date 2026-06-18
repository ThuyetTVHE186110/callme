# CallMe — Đặt người lái xe hộ

Ứng dụng backend cho dịch vụ **lái xe hộ theo yêu cầu**: khách thuê tài xế đến lái *chính chiếc xe của khách* về nhà — không phải gọi xe công nghệ thông thường.

> Phân tích nghiệp vụ đầy đủ (edge case, bất biến, quyết định thiết kế MVP) → [`CLAUDE.md`](CLAUDE.md)  
> Hướng dẫn deploy production → [`deploy/README.md`](deploy/README.md)  
> Kịch bản demo Swagger → [`docs/demo-script.md`](docs/demo-script.md)

---

## Tech stack

| Thành phần | Chi tiết |
|---|---|
| Runtime | Java 21, Spring Boot 4 |
| Database | PostgreSQL 16, Flyway (V1–V17) |
| Auth | JWT (24h TTL, per-request `tokenVersion` revocation) |
| SMS OTP | eSMS / SpeedSMS / Logging (dev) |
| Rate limiting | Bucket4j in-process |
| Monitoring | Actuator, Micrometer + Prometheus |
| Logging | Logback → JSON (logstash-logback-encoder) |
| Deploy | Docker Compose + Caddy (auto-TLS) trên DigitalOcean |
| CI/CD | GitHub Actions (`ci.yml` on PR, `cd.yml` on master) |

---

## Kiến trúc

Maven multi-module, **hexagonal (port/adapter)**:

```
common/          — ports, events, shared DTOs (không phụ thuộc module nào)
infrastructure/  — adapters: JPA, Security, SMS, Rate-limit, Web filters
modules/
  identity/      — Account, Customer, OTP, Auth
  driver/        — Driver profile, verification, availability
  location/      — GPS push, freshness tracking
  pricing/       — FareEstimation theo khung giờ (4 time-bands)
  dispatch/      — DriverMatching, DriverReservation
  booking/       — Booking lifecycle, scheduled booking
  trip/          — Trip lifecycle, SOS, incident, route-deviation
  payment/       — Payment, CASH/IN_APP, unsettled queue
  rating/        — Rating 2 chiều, retaliation flag
  notification/  — Notification fan-out
start/           — Spring Boot entry point, application.yml
```

Cross-module communication qua **Spring ApplicationEvents** (đồng bộ, cùng transaction). Notification listeners dùng `@TransactionalEventListener(AFTER_COMMIT)` để không rollback transaction nghiệp vụ khi push notification thất bại.

---

## Luồng chính

```
Khách đặt (POST /api/bookings)
  → Dispatch ghép tài xế (DriverMatchingPort)
  → BookingConfirmedEvent → Trip STARTED
  → Tài xế đến (PUT .../arrive)
  → Xác minh danh tính khách (PUT .../pick-up, identityVerified=true)
  → Lái xe (Trip IN_PROGRESS)
  → Hoàn thành (PUT .../complete) → Payment tạo
  → Tài xế xác nhận thu tiền mặt / khách xác nhận IN_APP
  → Rating 2 chiều
```

---

## Chạy local (dev)

### Yêu cầu
- Java 21
- Docker Desktop (cho PostgreSQL + integration tests)

### Khởi động

```bash
# 1. Database
docker run -d --name callme-db \
  -e POSTGRES_DB=callme -e POSTGRES_USER=callme -e POSTGRES_PASSWORD=callme \
  -p 5432:5432 postgres:16-alpine

# 2. Build + run (Spring profile dev — OTP in ra log, không cần SMS provider)
./mvnw spring-boot:run -pl start \
  -Dspring-boot.run.profiles=dev \
  -Dspring-boot.run.jvmArguments="-DJWT_SECRET=devsecret32charsminimumpad000000 -DDB_PASSWORD=callme -DCORS_ALLOWED_ORIGINS=http://localhost:3000"
```

Swagger UI: `http://localhost:8080/swagger-ui/index.html`

### OTP khi dev

Profile `dev` dùng `LoggingSmsOtpPort` — OTP in thẳng ra log:

```
[FAKE SMS] OTP 123456 for phone 0901234567 (purpose=REGISTRATION, ttl=5m)
```

### Tạo tài khoản ADMIN (dev)

```sql
INSERT INTO accounts (id, phone_number, password_hash, role, active, profile_id, token_version, phone_verified_at)
VALUES (gen_random_uuid(), '0900000000',
  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhy8',
  'ADMIN', true, gen_random_uuid(), 0, now());
-- password: admin1234
```

---

## Chạy tests

```bash
# Unit tests (không cần Docker)
./mvnw test -Dsurefire.failIfNoSpecifiedTests=false

# Integration tests (cần Docker Desktop)
./mvnw verify
```

Integration test dùng **Testcontainers** (singleton container pattern) — Spring context được cache xuyên suốt để giảm thời gian chạy.

---

## API

| Nhóm | Endpoint gốc | Role |
|---|---|---|
| Auth & OTP | `/api/auth/**` | Public |
| Account | `/api/accounts/**` | Authenticated / ADMIN |
| Booking | `/api/bookings/**` | CUSTOMER / ADMIN |
| Trip | `/api/trips/**` | DRIVER / CUSTOMER / ADMIN |
| Payment | `/api/payments/**` | DRIVER / CUSTOMER / ADMIN |
| Rating | `/api/ratings/**` | CUSTOMER / DRIVER / ADMIN |
| Driver | `/api/drivers/**` | DRIVER / ADMIN |
| Location | `/api/locations/**` | DRIVER |
| Admin queues | `/api/trips/sos`, `/incidents`, `/emergency-aborts`, `/route-deviations` | ADMIN |

Swagger đầy đủ: bật `API_DOCS_ENABLED=true` trong `.env` rồi truy cập `/swagger-ui/index.html`.

---

## Biến môi trường

| Biến | Bắt buộc | Mặc định | Mô tả |
|---|---|---|---|
| `JWT_SECRET` | Có | — | Tối thiểu 32 ký tự |
| `DB_PASSWORD` | Có | — | |
| `CORS_ALLOWED_ORIGINS` | Có | — | VD: `https://app.callme.vn` |
| `SMS_PROVIDER` | Có | — | `esms` / `speedsms` / `logging` |
| `ESMS_API_KEY` | Khi dùng eSMS | — | |
| `ESMS_SECRET_KEY` | Khi dùng eSMS | — | |
| `ESMS_SANDBOX` | Không | `0` | `1` = sandbox, không gửi SMS thật |
| `SPEEDSMS_ACCESS_TOKEN` | Khi dùng SpeedSMS | — | |
| `API_DOCS_ENABLED` | Không | `false` | Bật Swagger UI trên prod |

---

## Deploy production

Xem chi tiết tại [`deploy/README.md`](deploy/README.md).

Tóm tắt: **Docker Compose** trên một DigitalOcean droplet Ubuntu 24.04 (≥2GB RAM):

```
Internet → Caddy (TLS tự động, chặn /actuator/**) → Spring Boot app → PostgreSQL
```

CD pipeline (GitHub Actions) tự động deploy khi push vào `master`. Rollback = đổi `APP_TAG` trong `.env` về SHA cũ.

---

## Cấu trúc migrations

Flyway migrations `V1–V17` tại `infrastructure/src/main/resources/db/migration/`. Nguyên tắc: **chỉ thêm, không xóa** — rollback code không bao giờ cần rollback DB.

| Migration | Nội dung |
|---|---|
| V1–V3 | Schema cơ bản: accounts, drivers, bookings, trips, payments |
| V4 | Driver no-response tracking (strike penalty) |
| V5 | Vehicle breakdown cancellation reason |
| V6 | GPS signal lost notification type |
| V7 | Route deviation flags |
| V8 | Booking cancellation fee |
| V9 | Review pattern flags |
| V10 | Emergency abort reports |
| V11 | Booking scheduled_at (đặt lịch trước) |
| V12 | Driver verification fields |
| V13 | Incident reports |
| V14 | BookingStatus.COMPLETED (đóng lỗ hổng booking kẹt CONFIRMED) |
| V15 | Payment.driverId |
| V16 | DESTINATION_CHANGED + VERIFICATION_EXPIRED notification types |
| V17 | Phone verification + token_version (OTP §4.8) |
