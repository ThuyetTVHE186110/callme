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
- Vòng đời `Booking` phải **đóng lại cùng nhịp** với `Trip` của nó: trip `COMPLETED` → booking `COMPLETED`; trip bị hủy trực tiếp ở tầng trip → booking bị đóng với **đúng lý do đó**. Không được tồn tại booking `CONFIRMED` "mồ côi" sau khi chuyến đã kết thúc — vì quy tắc "1 booking active/khách" (A.6) sẽ khóa khách vĩnh viễn.

   > **Đã triển khai (đóng lỗ hổng "booking kẹt CONFIRMED vĩnh viễn"):** Trước đây `BookingStatus` không có trạng thái kết thúc thành công và module booking không lắng nghe `TripCompletedEvent`/`TripCancelledEvent` — khách đi xong **một** chuyến là bị A.6 chặn đặt xe mãi mãi (lối thoát duy nhất là "hủy" chuyến đã hoàn thành, vừa bị tính oan phí E.1 vừa phá hỏng dữ liệu đối soát D.2). Nay: (1) thêm `BookingStatus.COMPLETED` + `Booking.complete()` (chỉ hợp lệ từ `CONFIRMED`; `Booking.cancel` từ chối booking đã `COMPLETED` — chuyến đã diễn ra thì không thể "hủy"); (2) `TripCancelledEvent` mang theo `CancellationReason`; (3) `booking.TripEventListener` (plain `@EventListener` — phải nguyên tử với transaction gốc theo G.1) gọi `completeForTrip`/`closeAfterTripCancellation`. `closeAfterTripCancellation` **bỏ qua** ba lý do: `SYSTEM_CASCADE` (chính booking khởi xướng việc hủy — không có gì để làm thêm), `DRIVER_REQUEST`/`DRIVER_UNRESPONSIVE` (B.4 — yêu cầu của khách phải sống tiếp để tái điều phối qua `DriverCancelledBeforePickupEvent`); mọi lý do còn lại (`CUSTOMER_NO_SHOW`/`VEHICLE_BREAKDOWN`/`DRIVER_EMERGENCY_ABORT`/`FORCE_MAJEURE`/`CUSTOMER_REQUEST`) đóng booking bằng `Booking.cancel(reason, now)` — nhờ đó chính sách phí E.1 chỉ tồn tại **một nơi duy nhất** (`Booking.cancel`) nhưng áp dụng nhất quán cho cả hai cửa hủy (hủy booking trực tiếp lẫn hủy trip cascade ngược). Migration: `V14__booking_completed_status.sql` (kèm backfill cho các booking đã kẹt).

## 3. Edge case — phân theo từng giai đoạn

### A. Giai đoạn đặt chuyến (Booking creation)
1. **Không tìm thấy tài xế nào khả dụng** quanh điểm đón → Booking nên có trạng thái trung gian (vd. `SEARCHING` / `NO_DRIVER_FOUND`) thay vì rơi thẳng vào `PENDING` vô thời hạn; cần cơ chế mở rộng bán kính tìm kiếm theo thời gian hoặc thông báo khách chủ động.
2. **Khách đang say, nhập sai vị trí/điểm đến** — cần xác thực địa chỉ qua bản đồ (location module), cho phép chỉnh sửa trước khi tài xế xuất phát, và cảnh báo nếu vị trí GPS lệch quá xa địa chỉ nhập tay.
3. **Đặt trùng lặp** do mạng chập chờn (request gửi 2 lần) → cần idempotency key khi tạo Booking.
4. **Giá ước tính thay đổi** giữa lúc xem báo giá và lúc xác nhận (phụ phí giờ cao điểm / khuya) — cần "khóa giá" trong khoảng thời gian xác nhận hoặc hiển thị lại giá mới để khách đồng ý.

   > **Quyết định (đã chốt — không khóa giá):** Hệ thống cố ý **không khóa giá**. `BookingServiceImpl.requestDriverHome` báo giá tại thời điểm tạo booking (`fareEstimationPort.estimate(pickup, destination, Instant.now())`, lưu vào `Booking.estimatedFareAmount/Currency`); `TripServiceImpl.complete` **re-quote lại** tại thời điểm hoàn thành thực tế (`Instant.now()` lúc đó) để tính `finalFareAmount` — đúng tinh thần CLAUDE.md §4.4 "tính cước cuối cùng tại thời điểm hoàn thành: chuyến chạy qua nửa đêm phải chịu đúng phụ phí giờ khuya áp dụng lúc nó *thực sự* diễn ra". "Khóa giá" theo nghĩa đen sẽ mâu thuẫn trực tiếp với nguyên tắc đó — một chuyến đặt lúc 21h nhưng tài xế đến muộn, lái đến 1h sáng, khóa giá ở mức 21h sẽ vừa thiệt cho công ty (bỏ qua phụ phí khuya thực tế) vừa tạo kẽ hở để khách cố tình trì hoãn hưởng giá rẻ. Thay vào đó, hệ thống đáp ứng đúng nhu cầu cốt lõi của A.4 — "khách không bị bất ngờ về giá, và có bằng chứng khi tranh chấp" — bằng cách lưu **cả hai** mốc giá (`estimatedFareAmount` lúc đặt, `finalFareAmount` lúc hoàn thành) làm dấu vết đối soát cho D.2, đồng thời publish lại quote mới ngay khi khách đổi điểm đến giữa chừng (`changeDestination` — xem C.5) để khách luôn biết giá hiện hành trước khi nó chốt. Không cần thêm cơ chế khóa/giữ giá riêng.
5. **Khách hủy ngay sau khi đặt**, trước khi có tài xế nhận — phải là thao tác miễn phí, không tính phí hủy.
6. **Khách đặt nhiều chuyến cùng lúc** (vd. cho cả nhóm bạn nhậu) — có nên giới hạn số booking đang hoạt động/khách?

   > **Quyết định & đã triển khai (tinh chỉnh cho đặt lịch trước — §4.7):** Giới hạn gốc "1 booking active/khách" từng tính cả booking **đặt lịch trước chưa đến hạn** (`PENDING` + `scheduledAt` tương lai) — nghĩa là khách đặt lịch cho tối mai bị khóa không gọi được xe **tối nay** trong tối đa 48 giờ. "Active" đúng nghĩa A.6 là *"đang (hoặc sắp) ngồi trên một chiếc xe"* — một booking hẹn ngày mai không thỏa điều đó. Quy tắc mới trong `BookingServiceImpl.requestDriverHome`: tối đa **một chuyến tức-thời/đến-hạn** (phân loại bằng `Booking.isDueForMatching(now)`) **và** tối đa **một chuyến đặt lịch trước** đồng thời — hai loại không chặn lẫn nhau. Để bất biến "một xe một lúc" vẫn kín khi hai loại *giao nhau về thời gian* (khách vẫn đang trên chuyến tức thời lúc booking đặt lịch đến hạn ghép), `sweepScheduledBookings` **bỏ qua chu kỳ hiện tại** cho booking đặt lịch nếu khách còn một booking đến-hạn khác đang active — vòng quét kế tiếp (1 phút sau) sẽ thử lại, không bao giờ cấp hai tài xế cùng lúc cho một khách.

### B. Giai đoạn ghép tài xế (Dispatch / Matching)
1. **Race condition**: nhiều tài xế cùng "accept" một booking cùng lúc → cần cơ chế khóa (optimistic lock trên `Booking.version`, hoặc gán theo nguyên tắc "ai chạm trước thắng" ở tầng transaction) để đảm bảo chỉ một tài xế được gán.
2. **Tài xế "ảo"**: trạng thái `online = true` trong DB nhưng vị trí không cập nhật / thiết bị mất kết nối → ứng viên trả về không thực sự khả dụng. Cần kèm điều kiện "vị trí cập nhật trong N phút gần nhất".
3. **Timeout không phản hồi**: tài xế nhận yêu cầu nhưng không accept/reject trong khung thời gian quy định → tự động chuyển sang ứng viên kế tiếp, đồng thời hạ điểm ưu tiên hiển thị của tài xế đó cho các lần sau.
   > **Đã triển khai (đã điều chỉnh theo kiến trúc thực tế):** hệ thống dùng mô hình "ghép nối nguyên tử" (`DriverMatchingPort` + `DriverReservationPort` — tài xế được *reserve* ngay khi ghép, không có cửa sổ broadcast-rồi-chờ-accept/reject như mô tả gốc, vì vậy race "nhiều tài xế cùng accept" không thể xảy ra — xem B.1). Hệ quả là điểm thất bại tương đương không phải "không accept trong N giây" mà là "đã được ghép/reserve nhưng biến mất, không di chuyển đến điểm đón". `TripServiceImpl.sweepUnresponsiveDrivers` (chạy định kỳ mỗi phút, `DRIVER_RESPONSE_TIMEOUT = 15 phút`) phát hiện các `Trip` còn ở `STARTED` quá lâu, hủy qua đúng luồng tái điều phối B.4 (`DriverCancelledBeforePickupEvent` — không hủy yêu cầu của khách), gắn lý do `CancellationReason.DRIVER_UNRESPONSIVE` (không tính phí cho khách), và phát `DriverUnresponsiveEvent` để `driver` module ghi nhận `Driver.noResponseStrikes`. `DriverMatchingPortImpl` xếp hạng ứng viên theo **khoảng cách hiệu dụng** = khoảng cách thực + `noResponseStrikes × STRIKE_DISTANCE_PENALTY_KM (2 km)` — đúng tinh thần "hạ điểm ưu tiên hiển thị của tài xế đó cho các lần sau". *(Điều chỉnh từ thiết kế ban đầu "sort theo strike trước, khoảng cách sau": sort tuyệt đối nghĩa là tài xế 1 strike cách khách 200 m thua vĩnh viễn mọi tài xế 0 strike dù cách 10 km — vì strike không bao giờ giảm, "hạ ưu tiên" biến thành "cấm trọn đời" trên thực tế. Quy strike thành mét phạt giữ đúng mục đích: vài lần lỡ nhịp tốn vị thế nhưng còn đường quay lại, kẻ mất tích kinh niên chìm hẳn khỏi vùng cạnh tranh.)* Migration: `V4__driver_no_response_tracking.sql`.
4. **Tài xế hủy ngay sau khi nhận** (trước khi đến điểm đón) → Booking phải quay lại trạng thái tìm tài xế mới, **không hủy luôn** chuyến của khách; cần thông báo lại cho khách về độ trễ phát sinh.

   > **Đã triển khai (đóng lỗ hổng "ghép lại đúng tài xế vừa bỏ chuyến"):** Khi tái điều phối, tài xế vừa rút lui đã được release về pool ngay trước đó (qua `TripCancelledEvent`) và thường vẫn là ứng viên **gần nhất** — không loại trừ thì hệ thống sẽ ghép khách lại với chính người vừa bỏ rơi họ; với tài xế mất tích (B.3) điều này tạo vòng lặp vô hạn "ghép → chờ 15 phút → hủy → ghép lại". Nay `DriverCancelledBeforePickupEvent.previousDriverId` được truyền xuyên suốt `rematchAfterDriverWithdrawal → matchAndConfirm → DriverMatchingPort.matchDriver(pickup, radiusKm, excludedDriverId)`: tài xế vừa rút bị loại khỏi **mọi** vòng mở rộng bán kính của lần rematch đó (vẫn được ghép bình thường cho các yêu cầu khác). Đồng thời `sweepUnresponsiveDrivers` (B.3) phát `DriverUnresponsiveEvent` (ghi strike) **trước** `DriverCancelledBeforePickupEvent` — để các lần ghép trong cùng cửa sổ quét đã nhìn thấy strike mới khi xếp hạng ứng viên. Ngoài ra, lối hủy thông thường `TripService.cancel` do tài xế khởi xướng trước khi đến điểm đón nay được **dồn về đúng luồng B.4 này** (`driverCancelBeforePickup`) thay vì giết luôn booking — trước đây "ordinary cancel" của tài xế vi phạm thẳng yêu cầu "không hủy luôn chuyến của khách" của chính edge case này.

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

   > **Đã triển khai (đóng khoảng trống "quote mới chỉ nằm trong log"):** Lời hứa ở A.4 — "publish lại quote mới ngay khi khách đổi điểm đến để khách luôn biết giá hiện hành" — trước đây chỉ được thực hiện một nửa: `changeDestination` có re-quote nhưng kết quả chỉ đi vào `log.info` (API trả `Void`), tức là *không một con người nào* nhìn thấy giá mới. Nay `PUT /api/trips/{id}/destination` trả về `DestinationChangeResponse` (cước ước tính mới + quãng đường) cho chính khách gọi, đồng thời publish `TripDestinationChangedEvent` → `NotificationType.DESTINATION_CHANGED` tới **cả khách lẫn tài xế** (tài xế đang giữa chừng công việc trên xe của khách — họ xứng đáng cùng mức minh bạch về lộ trình và tiền như khách, mirror đúng mẫu fan-out hai bên của `onGpsSignalLost`/`onSosRaised`). Migration: `V16__destination_changed_and_verification_expired_notifications.sql`.

6. **Khách có hành vi gây rối / quấy rối tài xế** (do say xỉn) — cần nút SOS / báo khẩn trong app, ghi âm/ghi hình hành trình (nếu pháp luật cho phép), quy trình hỗ trợ khẩn 24/7.
7. **Mất tín hiệu GPS / kết nối mạng giữa hành trình** — không theo dõi được lộ trình thực tế, ảnh hưởng tính cước và an toàn; cần cơ chế đồng bộ lại khi có kết nối + cảnh báo nếu mất tín hiệu quá lâu.

   > **Đã triển khai:** `TripServiceImpl.sweepStaleGpsTrips` — job định kỳ (`@Scheduled(fixedDelayString = "PT1M")`, `GPS_SWEEP_INTERVAL`) quét mọi `Trip` đang `IN_PROGRESS` (driver đang cầm lái xe khách — giai đoạn nhạy cảm nhất), tra cứu mốc cập nhật vị trí gần nhất qua cổng `DriverLocationFreshnessPort` (impl: `DriverLocationFreshnessPortImpl`, đọc `Driver.lastLocationUpdatedAt`). Nếu mốc đó cũ hơn `GPS_LOSS_THRESHOLD = 3 phút`, hệ thống phát `GpsSignalLostEvent` đúng **một lần** nhờ kỹ thuật "crossed-threshold window" — không cần cờ trạng thái đã-cảnh-báo trong DB: chỉ bắn sự kiện khi mốc cập nhật rơi vào khoảng `(mốc staleSince của lần quét trước, staleSince hiện tại)`, một cửa sổ mà một timestamp "đứng yên" (vì driver mất tín hiệu, không còn cập nhật) chỉ đi qua đúng một lần rồi "già" ra khỏi cửa sổ ở vòng quét kế tiếp. *(Tinh chỉnh độ bền: cận dưới của cửa sổ neo vào `lastGpsSweepStaleSince` — mốc của lần quét **thực tế** trước đó — thay vì giả định nhịp danh nghĩa 1 phút; các cửa sổ liên tiếp vì thế lát kín trục thời gian không hở khe, nên một lần quét chạy trễ (scheduler dùng chung một luồng, chu kỳ chậm) tự nới rộng cửa sổ của chính nó thay vì để mốc stale "nhảy qua" cửa sổ cố định và cảnh báo an toàn câm lặng vĩnh viễn. Nếu một phần tử trong lượt quét lỗi, mốc **không** dịch — lượt sau quét lại trùm cả khoảng đó: cảnh báo trùng là chế độ hỏng được chọn có chủ đích, bỏ lỡ cảnh báo thì không.)* `DomainEventListener.onGpsSignalLost` đẩy thông báo tới **cả khách lẫn tài xế** (`NotificationType.GPS_SIGNAL_LOST`, migration `V6__gps_signal_lost_notification_type.sql` mở rộng `notifications_type_check`) — khách được biết tài xế/xe của mình có thể đang gặp sự cố, tài xế được nhắc kiểm tra kết nối để chuyến đi được ghi nhận chính xác (tránh tranh chấp cước sau này — liên hệ D.2). Lưu ý: ngưỡng `GPS_LOSS_THRESHOLD = 3 phút` ở đây là ngưỡng *giám sát an toàn giữa chuyến*, khác với `DriverAvailabilityPortImpl.LOCATION_FRESHNESS_WINDOW = 5 phút` dùng cho mục đích *ghép tài xế* (xem ghi chú G.3 bên dưới — không nhầm lẫn hai ngưỡng).
8. **Tài xế cố tình đi đường vòng để tăng cước** (gian lận) — cần so sánh lộ trình thực tế với lộ trình tối ưu được đề xuất, cảnh báo nếu lệch quá ngưỡng.

   > **Đã triển khai (điều chỉnh theo hạ tầng thực tế — không có dịch vụ chỉ đường):** hệ thống **chưa** tích hợp dịch vụ định tuyến (Google Maps/HERE/OSRM...) nên không có khái niệm "lộ trình tối ưu được đề xuất" để so khớp rẽ-từng-khúc — `pricing` chỉ tính cước trên khoảng cách đường chim bay (`GeoPoint.distanceKm`, xem `FareEstimationPort`). Thay vì xây dựng hạ tầng định tuyến mới (tốn kém, thêm phụ thuộc ngoài, không phải trọng tâm MVP), heuristic khả thi ngay với dữ liệu sẵn có: **so sánh quãng đường GPS thực tế đã đi với quãng đường đường-chim-bay đã tính cước**. Cụ thể — `TripServiceImpl.checkRouteDeviation` (gọi từ `complete()`) dùng cổng mới `DriverRouteTracePort` (impl `DriverRouteTracePortImpl` ở module `location`, cộng dồn khoảng cách giữa các điểm GPS liên tiếp trong `location_updates` — bảng vốn đã lưu lịch sử đầy đủ cho mục đích đối soát D.2) để tính tổng quãng đường tài xế đã đi trong khoảng từ lúc cầm lái (`Trip.identityVerifiedAt` — CLAUDE.md C.2) đến lúc hoàn thành. Nếu tỉ lệ `thực tế / đường chim bay` vượt `ROUTE_DEVIATION_RATIO_THRESHOLD = 1.5` (và quãng đường tính cước đủ lớn — `MIN_FLAGGABLE_DISTANCE_KM = 1 km`, để nhiễu GPS trên các chuyến ngắn không tạo cảnh báo giả), hệ thống lưu một `RouteDeviationFlag` (bảng `route_deviation_flags`, migration `V7__route_deviation_flags.sql`) vào hàng đợi CSKH (`GET /api/trips/route-deviations`, chỉ `ADMIN`) — **không** tự động trừ điểm hay từ chối thanh toán, vì đường vòng thật (kẹt xe, đường cấm, đưa khách qua nhiều điểm) tạo ra đúng tín hiệu giống gian lận; phân biệt hai trường hợp này cần con người xem xét lộ trình GPS đầy đủ. Mẫu thiết kế "bản ghi phẳng append-only + worklist admin" cố tình giống hệt `SosAlert`/`listSosAlerts` (C.6) để nhất quán cách CSKH xử lý các loại cảnh báo tự động. *(Khi sản phẩm đủ trưởng thành để tích hợp dịch vụ định tuyến, đây chính là điểm mở rộng: thay "đường chim bay" bằng "lộ trình tối ưu đề xuất" mà không đổi luồng nghiệp vụ xung quanh.)*
9. **"Xe theo sau" bị lạc / đến trễ** — nếu mô hình có người chở tài xế về, cần điều phối song song hành trình của 2 phương tiện, xử lý khi chúng tách rời nhau.

### D. Giai đoạn hoàn thành & thanh toán
1. **Khách từ chối thanh toán hoặc không đủ tiền mặt** lúc kết thúc (đặc biệt vì khách say) — cần chính sách: giữ giấy tờ tạm thời? thanh toán trễ qua app? báo cáo CSKH?
   > **Quyết định & đã triển khai (chọn nhánh "báo cáo CSKH"):** Trong 3 phương án gợi ý, "giữ giấy tờ tạm thời" mang rủi ro pháp lý/tranh chấp tài sản quá lớn cho một thao tác tự phục vụ (ai giữ? trả lại khi nào? mất thì sao?), còn "thanh toán trễ qua app" **đã có sẵn** đường thoát qua `Payment.retry`/`PaymentMethod.IN_APP` (D.3, `MAX_RETRIES = 3`). Phần còn thiếu thực sự chỉ là **"báo cáo CSKH"** — một hàng đợi để con người can thiệp khi cả thiện chí tự xử lẫn số lần thử lại đều đã cạn. `Payment.retry()` vốn đã từ chối với thông điệp trỏ tới "liên hệ tổng đài" khi `retryCount >= MAX_RETRIES` (D.3) — nhưng trước đây không có nơi nào để CSKH *tìm* đúng các khoản đó. Nay `PaymentRepository.findByStatusAndRetryCountGreaterThanEqualOrderByIdDesc(FAILED, Payment.MAX_RETRIES)` truy đúng tập "thanh toán tiền mặt bị từ chối/không đủ tiền và đã hết lượt thử lại" — chính là tập D.1 mô tả — phơi ra qua `PaymentService.listUnsettled` / `GET /api/payments/unsettled` (admin-only, mirror `listSosAlerts`/`listComplaints`/`listRouteDeviationFlags`: hàng đợi phẳng, không tự động hành động, để CSKH quyết định đối soát/thu hồi công nợ ra sao theo từng ca cụ thể). `Payment.MAX_RETRIES` được nâng lên `public` để cùng một ngưỡng "đã cạn kiên nhẫn hệ thống" được dùng nhất quán ở cả hai nơi (entity guard + repository query) — không trùng lặp magic number. **Bổ sung (đóng khoảng trống "ai báo cáo được khách không trả tiền"):** trước đây mọi thao tác trên `Payment` chỉ thuộc về *khách* — nghĩa là kịch bản chủ đạo của D.1 ("khách say từ chối/không đủ tiền mặt") yêu cầu chính người từ chối trả tiền tự bấm "tôi không trả" thì hàng đợi CSKH mới có dữ liệu — tức là không bao giờ. Nay `Payment` lưu thêm `driverId` (migration `V15__payment_driver_id.sql`, backfill từ `trips`): **tài xế** — người đứng thu tiền tại chỗ — xem được payment của chuyến mình (`getByTrip`), báo được thất bại (`markFailed` — chính là điểm vào thực tế của D.1), và là người **duy nhất** (ngoài admin) xác nhận thanh toán `CASH` — bên *nhận* tiền mới là bên có tư cách xác nhận đã nhận, khách tự khai "đã đưa tiền mặt" sẽ để mọi tranh chấp "đưa rồi/chưa đưa" không có lời chứng đối ứng (D.2). Ngược lại `IN_APP` do **khách** xác nhận (ví/thẻ của họ — và họ cũng là người báo lỗi cổng thanh toán cho D.3). `retry` vẫn của riêng khách, `refund` vẫn của riêng admin.
2. **Tranh chấp về cước phí** — khách (lúc tỉnh) cho rằng quãng đường/thời gian bị tính sai — cần lưu vết đầy đủ lộ trình GPS + log thời gian từng mốc trạng thái Trip làm bằng chứng đối soát.
3. **Thanh toán qua app thất bại** (timeout cổng thanh toán, thẻ bị từ chối) — cần cơ chế retry + đối soát công nợ, không chặn tài xế nhận chuyến mới vì lỗi thanh toán của khách trước.
4. **Yêu cầu hoàn tiền sau khi hoàn thành** — quy trình khiếu nại + hoàn tiền cần SLA rõ ràng.

### E. Hủy chuyến (Cancellation)
1. **Khách hủy sau khi tài xế đã xác nhận và đang di chuyển đến điểm đón** — nên có phí hủy để bù chi phí di chuyển của tài xế, nhưng cần ngưỡng thời gian hợp lý (hủy trong 1 phút đầu vẫn miễn phí).
   > **Đã triển khai:** `Booking` ghi nhận `confirmedAt` (đặt trong `confirmWithDriver` — đúng thời điểm tài xế được ghép và bắt đầu "tốn chi phí" thay vì từ lúc tạo booking, vì hủy trước khi có tài xế không tốn gì cả — A.5 vẫn miễn phí vô điều kiện). `Booking.cancel` áp phí hủy **cố định** `CANCELLATION_FEE_AMOUNT = 20.000 VND` (không tỷ lệ theo cước — cái cần bù là chi phí tài xế chạy không tới điểm đón, một chi phí gần như không đổi bất kể cuốc xe lớn hay nhỏ) khi và chỉ khi **cả ba** điều kiện đúng: lý do là `CUSTOMER_REQUEST` (mọi lý do khác — `DRIVER_REQUEST`/`FORCE_MAJEURE`/`SYSTEM_CASCADE`/`CUSTOMER_NO_SHOW`/`DRIVER_UNRESPONSIVE`/`VEHICLE_BREAKDOWN` — đã miễn trừ trách nhiệm cho khách theo định nghĩa của chính chúng), booking đang `CONFIRMED` (chưa có tài xế thì chưa tốn gì), và đã vượt `CANCELLATION_GRACE_PERIOD = 1 phút` kể từ `confirmedAt` — đúng ví dụ minh họa "1 phút đầu miễn phí" trong edge case này. Phí được lưu trực tiếp trên `Booking` (`cancellationFeeAmount`/`cancellationFeeCurrency`, nullable) thay vì đi qua `Payment` — vốn được nối cứng 1:1 với `tripId` của một chuyến đã hoàn thành (`PaymentRepository.findByTripId` là unique index, xem D.1) — nên việc thu hộ/đối soát khoản phí này (CSKH thu sau, trừ vào số dư tài xế...) vẫn là việc còn lại của `payment` khi engine thu phí từng phần được thiết kế; ở đây hệ thống chỉ đảm bảo **chính sách được áp đúng và để lại dấu vết minh bạch** (giảm tranh chấp D.2). `confirmWithDriver`/`cancel` nhận `Instant now` tường minh (giống `Rating#edit`) để chính sách dựa-trên-thời-gian này kiểm thử được tất định. Migration: `V8__booking_cancellation_fee.sql`. **Bổ sung (cùng đợt với bất biến "đóng vòng đời booking" ở §2):** trước đây khách có thể né phí này bằng cách hủy ở tầng **trip** (`DELETE /api/trips/{id}` — không có logic phí) thay vì tầng booking; nay hủy trip trước khi đón (lý do `CUSTOMER_REQUEST`) cascade ngược về `Booking.cancel(CUSTOMER_REQUEST, now)` qua `closeAfterTripCancellation`, nên phí E.1 áp dụng **y hệt nhau ở cả hai cửa** — một chính sách, một chỗ code, hai đường vào.
2. **Tài xế hủy giữa chừng khi đã ở trạng thái `IN_PROGRESS`** (đang cầm lái xe khách!) — đây là tình huống **nghiêm trọng nhất** trong toàn bộ domain: tài xế không thể đơn giản "bỏ chuyến" giữa đường vì đang điều khiển tài sản của khách. Cần quy trình đặc biệt: tài xế phải đưa xe + khách đến nơi an toàn trước khi được phép kết thúc bất thường, có thể cần điều phối tài xế thay thế đến tiếp ứng tại chỗ.

   > **Đã triển khai (đóng kẽ hở — trước đây `cancel` cho phép tài xế "bỏ chuyến" tùy tiện ngay cả khi `IN_PROGRESS`):** Rà soát lại nghiệp vụ phát hiện `TripService.cancel` (lối hủy thông thường) trước đây **không** chặn tài xế tự hủy khi đang `IN_PROGRESS` — đúng cái "lối thoát tùy tiện" mà chính đoạn edge-case này cảnh báo không được phép tồn tại (chỉ được tài liệu hóa bằng comment, chưa được chặn bằng code). Nay `TripServiceImpl.cancel` **từ chối thẳng** mọi yêu cầu hủy do chính tài xế được phân công khởi xướng khi `status = IN_PROGRESS` (`ForbiddenException`, hướng họ sang quy trình chuyên biệt bên dưới); CSKH/admin (`FORCE_MAJEURE`) vẫn hủy được từ mọi trạng thái chưa kết thúc. **Cập nhật (đóng lỗ hổng trốn cước):** khách hàng nay **cũng bị chặn** hủy khi `IN_PROGRESS` — ở cả hai cửa: `TripService.cancel` (`ForbiddenException`) lẫn hủy `Booking` cascade xuống (`TripServiceImpl.cancelForBooking` ném `ConflictException` khi `BookingCancelledEvent.reason = CUSTOMER_REQUEST` gặp trip `IN_PROGRESS` — listener đồng bộ trong cùng transaction nên booking cancellation tự rollback nguyên tử, đúng pattern G.1). Lý do: hủy giữa chuyến xóa sạch cước (`TripCompletedEvent` không phát → không có `Payment`) — khách được chở 19/20 km rồi "hủy" sẽ đi gần hết chặng miễn phí; trong khi nhu cầu chính đáng "muốn dừng sớm" đã có đường đi đúng là `changeDestination` về vị trí hiện tại (C.5, re-quote ngay) + tài xế `complete` tại đó — cước phần đã đi được tính đủ. Đây không phải tình huống của riêng tài xế nữa: một khi xe đã lăn bánh, **không bên nào** có lối "cancel" tùy tiện; các lối ra hợp lệ là complete (có cước), `abortInProgressTrip` (tài xế, sự cố an toàn), `reportVehicleBreakdown` (C.3) và FORCE_MAJEURE (CSKH). Lối ra hợp pháp duy nhất cho tài xế là `TripService.abortInProgressTrip` (`PUT /api/trips/{id}/abort-in-progress`, chỉ tài xế được phân công, chỉ hợp lệ từ `IN_PROGRESS`): toạ độ `safeLatitude`/`safeLongitude` trong request **chính là lời xác nhận** "tôi đã đưa xe và khách đến nơi an toàn *trước khi* kết thúc chuyến" — không phải một yêu cầu xin phép, mô phỏng đúng cách `PickUpCustomerRequest.identityVerified` (C.2) buộc xác nhận tại biên API trước khi chạm vào guard nghiệp vụ. Hủy với lý do riêng `CancellationReason.DRIVER_EMERGENCY_ABORT` — tách biệt khỏi `DRIVER_REQUEST` (không bị tính là "bỏ chuyến tùy tiện", không áp phí hủy nào) — và ghi một `EmergencyAbortReport` (bảng `emergency_abort_reports`, mô phỏng đúng mẫu `SosAlert`/`RouteDeviationFlag`/`ReviewPatternFlag`: bản ghi phẳng append-only) vào hàng đợi CSKH (`GET /api/trips/emergency-aborts`, chỉ `ADMIN`). Cố tình **không** tự động điều phối tài xế thay thế đến vị trí an toàn — đúng tinh thần "có thể cần" trong mô tả gốc (không phải "luôn luôn cần"): mỗi tình huống khẩn cấp giữa đường là khác nhau (xe cháy, tai nạn, khách lên cơn bệnh...), chỉ con người mới đủ ngữ cảnh để quyết định có cần điều xe tiếp ứng hay không, giống hệt cách C.3 để CSKH quyết định "gọi cứu hộ hay đổi taxi thường". `TripAbortedMidwayEvent` đẩy thông báo `NotificationType.TRIP_ABORTED_MIDWAY` (migration mở rộng `notifications_type_check`) tới **cả khách lẫn tài xế** — mô phỏng đúng cách `onGpsSignalLost`/`onSosRaised` đã làm. Đồng thời cập nhật javadoc của `driverCancelBeforePickup` (B.4) — vốn trỏ sai sang "ordinary `cancel` + support flow" cho tình huống `IN_PROGRESS` — nay trỏ đúng sang `abortInProgressTrip`. Migration: `V10__emergency_abort_reports.sql`.
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
5. **Thu hồi phiên đăng nhập (token revocation)** — JWT tự chứa (không tra DB khi parse) nên không thể tự thu hồi; với TTL mặc định 24h, một tài xế bị đình chỉ giữa ca (sau sự cố an toàn C.4/C.6) lẽ ra vẫn thao tác được tới khi token hết hạn.
6. **Độ bền của các job định kỳ (sweeps)** — bốn sweep (`sweepUnresponsiveDrivers`, `sweepStaleGpsTrips`, `sweepScheduledBookings`, `sweepExpiredVerifications`) chia sẻ scheduler một luồng mặc định của Spring; một phần tử lỗi giữa lượt quét từng làm rollback cả lượt, và chạy nhiều instance sẽ double-fire mọi sweep.
7. **Triển khai production** — hệ thống cần chạy thật ở đâu, deploy thế nào, secrets nằm đâu, ai gác cổng TLS/firewall.

   > **Quyết định & đã triển khai (single droplet DigitalOcean — runbook đầy đủ ở `deploy/README.md`):**
   > Docker Compose trên **một droplet Ubuntu 24.04 (≥2GB)**: `caddy` (TLS tự động Let's
   > Encrypt + HSTS, **chặn `/actuator/**` từ public** — thực thi đúng giả định mà
   > `SecurityConfig` đã ghi khi permitAll actuator) → `app` (profile prod, không publish
   > port) → `db` (postgres:16, volume, không publish port). Caddy set `X-Forwarded-For`
   > và là proxy nội bộ nên khớp `server.tomcat.remoteip` (A07) — rate-limit theo IP đúng
   > từng client. CI/CD bằng GitHub Actions: `ci.yml` (PR-only) gác merge; `cd.yml` trên
   > push master chạy lại full test → build image layered (Dockerfile, non-root, healthcheck)
   > → push **GHCR tag theo git SHA** (chính là cần gạt rollback: sửa `APP_TAG` trong
   > `/opt/callme/.env` rồi `compose up -d`) → scp compose/Caddyfile (repo là nguồn sự thật,
   > droplet không drift) → SSH deploy và **fail pipeline nếu container không healthy**.
   > Secrets runtime (JWT_SECRET, DB_PASSWORD, SPEEDSMS_ACCESS_TOKEN, CORS) sống trong
   > `/opt/callme/.env` chmod 600 — không bao giờ trong repo; secrets CI (host/user/SSH key)
   > trong GitHub Actions secrets. Droplet hardening một lần qua `deploy/setup-droplet.sh`
   > (user `deploy`, tắt root+password SSH, UFW 22-limit/80/443 + DO cloud firewall hai lớp,
   > fail2ban, unattended-upgrades, swap 2G, log rotation). `server.shutdown: graceful`
   > (20s) để deploy giữa giờ cao điểm không chém ngang request complete-trip/thanh toán.
   > Backup: `pg_dump` hằng đêm giữ 14 ngày (`deploy/backup-db.sh` + cron) — **chưa** phải
   > PITR; đường nâng cấp đã ghi trong runbook: DO Managed Postgres khi DB thành tài sản
   > sống còn, droplet thứ hai + LB (kèm chuyển rate-limit sang Redis — sweep thì đã có
   > advisory lock sẵn từ G.6) khi cần scale ngang. Migration giữ nguyên tắc
   > backward-compatible (thêm, không xóa) để rollback code không bao giờ cần rollback DB.

   > **Đã triển khai:** (1) **Cô lập lỗi từng phần tử** — thân vòng lặp của cả bốn sweep bọc `try/catch RuntimeException` + log: một trip/booking/driver "chua" (thường do race với thao tác đồng thời — guard state machine từ chối, optimistic lock fail) chỉ bị bỏ qua trong chu kỳ đó, các phần tử còn lại vẫn được xử lý, chu kỳ kế tiếp tự thử lại (mọi sweep đều idempotent-theo-chu-kỳ). Riêng `sweepStaleGpsTrips` giữ mốc cửa sổ đứng yên khi có lỗi để không bỏ lỡ cảnh báo (xem C.7). (2) **Khóa chống double-fire đa instance** — mỗi sweep mở đầu bằng `pg_try_advisory_xact_lock(key)` (native query trên repository của chính module, key hằng số duy nhất 1001–1004): instance không lấy được khóa thì bỏ qua chu kỳ — không cần dependency mới (ShedLock...) hay bảng khóa riêng, khóa gắn vào transaction của lượt quét nên **không thể quên nhả**. Đây là khóa *per-cycle* (sweep ngắn hơn nhiều so với nhịp 1 phút) — đủ cho mục tiêu "không double-fire", không phải leader election tổng quát; nếu sau này cần điều phối phức tạp hơn giữa các instance, đó là lúc cân nhắc ShedLock/K8s CronJob thay thế.

   > **Quyết định & đã triển khai:** Không xây refresh-token/blacklist ở MVP (thêm state + endpoint + client flow chỉ để giải quyết một vấn đề có lời giải rẻ hơn). Thay vào đó `JwtAuthenticationFilter` kiểm tra **`Account.active` trên từng request** qua cổng mới `AccountStatusPort` (`common.port`, impl `AccountStatusPortImpl` ở module `identity` — `infrastructure` chỉ phụ thuộc `common`, chiều phụ thuộc không đổi): token hợp lệ nhưng tài khoản đã bị vô hiệu hóa (hoặc đã xóa) → context bị xóa → 401 ngay request kế tiếp, tức **vô hiệu hóa tài khoản có hiệu lực tức thì** bất kể TTL của token. Chi phí là một lookup theo primary key mỗi request có xác thực — chấp nhận có chủ đích ở quy mô MVP; nếu profiling sau này chỉ ra điểm nóng, thêm cache TTL ngắn *bên trong adapter* mà không đụng vào filter. TTL 24h giữ nguyên — sau khi có kiểm tra per-request, TTL dài không còn là cửa sổ rủi ro đình chỉ nữa, và buộc người dùng đêm khuya đăng nhập lại mỗi giờ là trải nghiệm tồi vô cớ. **Bổ sung (từ đợt rà soát OWASP — đòn bẩy vận hành cho cơ chế này):** `Account.deactivate()` trước đây **không có bất kỳ caller nào** — toàn bộ chuỗi "đình chỉ có hiệu lực tức thì" chỉ kích hoạt được bằng UPDATE tay vào DB. Nay có `PUT /api/accounts/{id}/deactivate` / `/reactivate` (admin-only, `AccountController`/`AccountService`): admin không thể tự đình chỉ chính mình (deployment một-admin không được phép tự khoá đòn bẩy duy nhất), mọi lần đình chỉ/khôi phục đều ghi log `warn` kèm ai-làm-gì-với-ai (OWASP A09), và `JwtAuthenticationFilter` cũng log mỗi lần chặn token còn hạn của tài khoản đã bị vô hiệu — tín hiệu đúng nghĩa "kẻ bị đình chỉ đang cố quay lại".

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

> **Đã triển khai (phần "bảo hiểm còn hiệu lực trước khi online"):** `Driver.insuranceValidUntil`
> (`LocalDate`) là một trong bốn điều kiện của `isEligibleToGoOnline`/`goOnline` — xem blockquote
> "Đã triển khai" ở §4.5 ngay trên, nơi cả ba điều kiện xác minh (lý lịch, bằng lái, bảo hiểm)
> được ghi nhận cùng lúc qua `recordVerification` vì một đợt tái xác minh thực tế kiểm tra cả ba.
> Phần `IncidentReport` (mô hình hoá tai nạn/va chạm cho quy trình điều tra C.4/D.2) — xem
> blockquote riêng ngay bên dưới phần "Hệ quả thiết kế" ở cuối mục 4.2 này.

> **Đã triển khai (`IncidentReport`):** `modules/trip` có entity `IncidentReport` (bảng
> `incident_reports`, append-only, mirror `EmergencyAbortReport`/`SosAlert`): `tripId`,
> `reportedByProfileId`, `customerId`, `driverId`, `description`, `investigationStatus` (enum
> `IncidentInvestigationStatus`: `REPORTED` mặc định, `UNDER_INVESTIGATION`,
> `RESOLVED_NO_FAULT`/`RESOLVED_DRIVER_FAULT`/`RESOLVED_COMPANY_LIABLE` — phản ánh đúng 3 lớp
> trách nhiệm "không lỗi ai / lỗi tài xế / công ty bồi thường" nêu ở "Quyết định" phía trên),
> `reportedAt`, `resolutionNote`/`resolvedAt` (nullable). `TripService.reportIncident(tripId,
> requester, description)` — chỉ 2 bên tham gia chuyến (`requireParticipant`) được báo cáo, và
> chỉ khi `trip.arrivedAtPickupAt != null` — tức tài xế **đã thực sự tiếp cận xe của khách** ở
> bất kỳ thời điểm nào trong vòng đời chuyến. *(Điều chỉnh từ thiết kế ban đầu dùng danh sách
> status `{ARRIVED_AT_PICKUP, IN_PROGRESS, COMPLETED, CANCELLED}` loại trừ `STARTED`: danh sách
> đó lách được — trip bị hủy ngay từ `STARTED` mang status `CANCELLED` nên vẫn báo cáo được dù
> tài xế chưa từng thấy chiếc xe; mốc thời gian đến điểm đón mới phát biểu đúng điều kiện
> nghiệp vụ "đã từng ở cạnh xe" trên mọi nhánh vòng đời.)* Lưu `IncidentReport` rồi publish `IncidentReportedEvent` → thông báo
> `NotificationType.INCIDENT_REPORTED` tới **cả khách lẫn tài xế** (mirror `onSosRaised`/
> `onTripAbortedMidway`, `@TransactionalEventListener(AFTER_COMMIT)`). `listIncidentReports`
> (`GET /api/trips/incidents`, admin-only, mirror `listSosAlerts`) là hàng đợi điều tra cho CSKH;
> `resolveIncident` (`PUT /api/trips/incidents/{id}/resolve`, admin-only) ghi `investigationStatus`
> cuối cùng + `resolutionNote` — chính là "input cho bồi thường" mà "Hệ quả thiết kế" yêu cầu, để
> `payment` đọc khi engine bồi thường từng phần được thiết kế (chưa tự động trừ/hoàn tiền ở đây,
> đúng nguyên tắc "không bao giờ tự động" đã áp dụng cho `RouteDeviationFlag`/`ReviewPatternFlag`).
> Migration: `V13__incident_reports.sql`.

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

> **Đã triển khai (tinh chỉnh "phụ phí giờ khuya" thành cước cơ bản theo khung giờ — tham
> khảo mô hình 代驾 Trung Quốc):** Khảo sát cách tính cước của 滴滴代驾/e代驾 cho thấy mô hình
> phổ biến **không** dùng "cước cơ bản cố định + hệ số nhân % giờ khuya" mà dùng **giá khởi
> điểm theo khung giờ, mỗi khung đã bao gồm sẵn N km đầu** (滴滴: 30/40/55/70 NDT cho 8km tùy
> 4 khung giờ trong ngày; e代驾: khung 00:00–06:59 còn giảm km bao gồm xuống 6) — giờ càng
> khuya, giá khởi điểm càng cao **và** số km miễn phí càng ít, phản ánh đúng chi phí cơ hội
> thực tế của tài xế (ít cuốc hơn, khó tìm phương tiện tự túc về hơn — liên hệ §4.1). Đây là
> cùng bản chất công thức `cước cơ bản + đơn giá/km × quãng đường` mà §4.4 đã chốt, chỉ khác
> ở chỗ "cước cơ bản" và "quãng đường được tính phí" giờ phụ thuộc khung giờ — nên áp dụng
> ngay mà không phá vỡ hợp đồng `FareEstimationPort.estimate(pickup, destination, atTime)`.
> `FareEstimationPortImpl` định nghĩa enum `FareTimeBand` (`common.port.dto`) với 4 khung liền
> kề phủ kín 24h (giờ Asia/Ho_Chi_Minh): `DAY` 06:00–18:59 (80.000đ/8km), `EVENING` 19:00–22:59
> (100.000đ/8km), `LATE_NIGHT` 23:00–23:59 (130.000đ/8km), `OVERNIGHT` 00:00–05:59
> (160.000đ/6km); đơn giá phần vượt giữ nguyên `12.000đ/km`. `FareBreakdown` bỏ trường
> `nightSurcharge` (không còn là một dòng phụ phí riêng — "phụ phí khuya" giờ *là* giá khởi
> điểm cao hơn của khung muộn hơn) và thêm `timeBand`/`includedKm` để D.2 tra cứu được "tại
> sao giá khởi điểm là X" trực tiếp thay vì suy ngược từ một hệ số nhân ẩn. `remoteAreaSurcharge`
> (phụ phí khu vực xa, ngưỡng 20km) không đổi — vẫn cộng thêm độc lập trên phần vượt ngưỡng,
> bất kể khung giờ. Nhân tiện thay luôn `haversineKm` riêng của `pricing` bằng
> `GeoPoint.distanceKm` đã có sẵn ở `common` (đúng mục đích "mọi module cần khoảng cách đều
> dùng chung một công thức" mà javadoc của `GeoPoint` đã ghi). Test: `FareEstimationPortImplTest`
> (mới, module `pricing` trước đây chưa có test nào).

> **Đã triển khai (phí chờ khách — mục cuối của §4.4, tham khảo mô hình 代驾 Trung Quốc):**
> Khảo sát các nền tảng 滴滴代驾/e代驾/洪司傅代驾 cho thấy chính sách thống nhất: **miễn phí 10
> phút chờ đầu tiên** tại điểm đón, sau đó **1 NDT/phút**, với 滴滴代驾 áp **trần 180 phút tính
> phí** (tương đương trần phí chờ ~180 NDT). Quy đổi sang VND theo đúng tỉ lệ bậc giá hiện có
> (đơn giá/km = 12.000đ): `FareEstimationPortImpl` thêm `FREE_WAITING_MINUTES = 10`,
> `WAITING_FEE_PER_MINUTE_VND = 3.000`, `MAX_CHARGEABLE_WAITING_MINUTES = 180` (trần phí chờ
> = 540.000đ). `FREE_WAITING_MINUTES` cố ý bằng đúng `TripServiceImpl.NO_SHOW_GRACE_PERIOD`
> (10 phút, CLAUDE.md C.1) dù định nghĩa ở module khác — cả hai cùng là "khoảng thời gian khách
> được nợ tài xế trước khi phát sinh hệ quả" (ở đây là phí, ở C.1 là quyền huỷ no-show), nên
> giữ chúng bằng nhau là đúng tinh thần dù không chia sẻ hằng số (tránh phụ thuộc chéo
> `pricing` ↔ `trip`). Vượt `MAX_CHARGEABLE_WAITING_MINUTES` không còn là "chờ" thông thường
> nữa mà là sự cố vận hành — C.1 (`cancelNoShow`) đã xử lý nhánh đó riêng, nên phí chờ chỉ cần
> chặn trần để tránh số tiền phi thực tế trên một chuyến bị kẹt bất thường.
>
> **Hệ quả hợp đồng:** `FareEstimationPort.estimate(pickup, destination, atTime, waitingTime)`
> nhận thêm `Duration waitingTime`; `FareBreakdown` thêm trường `waitingFee` (cộng vào `total`).
> `BookingServiceImpl.requestDriverHome` truyền `Duration.ZERO` (chưa có tài xế nào đến nơi ở
> thời điểm đặt chuyến). `TripServiceImpl` thêm helper `waitingTime(trip)` =
> `Duration.between(arrivedAtPickupAt, identityVerifiedAt)` (hoặc `ZERO` nếu một trong hai mốc
> còn null — chuyến đi tắt lifecycle bình thường, vd. bị huỷ trước khi tài xế cầm lái), dùng cho
> cả `complete()` (tính cước cuối) lẫn `changeDestination()` (re-quote khi đổi điểm đến giữa
> chừng — tại thời điểm đó cả hai mốc đã có sẵn vì chuyến đang `IN_PROGRESS`). Test: mở rộng
> `FareEstimationPortImplTest` với 3 case — miễn phí trong 10 phút, tính phí phần vượt, và chặn
> trần 180 phút tính phí.

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

> **Đã triển khai (gộp chung với §4.2 — một đợt tái xác minh kiểm tra cả ba thứ cùng lúc):**
> `Driver` có 4 trường mới: `backgroundCheckStatus` (enum `PENDING`/`APPROVED`/`REJECTED`, mới
> đăng ký mặc định `PENDING`), `licenseExpiryDate`, `insuranceValidUntil` (cả hai `LocalDate`,
> null cho tới lần xác minh đầu) và `lastReverificationAt` (`Instant`, null cho tới lần xác minh
> đầu). Hằng số `Driver.REVERIFICATION_INTERVAL = 180 ngày` — chọn cận dưới của khoảng "6-12
> tháng" nêu trên, đúng tinh thần "domain rủi ro cao nên ưu tiên kiểm tra thường xuyên hơn".
> `Driver.isEligibleToGoOnline(now)` đúng khi và chỉ khi **cả bốn** điều kiện đạt: lý lịch
> `APPROVED`, `licenseExpiryDate`/`insuranceValidUntil` đều chưa qua "hôm nay" (theo UTC), và
> `lastReverificationAt` còn trong vòng `REVERIFICATION_INTERVAL`. `goOnline(Instant now)` (đổi
> chữ ký từ không tham số) ném `IllegalStateException` (→ 409, đã có handler chung) liệt kê đúng
> những điều kiện chưa đạt nếu gọi khi không đủ điều kiện — **một tài xế mới đăng ký không thể
> online cho tới khi admin xác minh lần đầu**, đúng bất biến mới ở mục 2. `goOffline()` không đổi
> — luôn được phép, không cần điều kiện gì. `Driver.recordVerification(backgroundCheckStatus,
> licenseExpiryDate, insuranceValidUntil, now)` ghi cả 3 kết quả + đặt lại `lastReverificationAt =
> now`, expose qua `PUT /api/drivers/{driverId}/verification` (`@PreAuthorize("hasRole('ADMIN')")`,
> body `VerifyDriverRequest` — `@Future` trên hai ngày hết hạn). `DriverResponse` thêm trường dẫn
> xuất `eligibleToGoOnline` (tính trực tiếp từ `isEligibleToGoOnline(Instant.now())`, không lưu
> riêng — tránh dữ liệu suy ra bị lệch theo thời gian) để client hiển thị trạng thái mà không cần
> tự tính lại logic. Migration: `V12__driver_verification.sql`.
>
> **Bổ sung (bất biến phải đúng liên tục, không chỉ tại thời điểm bật online):** điều kiện
> §4.5 trước đây chỉ được kiểm tra trong `goOnline` — tài xế đang online mà bằng lái/bảo
> hiểm hết hạn (hoặc quá hạn tái xác minh) giữa ca vẫn nằm trong pool ghép chuyến vô hạn.
> Nay `DriverServiceImpl.sweepExpiredVerifications` (`@Scheduled` mỗi **1 giờ** — đủ dày vì
> các mốc hết hạn có độ phân giải theo *ngày*, không cần nhịp 1 phút như các sweep an toàn
> của `trip`) quét tài xế `online = true`, ai không còn đủ điều kiện (`ineligibilityReasons`
> — nay public để dùng chung đúng danh sách lý do mà `goOnline` sẽ từ chối) thì `goOffline()`
> + phát `DriverForcedOfflineEvent` → thông báo `NotificationType.VERIFICATION_EXPIRED` cho
> tài xế **kèm lý do cụ thể** (mất nguồn thu nhập mà không một lời giải thích là trải nghiệm
> thù địch — và tài xế cần biết phải gia hạn cái gì). Force-offline chỉ chặn ghép chuyến
> **mới**: tài xế đang giữa chuyến (`onTrip`) vẫn lái đến nơi an toàn — giật chuyến đang chạy
> sẽ tạo ra đúng tình huống khẩn cấp giữa đường mà E.2 tồn tại để ngăn chặn; họ chỉ không thể
> online lại cho tới khi tái xác minh (guard `goOnline` sẵn có). Migration:
> `V16__destination_changed_and_verification_expired_notifications.sql` (chung với C.5 —
> cùng một lần mở rộng `notifications_type_check`).

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

> **Đã triển khai:** `Booking.scheduledAt` (nullable `Instant`) — null giữ nguyên hành vi
> tức thời hiện có (match ngay trong `requestDriverHome`). Hai hằng số mới trên `Booking`:
> `MAX_ADVANCE_BOOKING_WINDOW = 48 giờ` (constructor ném `IllegalArgumentException` → 400 nếu
> `scheduledAt` không ở tương lai hoặc vượt quá cửa sổ này — đúng "giới hạn cửa sổ ngắn") và
> `SCHEDULED_MATCH_LEAD_TIME = 15 phút`. `Booking.isDueForMatching(now)` trả về `true` cho mọi
> booking tức thời, và cho booking đặt trước khi đã vào trong cửa sổ 15 phút trước giờ hẹn (hoặc
> đã quá giờ). `BookingServiceImpl.requestDriverHome`: nếu `!isDueForMatching(now)`, booking được
> lưu ở `PENDING` với `scheduledAt` đã set, **không** gọi `driverMatchingPort.matchDriver` —
> đúng tinh thần "tránh khóa tài xế quá sớm". Một job định kỳ mới
> `sweepScheduledBookings` (`@Scheduled(fixedDelayString = "PT1M")`, cùng nhịp với
> `sweepUnresponsiveDrivers`/`sweepStaleGpsTrips` ở `trip` module) truy vấn
> `findByStatusAndScheduledAtIsNotNullAndScheduledAtLessThanEqual(PENDING, now + LEAD_TIME)` và
> chạy matching cho từng booking đến hạn — "để tận dụng dữ liệu vị trí mới nhất". Logic
> match → confirm/markNoDriverFound → lưu → publish `BookingConfirmedEvent` → release tài xế nếu
> lỗi được gom vào một helper riêng `matchAndConfirm`, dùng chung giữa `requestDriverHome` (khi
> `isDueForMatching`), `rematchAfterDriverWithdrawal` (B.4) và `sweepScheduledBookings` — ba nơi
> duy nhất cần "ghép tài xế cho một booking đã/đang tồn tại". Migration: `V11__booking_scheduled_at.sql`.
>
> **Bổ sung (báo giá theo khung giờ của chuyến, không phải lúc bấm đặt):** với booking đặt
> lịch trước, `requestDriverHome` nay quote tại **`scheduledAt`** thay vì `Instant.now()` —
> đặt lúc 14h cho chuyến 23h mà báo giá khung `DAY` (80k/8km) trong khi cước cuối chốt theo
> khung `LATE_NIGHT` (130k/8km) là chính xác kiểu "bất ngờ về giá" mà A.4 cấm, lặp lại có hệ
> thống cho *mọi* booking đặt trước ban đêm. Booking tức thời không đổi (`scheduledAt = null`
> → quote tại `now`). Nguyên tắc A.4 "không khóa giá — cước cuối tính tại thời điểm hoàn
> thành thực tế" giữ nguyên; thay đổi này chỉ làm cho con số *ước tính* trung thực với thời
> điểm chuyến đi sẽ thật sự diễn ra. Quy tắc "1 booking active" tương tác với đặt lịch trước
> — xem blockquote mới ở A.6 (một tức thời + một đặt trước được phép song song).

### 4.8 Xác minh số điện thoại & vòng đời thông tin xác thực (đã chốt & triển khai — SpeedSMS trước, ZNS để sau)
**Bối cảnh (từ đợt rà soát OWASP A04/A07):** SĐT hiện là định danh đăng nhập *và* kênh liên
lạc khẩn cấp ban đêm, nhưng đăng ký **không xác minh** SĐT — ai cũng đăng ký được bằng số của
người khác (mạo danh, chiếm trước số kết hợp với thông điệp "đã được đăng ký"). Đồng thời
`Account.changePassword` chưa có endpoint nào gọi — không có luồng đổi/quên mật khẩu, và nếu
xây mà thiếu thu hồi token thì đổi mật khẩu không vô hiệu được phiên đã lộ.

**Đề xuất (chốt phương án trước khi code — cần chọn nhà cung cấp SMS trước):**
1. **OTP kích hoạt tài khoản khi đăng ký:** `Account` thêm trạng thái `phoneVerifiedAt`
   (null = chưa xác minh — không đăng nhập được, mirror cách `Driver` mới đăng ký không thể
   online cho tới khi admin xác minh §4.5). OTP 6 số, TTL 5 phút, tối đa 5 lần thử/OTP và
   3 OTP/giờ/SĐT (chống SMS-pumping — chi phí SMS là tiền thật). Gửi qua cổng mới
   `SmsOtpPort` (`common.port`) để impl (Twilio/eSMS/SpeedSMS...) là chi tiết hạ tầng thay
   được; dev/test dùng impl giả ghi log. Đăng ký hiện tại trả token ngay — sẽ đổi thành
   "tạo tài khoản pending → verify OTP → cấp token", một breaking change cho client nên làm
   trước khi có người dùng thật.
2. **Quên/đổi mật khẩu qua OTP cùng hạ tầng:** quên mật khẩu = OTP về chính SĐT đã xác minh
   (không qua email — email là trường optional, không xác minh); đổi mật khẩu khi đang đăng
   nhập yêu cầu mật khẩu cũ. Cả hai đường đều phải **thu hồi mọi token đang sống**.
3. **Thu hồi token bằng `tokenVersion`:** `Account.tokenVersion` (int, default 0) nhúng vào
   claim JWT lúc phát hành; đổi mật khẩu / đình chỉ / "đăng xuất mọi thiết bị" chỉ việc
   `tokenVersion++`. Mở rộng `AccountStatusPort.isActive(accountId)` thành
   `isTokenValid(accountId, tokenVersion)` — **tái dùng nguyên** lookup per-request đã có ở
   G.5, không thêm bảng blacklist, không thêm round-trip mới (cùng một truy vấn PK đọc thêm
   một cột). Đây là lý do chọn token-version thay vì blacklist: blacklist cần dọn rác + bảng
   mới, token-version là một cột và một phép so sánh.
4. **Chưa làm ở MVP (ghi rõ để khỏi tranh luận lại):** MFA/2FA đầy đủ, kiểm tra mật khẩu
   rò rỉ (HIBP), device binding — chỉ cân nhắc khi có dữ liệu lạm dụng thực tế; OTP từng
   lần đăng nhập (passwordless) là hướng phổ biến ở VN nhưng đổi mô hình xác thực hoàn
   toàn, để ngỏ như một lựa chọn khi làm mục 1 nếu muốn bỏ hẳn mật khẩu.

**Hệ quả thiết kế:** `identity` thêm `phoneVerifiedAt`/`tokenVersion` + bảng `otp_challenges`
(append-only, TTL — mirror mẫu worklist); `SmsOtpPort` ở `common`; `JwtTokenProvider` thêm
claim `tokenVersion`; `JwtAuthenticationFilter`/`AccountStatusPort` mở rộng như mục 3.
Rate-limit OTP nằm ở tầng service (đếm theo SĐT trong DB) chứ không phải `RateLimitFilter`
(vốn theo IP/user — không chặn được một SĐT bị bơm từ nhiều IP).

> **Đã triển khai (nhà cung cấp đã chốt lại: SpeedSMS — đổi từ eSMS sau khảo sát giá, xem ghi chú SMS bên dưới):**
> - **Luồng đăng ký mới (breaking change, làm trước khi có người dùng thật):** `POST
>   /api/auth/register` tạo account PENDING (`Account.phoneVerifiedAt = null` — không đăng
>   nhập được) + gửi OTP, trả về `accountId` thay vì token; `POST /api/auth/verify-phone`
>   (SĐT + mã 6 số) kích hoạt và cấp token đầu tiên; `POST /api/auth/resend-verification`
>   gửi lại mã, trả 200 đồng nhất bất kể SĐT có account chờ hay không (chống dò). `login`
>   chặn account chưa xác minh **sau** bước check mật khẩu — chỉ chính chủ thấy trạng thái này.
> - **OTP:** `OtpChallenge` (bảng `otp_challenges`) lưu mã dưới dạng **BCrypt hash** (DB lộ
>   không thành đống mã đăng nhập hợp lệ — A02), TTL 5 phút, 5 lần thử/mã (mã đúng cũng bị
>   từ chối khi đã cạn lượt — trần phải *chặn* kẻ tấn công chứ không chỉ làm chậm), 3 mã/giờ/SĐT
>   đếm trong DB qua `OtpServiceImpl` (chống SMS-pumping đa IP), single-use (`usedAt`), mọi
>   nhánh thất bại trả **cùng một** thông điệp 401 (chi tiết đi vào log — A09). Mã đăng ký
>   không mở khóa được reset mật khẩu và ngược lại (match theo cặp SĐT+purpose).
> - **Quên/đổi mật khẩu:** `POST /api/auth/forgot-password` (200 đồng nhất, chỉ gửi cho SĐT
>   đã xác minh) + `POST /api/auth/reset-password` (OTP + mật khẩu mới); `PUT
>   /api/accounts/password` (đã đăng nhập, yêu cầu mật khẩu cũ) — đặt ngoài `/api/auth/**`
>   vì pattern đó là permitAll. Cả hai đường đều thu hồi mọi token qua mục kế tiếp.
> - **Thu hồi token:** `Account.tokenVersion` nhúng vào claim JWT; `changePassword` và
>   `deactivate` (đình chỉ — gỡ đình chỉ không hồi sinh phiên cũ) đều `tokenVersion++`;
>   `AccountStatusPort.isActive` đổi thành `isTokenValid(accountId, tokenVersion)` — cùng
>   một lookup PK per-request đã có ở G.5, đọc thêm một cột. Token phát hành trước khi có
>   claim được coi là version 0 (khớp default của cột backfill).
> - **SMS:** `app.sms.provider` chọn bean — dev/test dùng `LoggingSmsOtpPort` (ghi log + giữ
>   mã trong bộ nhớ cho integration test; **không bao giờ** được là bean prod vì log mã thật);
>   prod dùng **`SpeedSmsOtpPort`** (adapter thật, đã viết): khảo sát giá 2026 cho thấy không
>   nhà cung cấp nào có free tier thực sự cho SMS tới số VN, nhưng SpeedSMS có **credit tích
>   hợp miễn phí** (5 SMS đăng ký + 20 SMS demo API + 2.000đ) và `sms_type=4` (brand "Notify"
>   dùng chung) chạy được **không cần đăng ký brandname** — đúng nhu cầu staging/soft-launch;
>   giá production ~500đ/tin tương đương eSMS (~520đ) nên không mất gì khi đổi. Adapter:
>   `POST https://api.speedsms.vn/index.php/sms/send`, Basic auth (token làm username, mật
>   khẩu literal `"x"`), timeout connect 3s/read 5s, **không retry** trong adapter (OTP issue
>   nằm trong transaction đăng ký — thất bại → `SmsDeliveryException` → 503 "thử lại sau",
>   transaction rollback sạch; đường retry đã có sẵn là endpoint resend với con người bấm).
>   Response 200 nhưng `status != "success"` (hết credit, token sai...) cũng là thất bại —
>   không được nuốt. `SPEEDSMS_ACCESS_TOKEN` không có default (fail-loud như JWT_SECRET);
>   khi brandname CallMe được nhà mạng duyệt: `SPEEDSMS_SMS_TYPE=3` + `SPEEDSMS_SENDER` —
>   không đổi code. **A10 (SSRF) đã đánh giá lại tại đây** — outbound HTTP đầu tiên của hệ
>   thống: URL là hằng số cố định, không input người dùng nào chạm vào URL. **Đường nâng cấp
>   chi phí (để sau, đã quyết):** khi volume vượt ~10–20k tin/tháng, thêm adapter **ZNS-first**
>   (Zalo ZNS OTP ~300đ/tin, rẻ hơn ~40%) fallback sang SMS cho người không có Zalo — chỉ là
>   một bean `SmsOtpPort` mới + template ZNS đăng ký với Zalo, không chạm `OtpService`.
>   Account tồn tại trước migration được backfill `phoneVerifiedAt = now()` — không khóa
>   người dùng/admin hiện hữu. Migration: `V17__phone_verification_and_token_version.sql`.
> - Hồ sơ profile (Customer/Driver) tạo ở bước register có thể thành "mồ côi" nếu không bao
>   giờ verify — chấp nhận ở MVP, dọn dẹp định kỳ là việc tương lai (cùng nhóm với dọn
>   `otp_challenges` hết hạn).

## 5. Liên hệ với skeleton hiện tại

Các quy tắc/edge case ở mục 3 ánh xạ trực tiếp vào các điểm mở rộng đã có sẵn trong skeleton:

- `BookingService.requestDriverHome` (`modules/booking`) — nơi xử lý các edge case nhóm **A** (validate, idempotency, khóa giá) và khởi tạo tìm kiếm tài xế nhóm **B**.
- `DriverAvailabilityPort` (`common`) — hợp đồng cần mở rộng để hỗ trợ lọc theo "vị trí cập nhật gần đây", loại xe tài xế có thể lái, bán kính tìm kiếm động (nhóm **B.2, B.5**).
- `Trip` / `TripStatus` (`modules/trip`) — cần bổ sung trạng thái trung gian để phản ánh đúng vòng đời thực tế ở mục 2 (vd. tách `STARTED` thành "đang đến điểm đón" và "đã gặp khách, chuẩn bị lái"), phục vụ nhóm **C**.
- `BookingConfirmedEvent` / `BookingEventListener` — điểm cần cân nhắc outbox pattern khi xử lý nhóm **G.1**.
- Các module chưa triển khai (`identity`, `dispatch`, `location`, `pricing`, `payment`, `rating`, `notification`) nên được thiết kế **bắt đầu từ danh sách câu hỏi mở ở mục 4**, vì câu trả lời cho các câu hỏi đó quyết định trực tiếp cấu trúc dữ liệu và luồng nghiệp vụ của từng module.
