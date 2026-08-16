# 12 — Key concepts & ngân hàng câu hỏi phỏng vấn

Trang cuối: **kiến thức nền bắt buộc thuộc** + **câu hỏi có đáp án**. Đọc sau cùng, ôn lại nhiều lần.

---

## 1. Bốn thành phần Android — hỏi 100%

| Thành phần | Là gì | Bẫy hay hỏi |
|---|---|---|
| **Activity** | Một màn hình có giao diện | Vòng đời; `onSaveInstanceState` chạy **trước** `onStop` (API 28+) |
| **Service** | Chạy nền, không giao diện | Từ API 26, background service bị giới hạn → dùng **WorkManager** hoặc **Foreground Service** (bắt buộc có notification) |
| **BroadcastReceiver** | Nhận sự kiện hệ thống | Từ API 26, phần lớn broadcast **implicit** không nhận được qua manifest → phải đăng ký động |
| **ContentProvider** | Chia sẻ dữ liệu giữa app | Khởi tạo **trước** `Application.onCreate` — đó là cơ chế nhiều SDK tự khởi động (và làm chậm cold start) |

### Vòng đời Activity

```mermaid
stateDiagram-v2
    [*] --> onCreate
    onCreate --> onStart
    onStart --> onResume
    onResume --> Running
    Running --> onPause: mất tiêu điểm (dialog che)
    onPause --> onResume
    onPause --> onStop: không còn nhìn thấy
    onStop --> onRestart --> onStart
    onStop --> onDestroy --> [*]
    onStop --> ProcessDeath: hệ thống thu hồi RAM
    ProcessDeath --> onCreate: savedInstanceState != null
```

| Việc | Làm ở đâu |
|---|---|
| Khởi tạo binding, ViewModel, đăng ký observer | `onCreate` |
| Bắt đầu animation, camera, sensor | `onStart` / `onResume` |
| Dừng cập nhật UI tốn tài nguyên | `onPause` |
| Huỷ đăng ký, ngắt WebSocket | `onStop` |
| Lưu state nhỏ | `onSaveInstanceState` |

> ⚠️ `onDestroy` **không đảm bảo được gọi**. Nếu hệ thống kill process, nó bị bỏ qua. Đừng bao giờ
> đặt việc quan trọng (lưu dữ liệu) ở đó.

### `launchMode`

| Mode | Hành vi |
|---|---|
| `standard` | Mỗi lần start là một instance mới |
| `singleTop` | Đang ở đỉnh thì gọi `onNewIntent` thay vì tạo mới |
| `singleTask` | Chỉ một instance trong task; các Activity trên nó bị huỷ |
| `singleInstance` | Một instance, task riêng, không chứa Activity khác |

Repo này dùng `singleTop` cho ba tab — bắt buộc để `FLAG_ACTIVITY_REORDER_TO_FRONT` hoạt động.

---

## 2. Kotlin — câu hỏi hay gặp

**`val` vs `const val`**
`const val` là hằng lúc **biên dịch** (chỉ primitive/String, đặt ở top-level hoặc `companion object`),
được nhúng thẳng vào bytecode. `val` là chỉ-đọc lúc **chạy**, có thể gán bằng kết quả hàm.

**`lateinit` vs `by lazy`**

| | `lateinit var` | `by lazy` |
|---|---|---|
| Mutable | Có | Không (`val`) |
| Primitive | ❌ Không được | ✅ Được |
| Khi nào khởi tạo | Bạn tự gán | Lần truy cập đầu tiên |
| Thread-safe | Không | ✅ Mặc định `SYNCHRONIZED` |
| Dùng cho | `binding` gán trong `onCreate` | Phụ thuộc nặng, khởi tạo tốn kém |

**`Sequence` vs `List`**
`List` tạo collection trung gian sau **mỗi** toán tử; `Sequence` xử lý **lười**, từng phần tử qua
toàn bộ chuỗi. Với danh sách lớn + nhiều `map`/`filter`, `Sequence` nhanh hơn hẳn. Với danh sách
nhỏ thì `List` nhanh hơn (không có chi phí thiết lập).

**`inline` / `reified`**
`inline` chép thân hàm vào chỗ gọi → bỏ được chi phí tạo object cho lambda. `reified` chỉ dùng
được trong hàm `inline`, giữ lại thông tin kiểu lúc chạy (vượt qua type erasure) — nhờ đó viết được
`inline fun <reified T> Gson.fromJson(json: String): T`.

**`data class` cho gì**
Sinh `equals`/`hashCode`/`toString`/`copy`/`componentN`. `equals` là thứ khiến `DiffUtil` và
`distinctUntilChanged` của `StateFlow` hoạt động đúng — dùng `class` thường cho UiState là nguồn
gốc bug "UI không cập nhật" hoặc "cập nhật liên tục".

**Coroutines**

| Khái niệm | Nghĩa |
|---|---|
| `suspend` | Hàm có thể tạm dừng mà không chặn thread |
| `CoroutineScope` | Phạm vi sống; huỷ scope là huỷ mọi coroutine con |
| **Structured concurrency** | Coroutine con phải xong trước khi cha xong; cha huỷ thì con huỷ |
| `launch` vs `async` | `launch` trả `Job` (fire-and-forget); `async` trả `Deferred<T>` (có `await`) |
| `Dispatchers.Main/IO/Default` | UI / I-O chờ đợi / tính toán nặng |
| `withContext` | Đổi dispatcher, **không** tạo coroutine mới |
| `supervisorScope` | Con lỗi không kéo cả nhóm chết theo |
| `CancellationException` | **Luôn phải rethrow**, nếu không huỷ coroutine bị hỏng |

```kotlin
// ❌ Bug kinh điển: catch nuốt luôn CancellationException
try { api.call() } catch (e: Exception) { handle(e) }

// ✅ Đúng
try { api.call() }
catch (e: CancellationException) { throw e }
catch (e: Exception) { handle(e) }
```

`data/SafeApiCall.kt` của repo này xử lý đúng chỗ này.

**Flow vs StateFlow vs SharedFlow** → [trang 07 §4](07-router-state.md).

---

## 3. OOP & SOLID (JD nhắc trực tiếp)

| Nguyên tắc | Nghĩa | Ví dụ trong app |
|---|---|---|
| **S** — Single Responsibility | Một lớp, một lý do để thay đổi | ViewModel lo state, Repository lo dữ liệu — không trộn |
| **O** — Open/Closed | Mở để mở rộng, đóng để sửa | Thêm loại callback ForgeRock mới bằng lớp mới, không sửa `when` khổng lồ |
| **L** — Liskov | Lớp con thay được lớp cha | `Result.Success`/`Result.Error` dùng chung được ở mọi chỗ nhận `Result` |
| **I** — Interface Segregation | Nhiều interface nhỏ hơn một interface to | `ApiService` tách theo nghiệp vụ, không gộp 80 endpoint |
| **D** — Dependency Inversion | Phụ thuộc vào abstraction | ViewModel nhận `Repository` qua constructor, không tự `new` |

**Bốn tính chất OOP:** đóng gói (encapsulation), kế thừa (inheritance), đa hình (polymorphism),
trừu tượng (abstraction). Nếu bị hỏi *"ưu tiên kế thừa hay composition?"* → **composition**;
kế thừa sâu làm code cứng và khó test. Kotlin ép suy nghĩ này bằng cách để class `final` mặc định.

**Design pattern nên biết tên và ví dụ:**

| Pattern | Ở đâu trong app |
|---|---|
| Singleton | `AppContainer`, `SessionManager` |
| Factory | `ViewModelProvider.Factory` |
| Builder | `NotificationCompat.Builder`, `OkHttpClient.Builder` |
| Observer | `StateFlow` / `LiveData` |
| Repository | Tầng data |
| Adapter | `RecyclerView.Adapter`, mapper DTO → domain |
| Strategy | `Interceptor` khác nhau cho từng nhu cầu |
| Coordinator | `AppRouter` |

---

## 4. So sánh kiến trúc — hỏi rất nhiều

| | MVC | MVP | **MVVM** | MVI |
|---|---|---|---|---|
| Ai giữ logic UI | Controller | Presenter | **ViewModel** | Reducer |
| View ↔ logic | 2 chiều, chặt | Presenter giữ ref View (interface) | **Không giữ ref**, phát state | Không |
| Test | Khó | Được (mock View) | **Dễ nhất** | Dễ |
| State | Rải rác | Rải rác | Tập trung trong UiState | **Một state bất biến duy nhất** |
| Nhược | Controller phình to | Nhiều interface boilerplate | Dễ để ViewModel phình | Rườm rà cho màn đơn giản |

**Vì sao chọn MVVM cho Android:** `ViewModel` sống qua config change, `StateFlow`/`LiveData` là
observer sẵn có, và ViewModel không giữ tham chiếu View nên không rò rỉ. Đây là mô hình Google
khuyến nghị.

**Clean Architecture** — ba tầng `presentation` / `domain` / `data`, quy tắc phụ thuộc hướng vào
trong (domain không biết gì về Android). Đáng dùng khi: nhiều người cùng làm, logic nghiệp vụ phức
tạp, cần test kỹ. **Không** đáng dùng cho app 5 màn hình CRUD — nói được điều kiện áp dụng quan
trọng hơn ca ngợi.

---

## 5. Hiệu năng (JD: "tối ưu hiệu năng")

| Vấn đề | Đo bằng | Xử lý |
|---|---|---|
| **Khởi động chậm** | `adb shell am start -W`, Macrobenchmark | Baseline Profile, lazy init, `androidx.startup` |
| **Jank / giật khi cuộn** | Profile GPU Rendering, Perfetto | `DiffUtil` thay `notifyDataSetChanged`, giảm độ sâu layout, `ConstraintLayout` phẳng |
| **Overdraw** | Developer options → Debug GPU overdraw | Bỏ background chồng nhau, xoá `android:background` thừa |
| **Rò rỉ bộ nhớ** | **LeakCanary**, Memory Profiler | Không giữ `Context` trong singleton; `binding = null` ở `onDestroyView` |
| **ANR** | Play Console, `traces.txt` | Không I-O trên main thread; xem [11 §9](11-kho-khan-thuc-te.md) |
| **APK to** | APK Analyzer | R8, resource shrinking, **AAB** + split APK, WebP |
| **Tốn pin** | Battery Historian | Gộp job vào WorkManager, giảm wake lock, không giữ WebSocket ở nền |
| **Tốn data** | Network Profiler | Cache, gzip, phân trang |

**Ba nguyên nhân jank hay gặp nhất:** (1) layout lồng quá sâu (mỗi lần đo là O(n²) với
`LinearLayout` có `weight`), (2) tạo object trong `onBindViewHolder`, (3) tải ảnh không đúng kích
thước (dùng Glide/Coil với `override()`).

> **Nguyên tắc trả lời:** luôn nói **đo trước, sửa sau**. "Em thấy chậm nên em đoán là do X" là câu
> trả lời yếu. "Em profile bằng Perfetto, thấy frame drop ở `onBindViewHolder` do decode ảnh trên
> main thread" là câu trả lời mạnh.

---

## 6. Ngân hàng câu hỏi phỏng vấn (có đáp án)

### Android core

**1. ViewModel sống sót qua process death không?**
Không. Chỉ qua configuration change. Cần `SavedStateHandle` cho process death.

**2. `Serializable` vs `Parcelable`?**
`Serializable` dùng reflection, chậm, sinh nhiều rác. `Parcelable` viết tay (hoặc
`@Parcelize` của Kotlin), nhanh hơn nhiều, là chuẩn của Android.

**3. `Context` có mấy loại, khi nào dùng cái nào?**
`Application context` — sống theo app, dùng cho singleton, DB, không dùng để inflate view có theme.
`Activity context` — có theme, dùng cho view/dialog, **không** được giữ trong biến static
(`StaticFieldLeak`).

**4. `RecyclerView` hoạt động thế nào?**
`LayoutManager` quyết định sắp xếp; `Adapter` tạo/bind ViewHolder; `RecycledViewPool` tái sử dụng
ViewHolder khi cuộn khỏi màn hình. `DiffUtil` tính chênh lệch trên background thread và chỉ cập
nhật item thay đổi.

**5. Vì sao không được gọi `notifyDataSetChanged()`?**
Nó vẽ lại toàn bộ, mất animation, mất vị trí cuộn, và tốn hơn nhiều so với cập nhật từng item.
Dùng `ListAdapter` + `DiffUtil`.

**6. Memory leak hay gặp ở đâu?**
Inner class không static giữ Activity; `Handler` có message chờ; listener quên gỡ; singleton giữ
`Context`; coroutine không gắn scope. Lint của repo này bật `StaticFieldLeak` + `HandlerLeak` ở mức
`error` chính vì thế.

### Kiến trúc & code

**7. Repository để làm gì, bỏ đi được không?**
Nó là **ranh giới**: ViewModel không biết dữ liệu đến từ mạng hay DB. Bỏ đi thì mỗi ViewModel phải
tự biết Retrofit lẫn Room, không test được, và không có single source of truth.

**8. Dependency Injection giải quyết vấn đề gì?**
Bỏ việc lớp tự tạo phụ thuộc → thay được bằng fake khi test, và quản lý vòng đời/singleton ở một
chỗ. Không nhất thiết phải Hilt: service locator thủ công như `AppContainer` là bước đầu hợp lý.

**9. Unit test và Instrumented test khác gì?**
Unit test chạy trên JVM (nhanh, không cần thiết bị) — hợp cho ViewModel, mapper, UseCase.
Instrumented test chạy trên thiết bị (chậm) — hợp cho DB, UI (Espresso). Tỷ lệ nên theo kim tự tháp:
nhiều unit, ít UI.

**10. Test ViewModel có coroutine thế nào?**
`kotlinx-coroutines-test`: `runTest`, `StandardTestDispatcher`, và `MainDispatcherRule` để thay
`Dispatchers.Main`. Repository thì fake bằng interface, không mock Retrofit.

### Mạng & bảo mật

**11. HTTPS bảo vệ khỏi cái gì, không bảo vệ khỏi cái gì?**
Bảo vệ: nghe lén, sửa đổi trên đường truyền. **Không** bảo vệ: máy bị root, người dùng tự cài CA,
lỗ hổng phía server, dữ liệu sau khi đã tới máy. Vì thế mới cần cert pinning + mã hoá lưu trữ.

**12. JWT gồm những gì? Verify ở đâu?**
`header.payload.signature`, base64url. Payload **không được mã hoá** — ai cũng đọc được, nên
tuyệt đối không để dữ liệu nhạy cảm trong đó. Chữ ký phải verify ở **server**; client chỉ đọc `exp`
để biết khi nào cần refresh.

**13. Làm sao biết token sắp hết hạn?**
Đọc `exp` trong JWT và refresh chủ động trước 1–2 phút, **kết hợp** phản ứng với 401 — vì đồng hồ
máy có thể sai.

**14. Chống man-in-the-middle thế nào?**
Certificate pinning + `network_security_config` chặn user CA ở release + phát hiện proxy. Và nhớ:
không có gì tuyệt đối, nên dữ liệu nhạy cảm vẫn cần mã hoá ở tầng ứng dụng.

### Nghiệp vụ ngân hàng

**15. Người dùng bấm "Chuyển tiền" hai lần thì sao?**
Ba lớp: khoá nút ngay khi bấm, `Idempotency-Key` gửi kèm, và server khử trùng lặp. Thiếu lớp nào
cũng có ngày chuyển hai lần.

**16. Timeout khi đang chuyển tiền — xử lý thế nào?**
**Không tự động retry.** Gọi API tra cứu trạng thái lệnh theo `Idempotency-Key`/`transactionId`,
rồi mới hiện kết quả. Hiện "Thất bại" khi thực ra đã thành công là sự cố nghiêm trọng.

**17. Vì sao không lưu số dư vào cache?**
Số dư thay đổi liên tục. Hiện số cũ có thể khiến khách hàng ra quyết định sai. Nếu buộc phải cache,
phải ghi rõ "cập nhật lúc HH:mm" và làm mới ngay khi vào màn hình.

**18. Tiền lưu kiểu dữ liệu gì?**
`Long` theo đơn vị nhỏ nhất, hoặc `BigDecimal`. **Không bao giờ** `Double`/`Float` — sai số dấu
phẩy động.

**19. App bị root có cho dùng không?**
Tuỳ chính sách. Cách làm phổ biến: phát hiện → báo backend → backend hạ hạn mức hoặc yêu cầu xác
thực bổ sung. Chặn cứng ở client thì bypass được, mà lại chặn nhầm người dùng bình thường.

**20. Bảo mật dữ liệu khi app vào nền?**
`FLAG_SECURE` (chặn screenshot + che trong Recents), khoá lại sau N giây nền, xoá dữ liệu nhạy cảm
khỏi RAM khi khoá.

### Câu hỏi mềm

**21. "Em thấy khó nhất khi chuyển từ Flutter sang native là gì?"**
Process death và vòng đời. Flutter giữ toàn bộ state trong Dart VM nên không có khái niệm này;
Android bắt buộc phải nghĩ tới `SavedStateHandle`, `onSaveInstanceState`, và việc Activity có thể
bị huỷ bất cứ lúc nào.

**22. "Em làm gì khi không đồng ý với thiết kế của BA/UX?"**
Nêu vấn đề bằng **dữ liệu**, không bằng cảm tính: ví dụ Class 2 không dùng được `CryptoObject` nên
không đạt yêu cầu bảo mật. Đề xuất phương án thay thế. Nếu vẫn bị quyết ngược, ghi lại rủi ro bằng
văn bản rồi thực hiện — đó là cách làm đúng trong môi trường ngân hàng có tuân thủ.

**23. "Em học công nghệ mới thế nào?"**
Cụ thể: đọc release note của Android mỗi bản, làm thử trong repo cá nhân
(chính repo `NativeKotlin` này), đọc source của thư viện khi tài liệu không đủ.

**24. "Em có câu hỏi gì cho chúng tôi không?"**
**Luôn phải có.** Gợi ý tốt: quy trình phát hành và tần suất; đội có bao nhiêu người Android; kiến
trúc hiện tại là gì và có kế hoạch chuyển sang Compose không; SIT/UAT được kiểm thử thế nào; đội có
làm Clean Architecture hay MVVM đơn giản. Hỏi được câu cụ thể là dấu hiệu bạn đã suy nghĩ nghiêm túc.

---

## 7. Checklist đêm trước buổi phỏng vấn

- [ ] Vẽ lại được sơ đồ [luồng một phiên ngân hàng](README.md#4-luồng-một-phiên-ngân-hàng-từ-đầu-tới-cuối) trên giấy
- [ ] Nói được vì sao `CryptoObject` quan trọng hơn callback `onSuccess`
- [ ] Giải thích được single-flight refresh token trong 2 phút
- [ ] Phân biệt data message vs notification message của FCM
- [ ] Nói được ba lý do chọn WebSocket thay vì polling
- [ ] Nhớ 5 câu chuyện STAR, mỗi câu 90 giây
- [ ] Biết vì sao tiền không lưu bằng `Double`
- [ ] Biết `ViewModel` **không** sống sót qua process death
- [ ] Chuẩn bị 3 câu hỏi ngược cho nhà tuyển dụng
- [ ] Mở sẵn repo `NativeKotlin` để nói về code thật của mình nếu được hỏi
