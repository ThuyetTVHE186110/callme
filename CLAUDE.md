# CLAUDE.md — Phân tích nghiệp vụ: Đặt người lái xe hộ (Designated Driver Booking)

> Tài liệu này phân tích sâu nghiệp vụ của domain "đặt người lái xe hộ" — dịch vụ
> khách hàng thuê tài xế đến lái **chính chiếc xe của khách** đưa khách (và xe) về nhà,
> thường vào ban đêm khi khách không đủ tỉnh táo để tự lái (sau tiệc, nhậu...).
>
> Đây **không phải** mô hình gọi xe (Grab/Uber) — khác biệt cốt lõi: tài xế không mang
> phương tiện của mình đến chở khách, mà **lái xe của khách**. Sự khác biệt này kéo theo
> hàng loạt hệ quả nghiệp vụ (an toàn, trách nhiệm pháp lý, hành trình về của tài xế...)
> được phân tích chi tiết bên dưới — đây là phần "tinh túy" cần nắm để thiết kế đúng các
> module còn lại (`identity`, `dispatch`, `location`, `pricing`, `payment`, `rating`,
> `notification`).

## 1. Actor & vai trò

| Actor | Mô tả | Đặc điểm cần lưu ý |
|---|---|---|
| **Khách hàng (Customer)** | Người đặt dịch vụ, chủ xe | Thường **đang say / mệt mỏi** lúc đặt và lúc đi — giảm khả năng cung cấp thông tin chính xác, tăng rủi ro hành vi bất thường |
| **Tài xế (Driver)** | Người được thuê để lái xe của khách | Phải có kỹ năng lái **nhiều loại xe khác nhau** (không phải xe quen thuộc của mình) — rủi ro cao hơn tài xế taxi thông thường |
| **Người lái xe theo sau (Follow-rider)** | (Tùy mô hình) người chở tài xế quay về sau khi hoàn thành | Có thể là 1 nhân sự riêng, hoặc chính tài xế tự đi xe máy gấp gọn mang theo |
| **Hệ thống điều phối (Dispatch)** | Module ghép tài xế với khách | Phải tối ưu cả khoảng cách đến điểm đón **lẫn** hành trình quay về |
| **Tổng đài / CSKH (Support)** | Xử lý sự cố, khiếu nại, tình huống khẩn | Vai trò quan trọng hơn hẳn so với app gọi xe vì rủi ro va chạm với tài sản (xe) và con người say xỉn cao hơn |

## 2. Vòng đời chính — Main Flow (Happy Path)

```
[1] Khách mở app → nhập điểm đón (vị trí khách + xe), điểm đến, loại xe
        ↓
[2] Hệ thống ước tính cước (khoảng cách, khung giờ đêm/khuya, loại xe, phí "xe theo sau")
        ↓
[3] Khách xác nhận đặt → tạo Booking (status = PENDING)
        ↓
[4] Dispatch tìm tài xế khả dụng quanh điểm đón
        (DriverAvailabilityPort.findAvailableNear — đã có trong skeleton hiện tại)
        ↓
[5] Gửi yêu cầu tới (các) tài xế ứng viên — tài xế chấp nhận trong khung thời gian quy định
        ↓
[6] Tài xế xác nhận nhận chuyến → Booking.confirmWithDriver(driverId) → status = CONFIRMED
        ↓ publish BookingConfirmedEvent
[7] Trip module lắng nghe sự kiện → tạo Trip (status = STARTED) — "tài xế đang đến điểm đón"
        ↓
[8] Tài xế đến nơi → xác nhận gặp khách, kiểm tra xe (giấy tờ, xăng, tình trạng xe)
        → Trip chuyển IN_PROGRESS — "đang lái xe khách đến điểm đến"
        ↓
[9] Tài xế lái xe khách đến điểm đến → xác nhận hoàn thành
        → Trip = COMPLETED, tính cước cuối cùng (có thể khác ước tính ban đầu)
        ↓
[10] Thanh toán (tiền mặt tại chỗ hoặc qua app)
        ↓
[11] Khách & tài xế đánh giá lẫn nhau (rating 2 chiều)
        ↓
[12] Tài xế kết thúc ca: tự di chuyển về / được "xe theo sau" đón / nhận chuyến mới gần đó
```

### Bất biến nghiệp vụ (invariants) cần giữ xuyên suốt
- Một `Booking` chỉ được gán cho **đúng một** `Driver` tại một thời điểm (tránh race condition khi nhiều tài xế cùng "accept").
- `Trip` chỉ được tạo **sau khi** `Booking` chuyển `CONFIRMED` — không có Trip mồ côi.
- Không cho phép Trip nhảy trạng thái ngược (`COMPLETED` → `IN_PROGRESS`) hay nhảy cóc (`STARTED` → `COMPLETED` mà bỏ qua `IN_PROGRESS`) trừ khi có lý do hủy/sự cố tường minh.
- Tài xế không thể đồng thời "online/available" cho booking mới trong khi đang có Trip ở trạng thái `IN_PROGRESS`.

## 3. Edge case — phân theo từng giai đoạn

### A. Giai đoạn đặt chuyến (Booking creation)
1. **Không tìm thấy tài xế nào khả dụng** quanh điểm đón → Booking nên có trạng thái trung gian (vd. `SEARCHING` / `NO_DRIVER_FOUND`) thay vì rơi thẳng vào `PENDING` vô thời hạn; cần cơ chế mở rộng bán kính tìm kiếm theo thời gian hoặc thông báo khách chủ động.
2. **Khách đang say, nhập sai vị trí/điểm đến** — cần xác thực địa chỉ qua bản đồ (location module), cho phép chỉnh sửa trước khi tài xế xuất phát, và cảnh báo nếu vị trí GPS lệch quá xa địa chỉ nhập tay.
3. **Đặt trùng lặp** do mạng chập chờn (request gửi 2 lần) → cần idempotency key khi tạo Booking.
4. **Giá ước tính thay đổi** giữa lúc xem báo giá và lúc xác nhận (phụ phí giờ cao điểm / khuya) — cần "khóa giá" trong khoảng thời gian xác nhận hoặc hiển thị lại giá mới để khách đồng ý.

   > **Quyết định (đã chốt — không khóa giá):** Hệ thống cố ý **không khóa giá**. `BookingServiceImpl.requestDriverHome` báo giá tại thời điểm tạo booking (`fareEstimationPort.estimate(pickup, destination, Instant.now())`, lưu vào `Booking.estimatedFareAmount/Currency`); `TripServiceImpl.complete` **re-quote lại** tại thời điểm hoàn thành thực tế (`Instant.now()` lúc đó) để tính `finalFareAmount` — đúng tinh thần CLAUDE.md §4.4 "tính cước cuối cùng tại thời điểm hoàn thành: chuyến chạy qua nửa đêm phải chịu đúng phụ phí giờ khuya áp dụng lúc nó *thực sự* diễn ra". "Khóa giá" theo nghĩa đen sẽ mâu thuẫn trực tiếp với nguyên tắc đó — một chuyến đặt lúc 21h nhưng tài xế đến muộn, lái đến 1h sáng, khóa giá ở mức 21h sẽ vừa thiệt cho công ty (bỏ qua phụ phí khuya thực tế) vừa tạo kẽ hở để khách cố tình trì hoãn hưởng giá rẻ. Thay vào đó, hệ thống đáp ứng đúng nhu cầu cốt lõi của A.4 — "khách không bị bất ngờ về giá, và có bằng chứng khi tranh chấp" — bằng cách lưu **cả hai** mốc giá (`estimatedFareAmount` lúc đặt, `finalFareAmount` lúc hoàn thành) làm dấu vết đối soát cho D.2, đồng thời publish lại quote mới ngay khi khách đổi điểm đến giữa chừng (`changeDestination` — xem C.5) để khách luôn biết giá hiện hành trước khi nó chốt. Không cần thêm cơ chế khóa/giữ giá riêng.
5. **Khách hủy ngay sau khi đặt**, trước khi có tài xế nhận — phải là thao tác miễn phí, không tính phí hủy.
6. **Khách đặt nhiều chuyến cùng lúc** (vd. cho cả nhóm bạn nhậu) — có nên giới hạn số booking đang hoạt động/khách?

### B. Giai đoạn ghép tài xế (Dispatch / Matching)
1. **Race condition**: nhiều tài xế cùng "accept" một booking cùng lúc → cần cơ chế khóa (optimistic lock trên `Booking.version`, hoặc gán theo nguyên tắc "ai chạm trước thắng" ở tầng transaction) để đảm bảo chỉ một tài xế được gán.
2. **Tài xế "ảo"**: trạng thái `online = true` trong DB nhưng vị trí không cập nhật / thiết bị mất kết nối → ứng viên trả về không thực sự khả dụng. Cần kèm điều kiện "vị trí cập nhật trong N phút gần nhất".
3. **Timeout không phản hồi**: tài xế nhận yêu cầu nhưng không accept/reject trong khung thời gian quy định → tự động chuyển sang ứng viên kế tiếp, đồng thời hạ điểm ưu tiên hiển thị của tài xế đó cho các lần sau.
   > **Đã triển khai (đã điều chỉnh theo kiến trúc thực tế):** hệ thống dùng mô hình "ghép nối nguyên tử" (`DriverMatchingPort` + `DriverReservationPort` — tài xế được *reserve* ngay khi ghép, không có cửa sổ broadcast-rồi-chờ-accept/reject như mô tả gốc, vì vậy race "nhiều tài xế cùng accept" không thể xảy ra — xem B.1). Hệ quả là điểm thất bại tương đương không phải "không accept trong N giây" mà là "đã được ghép/reserve nhưng biến mất, không di chuyển đến điểm đón". `TripServiceImpl.sweepUnresponsiveDrivers` (chạy định kỳ mỗi phút, `DRIVER_RESPONSE_TIMEOUT = 15 phút`) phát hiện các `Trip` còn ở `STARTED` quá lâu, hủy qua đúng luồng tái điều phối B.4 (`DriverCancelledBeforePickupEvent` — không hủy yêu cầu của khách), gắn lý do `CancellationReason.DRIVER_UNRESPONSIVE` (không tính phí cho khách), và phát `DriverUnresponsiveEvent` để `driver` module ghi nhận `Driver.noResponseStrikes`. `DriverMatchingPortImpl` sắp xếp ứng viên theo `noResponseStrikes` trước, khoảng cách sau — đúng tinh thần "hạ điểm ưu tiên hiển thị của tài xế đó cho các lần sau". Migration: `V4__driver_no_response_tracking.sql`.
4. **Tài xế hủy ngay sau khi nhận** (trước khi đến điểm đón) → Booking phải quay lại trạng thái tìm tài xế mới, **không hủy luôn** chuyến của khách; cần thông báo lại cho khách về độ trễ phát sinh.
5. **Bán kính tìm kiếm**: khu vực ít tài xế (ngoại thành, giờ khuya muộn) → thời gian chờ kéo dài; cân nhắc mở rộng bán kính động theo thời gian chờ hoặc chấp nhận phụ phí "khu vực xa".
6. **Đồng bộ trạng thái "đang chạy chuyến khác"**: dữ liệu vị trí/trạng thái tài xế có độ trễ → hệ thống có thể gán nhầm một tài xế đang `IN_PROGRESS` ở chuyến khác.

### C. Giai đoạn thực hiện chuyến đi (Trip execution) — đặc thù riêng của "lái xe hộ"
Đây là phần khác biệt lớn nhất so với app gọi xe thông thường, vì tài xế phải **điều khiển tài sản của khách** trong khi khách (chủ tài sản) **không đủ tỉnh táo** để giám sát:

1. **Khách không có mặt tại điểm hẹn** (no-show) — say đến mức ngủ quên, lạc đường, hoặc người khác gọi hộ — cần quy trình chờ + phí chờ + hủy sau ngưỡng thời gian.
2. **Khách quá say, không thể giao chìa khóa / cung cấp thông tin xe** — tài xế cần quy trình xác minh khách đúng là chủ xe (đối chiếu thông tin đặt chuyến) trước khi nhận xe, tránh rủi ro pháp lý "lái xe không phép".
3. **Xe của khách hỏng hóc / không khởi động được** — đây là rủi ro của khách (không phải tài xế), cần quy trình xử lý: đổi sang phương án gọi cứu hộ, hoặc đổi loại dịch vụ (gọi taxi thường), tính phí ra sao cho công bằng.
   > **Đã triển khai:** `TripService.reportVehicleBreakdown` (`PUT /api/trips/{id}/vehicle-breakdown`, chỉ tài xế được phân công, chỉ hợp lệ từ `ARRIVED_AT_PICKUP`/`IN_PROGRESS` — tức là tài xế đã thực sự tiếp cận xe). Hủy chuyến với lý do riêng `CancellationReason.VEHICLE_BREAKDOWN` — **không** đi qua `classifyCancellation` (vốn quy trách nhiệm cho khách hoặc tài xế) vì đây là rủi ro tài sản của khách, không phải lỗi của bên nào — đảm bảo không bên nào bị tính phí hủy oan, đồng thời để lại dấu vết kiểm toán riêng cho luồng "gọi cứu hộ / đổi taxi thường" mà CSKH xử lý tiếp. Migration: `V5__vehicle_breakdown_cancellation_reason.sql`. *(Câu hỏi "tính phí ra sao cho công bằng" với quãng đường đã đi thuộc về `pricing`/`payment` — vẫn là việc cần làm tiếp khi engine tính phí từng phần được thiết kế.)*
4. **Tai nạn / va chạm trong khi tài xế cầm lái xe khách** — vấn đề **trách nhiệm pháp lý và bảo hiểm** cực kỳ nhạy cảm: ai chịu trách nhiệm (tài xế, công ty điều phối, bảo hiểm xe của khách)? Cần xác định rõ chính sách bảo hiểm bắt buộc cho tài xế/chuyến đi trước khi go-live — đây là rủi ro kinh doanh, không chỉ kỹ thuật.
5. **Khách đổi điểm đến giữa chừng** — phải hỗ trợ cập nhật lộ trình + tính lại cước, đồng thời ghi log để tránh tranh chấp sau này.
6. **Khách có hành vi gây rối / quấy rối tài xế** (do say xỉn) — cần nút SOS / báo khẩn trong app, ghi âm/ghi hình hành trình (nếu pháp luật cho phép), quy trình hỗ trợ khẩn 24/7.
7. **Mất tín hiệu GPS / kết nối mạng giữa hành trình** — không theo dõi được lộ trình thực tế, ảnh hưởng tính cước và an toàn; cần cơ chế đồng bộ lại khi có kết nối + cảnh báo nếu mất tín hiệu quá lâu.

   > **Đã triển khai:** `TripServiceImpl.sweepStaleGpsTrips` — job định kỳ (`@Scheduled(fixedDelayString = "PT1M")`, `GPS_SWEEP_INTERVAL`) quét mọi `Trip` đang `IN_PROGRESS` (driver đang cầm lái xe khách — giai đoạn nhạy cảm nhất), tra cứu mốc cập nhật vị trí gần nhất qua cổng `DriverLocationFreshnessPort` (impl: `DriverLocationFreshnessPortImpl`, đọc `Driver.lastLocationUpdatedAt`). Nếu mốc đó cũ hơn `GPS_LOSS_THRESHOLD = 3 phút`, hệ thống phát `GpsSignalLostEvent` đúng **một lần** nhờ kỹ thuật "crossed-threshold window" — không cần cờ trạng thái đã-cảnh-báo trong DB: chỉ bắn sự kiện khi mốc cập nhật rơi vào khoảng `(staleSince - sweepInterval, staleSince)`, một cửa sổ hẹp mà một timestamp "đứng yên" (vì driver mất tín hiệu, không còn cập nhật) chỉ đi qua đúng một lần rồi "già" ra khỏi cửa sổ ở vòng quét kế tiếp. `DomainEventListener.onGpsSignalLost` đẩy thông báo tới **cả khách lẫn tài xế** (`NotificationType.GPS_SIGNAL_LOST`, migration `V6__gps_signal_lost_notification_type.sql` mở rộng `notifications_type_check`) — khách được biết tài xế/xe của mình có thể đang gặp sự cố, tài xế được nhắc kiểm tra kết nối để chuyến đi được ghi nhận chính xác (tránh tranh chấp cước sau này — liên hệ D.2). Lưu ý: ngưỡng `GPS_LOSS_THRESHOLD = 3 phút` ở đây là ngưỡng *giám sát an toàn giữa chuyến*, khác với `DriverAvailabilityPortImpl.LOCATION_FRESHNESS_WINDOW = 5 phút` dùng cho mục đích *ghép tài xế* (xem ghi chú G.3 bên dưới — không nhầm lẫn hai ngưỡng).
8. **Tài xế cố tình đi đường vòng để tăng cước** (gian lận) — cần so sánh lộ trình thực tế với lộ trình tối ưu được đề xuất, cảnh báo nếu lệch quá ngưỡng.

   > **Đã triển khai (điều chỉnh theo hạ tầng thực tế — không có dịch vụ chỉ đường):** hệ thống **chưa** tích hợp dịch vụ định tuyến (Google Maps/HERE/OSRM...) nên không có khái niệm "lộ trình tối ưu được đề xuất" để so khớp rẽ-từng-khúc — `pricing` chỉ tính cước trên khoảng cách đường chim bay (`GeoPoint.distanceKm`, xem `FareEstimationPort`). Thay vì xây dựng hạ tầng định tuyến mới (tốn kém, thêm phụ thuộc ngoài, không phải trọng tâm MVP), heuristic khả thi ngay với dữ liệu sẵn có: **so sánh quãng đường GPS thực tế đã đi với quãng đường đường-chim-bay đã tính cước**. Cụ thể — `TripServiceImpl.checkRouteDeviation` (gọi từ `complete()`) dùng cổng mới `DriverRouteTracePort` (impl `DriverRouteTracePortImpl` ở module `location`, cộng dồn khoảng cách giữa các điểm GPS liên tiếp trong `location_updates` — bảng vốn đã lưu lịch sử đầy đủ cho mục đích đối soát D.2) để tính tổng quãng đường tài xế đã đi trong khoảng từ lúc cầm lái (`Trip.identityVerifiedAt` — CLAUDE.md C.2) đến lúc hoàn thành. Nếu tỉ lệ `thực tế / đường chim bay` vượt `ROUTE_DEVIATION_RATIO_THRESHOLD = 1.5` (và quãng đường tính cước đủ lớn — `MIN_FLAGGABLE_DISTANCE_KM = 1 km`, để nhiễu GPS trên các chuyến ngắn không tạo cảnh báo giả), hệ thống lưu một `RouteDeviationFlag` (bảng `route_deviation_flags`, migration `V7__route_deviation_flags.sql`) vào hàng đợi CSKH (`GET /api/trips/route-deviations`, chỉ `ADMIN`) — **không** tự động trừ điểm hay từ chối thanh toán, vì đường vòng thật (kẹt xe, đường cấm, đưa khách qua nhiều điểm) tạo ra đúng tín hiệu giống gian lận; phân biệt hai trường hợp này cần con người xem xét lộ trình GPS đầy đủ. Mẫu thiết kế "bản ghi phẳng append-only + worklist admin" cố tình giống hệt `SosAlert`/`listSosAlerts` (C.6) để nhất quán cách CSKH xử lý các loại cảnh báo tự động. *(Khi sản phẩm đủ trưởng thành để tích hợp dịch vụ định tuyến, đây chính là điểm mở rộng: thay "đường chim bay" bằng "lộ trình tối ưu đề xuất" mà không đổi luồng nghiệp vụ xung quanh.)*
9. **"Xe theo sau" bị lạc / đến trễ** — nếu mô hình có người chở tài xế về, cần điều phối song song hành trình của 2 phương tiện, xử lý khi chúng tách rời nhau.

### D. Giai đoạn hoàn thành & thanh toán
1. **Khách từ chối thanh toán hoặc không đủ tiền mặt** lúc kết thúc (đặc biệt vì khách say) — cần chính sách: giữ giấy tờ tạm thời? thanh toán trễ qua app? báo cáo CSKH?
   > **Quyết định & đã triển khai (chọn nhánh "báo cáo CSKH"):** Trong 3 phương án gợi ý, "giữ giấy tờ tạm thời" mang rủi ro pháp lý/tranh chấp tài sản quá lớn cho một thao tác tự phục vụ (ai giữ? trả lại khi nào? mất thì sao?), còn "thanh toán trễ qua app" **đã có sẵn** đường thoát qua `Payment.retry`/`PaymentMethod.IN_APP` (D.3, `MAX_RETRIES = 3`). Phần còn thiếu thực sự chỉ là **"báo cáo CSKH"** — một hàng đợi để con người can thiệp khi cả thiện chí tự xử lẫn số lần thử lại đều đã cạn. `Payment.retry()` vốn đã từ chối với thông điệp trỏ tới "liên hệ tổng đài" khi `retryCount >= MAX_RETRIES` (D.3) — nhưng trước đây không có nơi nào để CSKH *tìm* đúng các khoản đó. Nay `PaymentRepository.findByStatusAndRetryCountGreaterThanEqualOrderByIdDesc(FAILED, Payment.MAX_RETRIES)` truy đúng tập "thanh toán tiền mặt bị từ chối/không đủ tiền và đã hết lượt thử lại" — chính là tập D.1 mô tả — phơi ra qua `PaymentService.listUnsettled` / `GET /api/payments/unsettled` (admin-only, mirror `listSosAlerts`/`listComplaints`/`listRouteDeviationFlags`: hàng đợi phẳng, không tự động hành động, để CSKH quyết định đối soát/thu hồi công nợ ra sao theo từng ca cụ thể). `Payment.MAX_RETRIES` được nâng lên `public` để cùng một ngưỡng "đã cạn kiên nhẫn hệ thống" được dùng nhất quán ở cả hai nơi (entity guard + repository query) — không trùng lặp magic number.
2. **Tranh chấp về cước phí** — khách (lúc tỉnh) cho rằng quãng đường/thời gian bị tính sai — cần lưu vết đầy đủ lộ trình GPS + log thời gian từng mốc trạng thái Trip làm bằng chứng đối soát.
3. **Thanh toán qua app thất bại** (timeout cổng thanh toán, thẻ bị từ chối) — cần cơ chế retry + đối soát công nợ, không chặn tài xế nhận chuyến mới vì lỗi thanh toán của khách trước.
4. **Yêu cầu hoàn tiền sau khi hoàn thành** — quy trình khiếu nại + hoàn tiền cần SLA rõ ràng.

### E. Hủy chuyến (Cancellation)
1. **Khách hủy sau khi tài xế đã xác nhận và đang di chuyển đến điểm đón** — nên có phí hủy để bù chi phí di chuyển của tài xế, nhưng cần ngưỡng thời gian hợp lý (hủy trong 1 phút đầu vẫn miễn phí).
   > **Đã triển khai:** `Booking` ghi nhận `confirmedAt` (đặt trong `confirmWithDriver` — đúng thời điểm tài xế được ghép và bắt đầu "tốn chi phí" thay vì từ lúc tạo booking, vì hủy trước khi có tài xế không tốn gì cả — A.5 vẫn miễn phí vô điều kiện). `Booking.cancel` áp phí hủy **cố định** `CANCELLATION_FEE_AMOUNT = 20.000 VND` (không tỷ lệ theo cước — cái cần bù là chi phí tài xế chạy không tới điểm đón, một chi phí gần như không đổi bất kể cuốc xe lớn hay nhỏ) khi và chỉ khi **cả ba** điều kiện đúng: lý do là `CUSTOMER_REQUEST` (mọi lý do khác — `DRIVER_REQUEST`/`FORCE_MAJEURE`/`SYSTEM_CASCADE`/`CUSTOMER_NO_SHOW`/`DRIVER_UNRESPONSIVE`/`VEHICLE_BREAKDOWN` — đã miễn trừ trách nhiệm cho khách theo định nghĩa của chính chúng), booking đang `CONFIRMED` (chưa có tài xế thì chưa tốn gì), và đã vượt `CANCELLATION_GRACE_PERIOD = 1 phút` kể từ `confirmedAt` — đúng ví dụ minh họa "1 phút đầu miễn phí" trong edge case này. Phí được lưu trực tiếp trên `Booking` (`cancellationFeeAmount`/`cancellationFeeCurrency`, nullable) thay vì đi qua `Payment` — vốn được nối cứng 1:1 với `tripId` của một chuyến đã hoàn thành (`PaymentRepository.findByTripId` là unique index, xem D.1) — nên việc thu hộ/đối soát khoản phí này (CSKH thu sau, trừ vào số dư tài xế...) vẫn là việc còn lại của `payment` khi engine thu phí từng phần được thiết kế; ở đây hệ thống chỉ đảm bảo **chính sách được áp đúng và để lại dấu vết minh bạch** (giảm tranh chấp D.2). `confirmWithDriver`/`cancel` nhận `Instant now` tường minh (giống `Rating#edit`) để chính sách dựa-trên-thời-gian này kiểm thử được tất định. Migration: `V8__booking_cancellation_fee.sql`.
2. **Tài xế hủy giữa chừng khi đã ở trạng thái `IN_PROGRESS`** (đang cầm lái xe khách!) — đây là tình huống **nghiêm trọng nhất** trong toàn bộ domain: tài xế không thể đơn giản "bỏ chuyến" giữa đường vì đang điều khiển tài sản của khách. Cần quy trình đặc biệt: tài xế phải đưa xe + khách đến nơi an toàn trước khi được phép kết thúc bất thường, có thể cần điều phối tài xế thay thế đến tiếp ứng tại chỗ.

   > **Đã triển khai (đóng kẽ hở — trước đây `cancel` cho phép tài xế "bỏ chuyến" tùy tiện ngay cả khi `IN_PROGRESS`):** Rà soát lại nghiệp vụ phát hiện `TripService.cancel` (lối hủy thông thường) trước đây **không** chặn tài xế tự hủy khi đang `IN_PROGRESS` — đúng cái "lối thoát tùy tiện" mà chính đoạn edge-case này cảnh báo không được phép tồn tại (chỉ được tài liệu hóa bằng comment, chưa được chặn bằng code). Nay `TripServiceImpl.cancel` **từ chối thẳng** mọi yêu cầu hủy do chính tài xế được phân công khởi xướng khi `status = IN_PROGRESS` (`ForbiddenException`, hướng họ sang quy trình chuyên biệt bên dưới); khách hàng và CSKH/admin (`FORCE_MAJEURE`) vẫn hủy được như cũ — đây là tình huống của riêng tài xế đang cầm lái, không phải của khách. Lối ra hợp pháp duy nhất cho tài xế là `TripService.abortInProgressTrip` (`PUT /api/trips/{id}/abort-in-progress`, chỉ tài xế được phân công, chỉ hợp lệ từ `IN_PROGRESS`): toạ độ `safeLatitude`/`safeLongitude` trong request **chính là lời xác nhận** "tôi đã đưa xe và khách đến nơi an toàn *trước khi* kết thúc chuyến" — không phải một yêu cầu xin phép, mô phỏng đúng cách `PickUpCustomerRequest.identityVerified` (C.2) buộc xác nhận tại biên API trước khi chạm vào guard nghiệp vụ. Hủy với lý do riêng `CancellationReason.DRIVER_EMERGENCY_ABORT` — tách biệt khỏi `DRIVER_REQUEST` (không bị tính là "bỏ chuyến tùy tiện", không áp phí hủy nào) — và ghi một `EmergencyAbortReport` (bảng `emergency_abort_reports`, mô phỏng đúng mẫu `SosAlert`/`RouteDeviationFlag`/`ReviewPatternFlag`: bản ghi phẳng append-only) vào hàng đợi CSKH (`GET /api/trips/emergency-aborts`, chỉ `ADMIN`). Cố tình **không** tự động điều phối tài xế thay thế đến vị trí an toàn — đúng tinh thần "có thể cần" trong mô tả gốc (không phải "luôn luôn cần"): mỗi tình huống khẩn cấp giữa đường là khác nhau (xe cháy, tai nạn, khách lên cơn bệnh...), chỉ con người mới đủ ngữ cảnh để quyết định có cần điều xe tiếp ứng hay không, giống hệt cách C.3 để CSKH quyết định "gọi cứu hộ hay đổi taxi thường". `TripAbortedMidwayEvent` đẩy thông báo `NotificationType.TRIP_ABORTED_MIDWAY` (migration mở rộng `notifications_type_check`) tới **cả khách lẫn tài xế** — mô phỏng đúng cách `onGpsSignalLost`/`onSosRaised` đã làm. Đồng thời cập nhật javadoc của `driverCancelBeforePickup` (B.4) — vốn trỏ sai sang "ordinary `cancel` + support flow" cho tình huống `IN_PROGRESS` — nay trỏ đúng sang `abortInProgressTrip`. Migration: `V10__emergency_abort_reports.sql`.
3. **Hủy do bất khả kháng** (tai nạn, sự cố y tế của khách/tài xế, thiên tai) — cần phân loại lý do hủy để không tính phí sai cho bên không có lỗi.

### F. Đánh giá & khiếu nại (Rating & Complaint)
1. **Report vi phạm an toàn / thái độ** — cần phân biệt giữa đánh giá thông thường (rating) và khiếu nại nghiêm trọng (yêu cầu CSKH can thiệp), luồng xử lý khác nhau.
2. **Đánh giá thiếu khách quan vì khách đang say lúc trải nghiệm dịch vụ** — cân nhắc cho phép chỉnh sửa đánh giá trong khung giờ nhất định sau khi tỉnh táo, hoặc hiển thị cảnh báo "đánh giá được ghi nhận lúc X giờ sáng".
3. **Review bombing / đánh giá trả đũa** giữa khách và tài xế.
   > **Đã triển khai:** Một điểm thấp đơn lẻ chỉ là sự không hài lòng; một **kiểu lặp lại** điểm thấp từ cùng một người đánh giá nhắm vào cùng một người được đánh giá, qua nhiều chuyến riêng biệt (vốn đã bị chặn "một đánh giá/chuyến/người" bởi `existsByTripIdAndRaterUserId`), mới là tín hiệu đáng để CSKH xem qua — có thể là một cặp khách-tài xế thật sự không hợp nhau và cứ bị ghép lại, cũng có thể là thù oán cá nhân đội lốt phản hồi; cả hai cách đọc đều cần con người cân nhắc lịch sử/nội dung thật, không phải thuật toán tự phán. `RatingServiceImpl.flagRetaliationPatternIfJustCrossed` đếm số điểm "thấp" (`score <= LOW_SCORE_THRESHOLD = 2`, qua `RatingRepository.countByRaterUserIdAndRateeUserIdAndScoreLessThanEqual`) mà cùng một rater đã chấm cho cùng một ratee, và phát `ReviewPatternFlag` đúng **một lần** ngay khi số đếm **chạm** `RETALIATION_PATTERN_COUNT = 3` lần đầu tiên (so sánh bằng, không phải "≥") — cùng kỹ thuật "crossed-threshold" mà `TripServiceImpl.sweepStaleGpsTrips` dùng cho C.7 (bắn sự kiện đúng một lần mà không cần cờ trạng thái đã-cảnh-báo trong DB), chỉ khác là dựa trên đếm thay vì cửa sổ thời gian. `ReviewPatternFlag` là một worklist phẳng, append-only — đúng tinh thần `SosAlert`/`RouteDeviationFlag` (C.8): **không bao giờ tự động trừ điểm uy tín** của người bị gắn cờ, chỉ đưa vào hàng đợi `GET /api/ratings/review-pattern-flags` (admin-only, mirror `listComplaints`/`listSosAlerts`/`listRouteDeviationFlags`) để CSKH quyết định dựa trên bối cảnh đầy đủ. Migration: `V9__review_pattern_flags.sql`.

### G. Đồng thời & hệ thống (Concurrency, Reliability)
1. **Sự kiện `BookingConfirmedEvent` bị mất hoặc xử lý trùng** — vì hiện publish qua `ApplicationEventPublisher` trong-process (đồng bộ với transaction), cần cân nhắc: nếu Trip creation thất bại sau khi Booking đã `CONFIRMED`, có rollback được không? Về lâu dài có thể cần outbox pattern khi tách thành nhiều service.

   > **Đã triển khai (transactional event pattern — đúng cho monolith):** Trước khi có `@Transactional` ở tầng service, mỗi `repository.save(...)` có transaction riêng → `bookingRepository.save(booking)` commit trước, rồi mới gọi listener → nếu listener (`BookingEventListener.onBookingConfirmed → tripService.startTrip`) thất bại, booking đã là `CONFIRMED` nhưng không có Trip nào được tạo ra — đây chính là trạng thái không nhất quán mà G.1 cảnh báo. **Nay đã thêm `@Transactional` ở class-level cho toàn bộ service impl** (`BookingServiceImpl`, `TripServiceImpl`, `PaymentServiceImpl` và các service còn lại) — tất cả thay đổi DB trong một lời gọi service (bao gồm cả việc listener `@EventListener` được kích hoạt đồng bộ trong cùng thread) giờ tham gia vào một transaction duy nhất với propagation `REQUIRED` mặc định. Kết quả: booking confirmation + trip creation, trip completion + payment creation đều atomic — nếu bất kỳ bước nào thất bại thì toàn bộ rollback. **Notification listeners (`DomainEventListener`) được đổi sang `@TransactionalEventListener(phase = AFTER_COMMIT)`** — thông báo là side effect "bắn rồi quên", không được phép khiến transaction nghiệp vụ rollback nếu gửi notification thất bại; `AFTER_COMMIT` đảm bảo notification chỉ gửi sau khi dữ liệu nghiệp vụ đã commit an toàn. Các listener cập nhật state nghiệp vụ quan trọng (`BookingEventListener`, `TripCompletedEventListener`, `TripLifecycleEventListener`, `DriverLocationEventListener`) vẫn là plain `@EventListener` — chúng phải atomic với aggregate gốc. **Outbox table thực sự (với relay riêng đọc và republish)** chỉ cần thiết khi tách thành các microservice độc lập với DB riêng — thời điểm đó `ApplicationEventPublisher` không còn đủ (vì sự kiện không vượt process boundary); đây là điểm mở rộng tường minh, không phải nợ kỹ thuật ở MVP monolith.
2. **Tải cao giờ cao điểm** (đêm cuối tuần, lễ Tết) — lượng booking tăng đột biến trong khi nguồn tài xế hữu hạn → cần chiến lược ưu tiên (khách quen, khách đặt trước) và cơ chế hàng đợi minh bạch.
   > **Quyết định (MVP):** Không xây hàng đợi tách biệt — `DriverMatchingPort.matchDriver` (CLAUDE.md B.5) đã là điểm hợp lý duy nhất để áp chính sách ưu tiên vì nó là nơi *duy nhất* chọn ai được ghép. Khi cần ưu tiên (khách quen / đặt trước — CLAUDE.md §4.7 đã quyết "có hỗ trợ đặt lịch trước"), bổ sung một bước *re-sort* ứng viên theo hạng khách **trước** bước sắp theo `noResponseStrikes`/khoảng cách hiện có (xem B.3 ở trên — cùng một điểm mở rộng, không phải hai cơ chế song song). "Cơ chế hàng đợi minh bạch" nghĩa là: khách luôn thấy trạng thái thực `SEARCHING`/`NO_DRIVER_FOUND` (đã có) thay vì một con số vị trí xếp hàng giả — tránh hứa hẹn sai về thời gian chờ trong giờ cao điểm. Chưa triển khai chính sách xếp hạng khách quen — phụ thuộc vào dữ liệu lịch sử đặt chuyến (module `rating`/`identity`) chưa đủ trưởng thành để định nghĩa "khách quen" một cách công bằng.
3. **Đồng bộ vị trí tài xế theo thời gian thực** — `location` module (chưa triển khai) cần quyết định: polling định kỳ hay streaming (WebSocket/SSE)? độ trễ chấp nhận được là bao nhiêu giây trước khi coi vị trí "stale"?
   > **Quyết định (đã có trong skeleton, nay chính thức hóa):** **Client-driven periodic push qua REST** (`POST /api/locations/{driverId}`, `LocationController` → `LocationServiceImpl` → `DriverLocationUpdatedEvent`) — **không** WebSocket/SSE ở MVP. Lý do: tập khách hàng là ứng dụng di động chạy nền ban đêm — một kết nối streaming dài hạn tốn pin và phức tạp để duy trì qua chuyển mạng (wifi↔4G) hơn nhiều so với một POST định kỳ chịu lỗi tốt (mất một nhịp không sao, nhịp kế tiếp tự phục hồi). Tần suất khuyến nghị cho client: **10–15 giây** khi `online`, có thể rút xuống **5 giây** khi đang có `Trip` ở trạng thái khác `STARTED`/`ARRIVED_AT_PICKUP` (đang cầm lái xe khách — độ chính xác lộ trình quan trọng hơn cho C.7/C.8/D.2). Ngưỡng "stale" đã code hóa: `DriverAvailabilityPortImpl.LOCATION_FRESHNESS_WINDOW = 5 phút` cho mục đích *ghép tài xế* (CLAUDE.md B.2 — đủ rộng vì sai lệch vài phút không ảnh hưởng nhiều đến quyết định "ai gần hơn"); một ngưỡng **chặt hơn nhiều (khuyến nghị ~60 giây)** sẽ cần cho mục đích *giám sát chuyến đang chạy* (cảnh báo mất tín hiệu GPS giữa hành trình — C.7) khi tính năng đó được xây — đây là điểm còn để ngỏ, ghi chú lại để không nhầm lẫn hai ngưỡng có mục đích khác nhau.
4. **Khả năng mở rộng của `DriverAvailabilityPort.findAvailableNear`** — hiện đang load toàn bộ tài xế `online = true` rồi lọc; ở quy mô lớn cần index không gian địa lý (PostGIS / geohash) thay vì quét tuyến tính.

## 4. Quyết định nghiệp vụ (chốt cho MVP — có thể sửa lại khi có dữ liệu vận hành thực tế)

> Bảy câu hỏi từng để ngỏ ở mục này nay đã được **chốt phương án cho MVP** theo nguyên tắc:
> chọn phương án đơn giản nhất để vận hành sớm, ít rủi ro pháp lý/tài chính nhất cho công ty,
> và để lại đường mở rộng rõ ràng. Mỗi quyết định ghi kèm **lý do chọn** và **hệ quả thiết kế**
> (mã module, trạng thái, trường dữ liệu...) để code bám sát đúng hướng — đây là nguồn sự thật
> cho `identity`, `dispatch`, `pricing`, `payment` và phần lớn nhóm edge-case **C**. Khi vận hành
> thực tế cho thấy phương án cần đổi, hãy sửa quyết định ở đây trước, rồi mới sửa code.

### 4.1 Mô hình "tài xế quay về"
**Quyết định:** Mặc định **tài xế tự túc quay về** (đi xe máy gấp gọn mang theo trên xe khách,
hoặc tự đặt xe ôm/taxi). "Xe theo sau" do công ty điều phối là **gói dịch vụ cộng thêm** (add-on
trả phí, bật theo khu vực) chứ không phải hạ tầng bắt buộc từ ngày đầu.
**Lý do:** Điều phối đồng thời 2 phương tiện (xe khách + xe theo sau) là bài toán dispatch phức
tạp nhất của domain này — xây trước khi có đủ mật độ tài xế/khách để chứng minh nhu cầu là rủi ro
đầu tư sai chỗ. Mô hình tự túc đã được nhiều dịch vụ "lái xe hộ" thực tế áp dụng thành công.
**Hệ quả thiết kế:** `dispatch`/`location` **không cần** mô hình song hành 2 phương tiện ở MVP;
trường `Trip.status` không cần thêm trạng thái "đang chờ xe theo sau". Khi bật add-on: thêm entity
`FollowVehicleAssignment` độc lập, lắng nghe `BookingConfirmedEvent`/`TripCompletedEvent`, không
chạm vào state machine của `Trip`.

### 4.2 Bảo hiểm & trách nhiệm khi va chạm
**Quyết định:**
- Công ty **mua bảo hiểm trách nhiệm bên thứ ba bắt buộc theo từng chuyến** (per-trip liability
  cover), kích hoạt tự động khi `Trip` chuyển `IN_PROGRESS` — chi phí được gộp vào cơ cấu giá.
- **Thiệt hại thân vỏ xe của khách**: thuộc trách nhiệm bảo hiểm xe (của khách) trong điều kiện
  vận hành bình thường; công ty chỉ bồi thường phần vượt mức bảo hiểm xe **khi xác định được lỗi
  của tài xế** (qua quy trình điều tra CSKH + log GPS/trạng thái Trip làm bằng chứng).
- Tài xế **chịu trách nhiệm cá nhân** (có thể bị chấm dứt hợp tác + truy cứu pháp lý) nếu lỗi do
  vi phạm rõ ràng (chạy quá tốc độ, lái khi không đủ điều kiện...).
**Lý do:** Tách rõ 3 lớp trách nhiệm giúp công ty không gánh toàn bộ rủi ro tài chính của một
ngành vốn có tỷ lệ tai nạn cao hơn taxi thường (C.4), đồng thời vẫn bảo vệ khách và tài xế ở mức
tối thiểu hợp lý — điều kiện tiên quyết để xin giấy phép vận hành.
**Hệ quả thiết kế:** `identity` cần lưu trạng thái xác minh **bảo hiểm trách nhiệm còn hiệu lực**
của từng tài xế trước khi cho `online`; `payment`/`trip` cần model `IncidentReport` (liên kết
`Trip`, mô tả, ảnh/log GPS đính kèm, trạng thái điều tra) làm input cho bồi thường — đây chính là
nền cho C.4 và D.2.

### 4.3 Phương thức thanh toán & vai trò trung gian
**Quyết định:** **Tiền mặt tại chỗ là phương thức mặc định** cho MVP (khớp với enum
`PaymentMethod.CASH` đã có); thanh toán qua app (`IN_APP` — ví điện tử/thẻ qua cổng thanh toán)
là **lựa chọn bổ sung**, bật dần theo từng thị trường khi đã có đối tác cổng thanh toán. Công ty
đóng vai trò trung gian giữ tiền và đối soát (giữ số dư tạm với tài xế, thanh toán theo chu kỳ).
**Lý do:** Tiền mặt loại bỏ phụ thuộc vào cổng thanh toán/ngân hàng ngay từ ngày đầu (giảm rủi ro
go-live), và phù hợp với hành vi người dùng thực tế của nhóm khách hàng mục tiêu (di chuyển ban
đêm, thường mang tiền mặt). `IN_APP` được giữ làm lối thoát cho D.1 (khách say không đủ tiền mặt).
**Hệ quả thiết kế:** `Payment.method` giữ nguyên 2 giá trị hiện có; không cần thêm ví nội bộ ở
MVP. Khi bật `IN_APP` thật: cần thêm `PaymentGatewayPort` (cổng thanh toán bên thứ ba) và sổ đối
soát công nợ tài xế — chính là phần "đối soát" còn thiếu ở D.3/D.4.

### 4.4 Cơ cấu cước & phụ phí
**Quyết định:** Công thức cước = `cước cơ bản + (đơn giá/km × quãng đường)` **cộng thêm** các
phụ phí sau (tất cả đã có đủ dữ liệu để tính ngay — không cần tích hợp ngoài):
- **Phụ phí giờ khuya**: áp dụng cho khung giờ đêm (ví dụ 22:00–06:00) — hệ số nhân trên tổng cước.
- **Phụ phí khu vực xa**: áp dụng khi quãng đường vượt ngưỡng bán kính chuẩn — phụ phí cố định
  hoặc đơn giá/km cao hơn cho phần vượt ngưỡng.
- **Phí chờ khách**: miễn phí N phút đầu tại điểm đón (xem 4.7/C.1), tính thêm theo phút sau đó.
**Lý do:** Đây là 3 phụ phí được chính các edge-case A.4, B.5, C.1 trong tài liệu này chỉ ra là
cần thiết — không phải suy đoán thêm tính năng. Việc tính được ngay trong `pricing` (không chờ
tích hợp bản đồ thời gian thực) giúp khép kín vòng lặp ước tính → khóa giá → tính cước cuối.
**Hệ quả thiết kế:** Mở rộng `FareEstimationPort`/`FareQuote` để nhận thêm `tripStartTime` (cho
phụ phí giờ khuya) và trả về `FareBreakdown` (cước cơ bản, phụ phí khuya, phụ phí xa, tổng) thay
vì chỉ một số tiền — vừa minh bạch với khách (giảm D.2 tranh chấp cước), vừa là nơi cắm phí chờ
khi `Trip` ghi nhận thời điểm tài xế đến nơi vs. thời điểm đón thực tế.

### 4.5 Mức độ xác minh danh tính tài xế
**Quyết định:** Yêu cầu tối thiểu để được duyệt làm tài xế:
1. Lý lịch tư pháp hợp lệ (không tiền án/tiền sự liên quan đến an toàn, tài sản);
2. Bằng lái phù hợp loại xe sẽ lái + tối thiểu N năm kinh nghiệm lái xe ô tô (không chỉ xe máy);
3. Tái xác minh định kỳ (6–12 tháng) — bằng lái còn hạn, không phát sinh vi phạm nghiêm trọng.
**Lý do:** Vì tài xế lái **xe lạ** của khách (rủi ro cao hơn lái xe quen thuộc — xem mục 1), mức
xác minh phải nghiêm hơn tài xế taxi/xe ôm công nghệ thông thường; đây cũng là điều kiện ràng buộc
cho gói bảo hiểm trách nhiệm ở 4.2 (hãng bảo hiểm thường yêu cầu hồ sơ xác minh tương ứng).
**Hệ quả thiết kế:** `identity`/`driver` cần các trường trạng thái xác minh có **hạn hiệu lực**
(không chỉ boolean một lần): `backgroundCheckStatus`, `licenseExpiryDate`,
`lastReverificationAt`. `Driver` không thể chuyển `online = true` nếu bất kỳ xác minh nào đã hết
hạn — một bất biến nghiệp vụ mới cần thêm vào danh sách invariant ở mục 2.

### 4.6 SLA hỗ trợ khẩn cấp & khiếu nại
**Quyết định:** Phân tầng hỗ trợ theo mức độ khẩn cấp:
- **Khẩn cấp** (SOS, tai nạn, sự cố an toàn — nhóm C.6, C.4): **tổng đài người thật trực 24/7**,
  cam kết phản hồi trong vài phút. Đây là khoản đầu tư bắt buộc, không thể thay bằng chatbot.
- **Không khẩn cấp** (tra cứu, khiếu nại cước phí, đổi lịch...): **chatbot/FAQ tự động** trước,
  leo thang lên người thật trong giờ hành chính nếu chatbot không giải quyết được.
**Lý do:** Rủi ro của domain này (tài sản lớn + người dùng say xỉn, ban đêm) cao hơn hẳn app gọi
xe thường — phản hồi chậm với một ca SOS có thể là vấn đề an toàn tính mạng, không chỉ trải
nghiệm. Ngược lại, đầu tư người trực 24/7 cho mọi loại yêu cầu là lãng phí ở quy mô MVP.
**Hệ quả thiết kế:** `SosAlert` (đã triển khai — C.6) là điểm vào của nhánh khẩn cấp; cần thêm
trường/luồng phân loại mức độ ưu tiên khi mở rộng `rating`/`notification` cho khiếu nại thường,
để hai nhánh không dùng chung một hàng đợi xử lý.

### 4.7 Đặt lịch trước (Advance booking)
**Quyết định:** **Có hỗ trợ đặt lịch trước**, giới hạn trong cửa sổ ngắn (ví dụ tối đa 24–48 giờ)
thay vì đặt trước tự do nhiều ngày/tuần.
**Lý do:** Vì dịch vụ phục vụ chủ yếu nhu cầu phát sinh "đêm nay sau khi nhậu", nhu cầu đặt trước
thực tế thấp và xa ngày dễ phát sinh sai lệch (đổi lịch, hủy, tài xế không còn khả dụng — trôi về
lại các edge-case nhóm B/E). Giới hạn cửa sổ ngắn giữ cho thuật toán dispatch đơn giản (chỉ cần
"giữ chỗ" gần giờ khởi hành thay vì lập lịch dài hạn).
**Hệ quả thiết kế:** `Booking` cần thêm trường `scheduledAt` (nullable — null nghĩa là tức thời);
`dispatch` chạy matching ngay khi tạo booking tức thời, nhưng **trì hoãn matching** đến gần
`scheduledAt` (ví dụ trước N phút) đối với booking đặt trước — tránh khóa tài xế quá sớm và để
tận dụng dữ liệu vị trí mới nhất.

## 5. Liên hệ với skeleton hiện tại

Các quy tắc/edge case ở mục 3 ánh xạ trực tiếp vào các điểm mở rộng đã có sẵn trong skeleton:

- `BookingService.requestDriverHome` (`modules/booking`) — nơi xử lý các edge case nhóm **A** (validate, idempotency, khóa giá) và khởi tạo tìm kiếm tài xế nhóm **B**.
- `DriverAvailabilityPort` (`common`) — hợp đồng cần mở rộng để hỗ trợ lọc theo "vị trí cập nhật gần đây", loại xe tài xế có thể lái, bán kính tìm kiếm động (nhóm **B.2, B.5**).
- `Trip` / `TripStatus` (`modules/trip`) — cần bổ sung trạng thái trung gian để phản ánh đúng vòng đời thực tế ở mục 2 (vd. tách `STARTED` thành "đang đến điểm đón" và "đã gặp khách, chuẩn bị lái"), phục vụ nhóm **C**.
- `BookingConfirmedEvent` / `BookingEventListener` — điểm cần cân nhắc outbox pattern khi xử lý nhóm **G.1**.
- Các module chưa triển khai (`identity`, `dispatch`, `location`, `pricing`, `payment`, `rating`, `notification`) nên được thiết kế **bắt đầu từ danh sách câu hỏi mở ở mục 4**, vì câu trả lời cho các câu hỏi đó quyết định trực tiếp cấu trúc dữ liệu và luồng nghiệp vụ của từng module.
