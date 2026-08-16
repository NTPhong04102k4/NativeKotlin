# 11 — Khó khăn thực tế & cách xử lý (kể theo STAR)

Phỏng vấn banking luôn có câu: *"Kể một lỗi khó nhất em từng gặp."* Câu trả lời tốt phải theo
**STAR**: **S**ituation (bối cảnh) → **T**ask (nhiệm vụ) → **A**ction (hành động) → **R**esult
(kết quả + bài học). Mỗi câu chuyện **90 giây**, không dài hơn.

Dưới đây là 10 câu chuyện lấy từ đúng những gì bạn đã làm. Đọc, chọn 4–5 cái ruột, tập nói to.

---

## 1. ForgeRock: luồng xác thực treo im lặng, không lỗi, không log

**S** — Tích hợp Journey của ForgeRock. Luồng đăng nhập bằng mật khẩu chạy tốt, nhưng khi bật thêm
node OTP thì app đứng im ở màn hình loading. Không exception, không log, không timeout.

**T** — Tìm nguyên nhân trong khi không có bất kỳ dấu vết nào.

**A** — Đặt breakpoint trong `onCallbackReceived` thì thấy nó **được gọi**, nhưng sau đó không có gì
nữa. Đọc kỹ tài liệu SDK mới hiểu: `onCallbackReceived` **bắt buộc** phải kết thúc bằng
`node.next(...)`. Với các node tự động (username/password đã có sẵn) thì gọi luôn được. Nhưng node
OTP cần người dùng nhập → code của tôi return khỏi hàm để chờ UI, và **không ai gọi `node.next()`**
nữa. SDK không có timeout nên nó chờ mãi.

**A (cách sửa)** — Tách thành máy trạng thái: giữ tham chiếu `pendingNode`, phát sự kiện lên UI,
và chỉ gọi `node.next()` sau khi người dùng bấm xác nhận. Thêm timeout 90 giây phía app để không
bao giờ treo vô hạn.

**R** — Luồng MFA chạy đúng. Bài học: **API bất đồng bộ mà không có timeout thì lỗi biểu hiện là
"không có gì xảy ra"**, khó hơn nhiều so với một exception. Từ đó tôi có nguyên tắc: mọi lời gọi
ra ngoài đều phải có timeout do mình kiểm soát.

---

## 2. Bridge: crash `@UiThread must be executed on the main thread`

**S** — Sau khi nối ForgeRock qua MethodChannel, app crash ngẫu nhiên khi đăng nhập — khoảng 1/5 lần.

**T** — Lỗi không tái hiện đều, QA báo "thỉnh thoảng".

**A** — Đọc stack trace: `Methods marked with @UiThread must be executed on the main thread`. Nguyên
nhân: callback của ForgeRock SDK chạy trên background thread, mà `MethodChannel.Result.success()`
bắt buộc gọi trên main thread. Lý do nó chỉ crash **thỉnh thoảng**: khi có cache/mạng nhanh, SDK
đôi lúc trả về đồng bộ ngay trên main thread nên tình cờ chạy được.

**A (cách sửa)** — Bọc mọi lời gọi `result.success/error` và `eventSink.success` trong
`Handler(Looper.getMainLooper()).post { }`. Sau đó viết luôn một lớp `SingleResult` bọc `Result` để
chống lỗi thứ hai vừa phát hiện: SDK gọi cả `onSuccess` lẫn `onException` trong một số nhánh, gây
`IllegalStateException: Reply already submitted`.

**R** — Hết crash. Bài học: **lỗi "thỉnh thoảng" thường là lỗi threading**, và cách chữa không phải
là thử lại cho tới khi hết mà là tìm ra bất biến bị vi phạm.

---

## 3. Google Sign-In — bốn vấn đề, cả bốn đều tốn thời gian

Đây là phần bạn hỏi cụ thể. Bốn vấn đề theo thứ tự hay gặp:

### 3.1 `ApiException: 10` (`DEVELOPER_ERROR`) — SHA-1 không khớp

**Nguyên nhân:** SHA-1 fingerprint khai trong Firebase/Google Cloud Console không khớp chứng chỉ
đang ký APK. Thông báo lỗi chỉ là số `10`, **không nói gì thêm**.

Ba fingerprint khác nhau mà ai cũng nhầm:

| Bản build | Chứng chỉ | Lấy ở đâu |
|---|---|---|
| Debug | `~/.android/debug.keystore` | `./gradlew signingReport` |
| Release tự ký | keystore của công ty | `keytool -list -v -keystore release.jks` |
| **Play Store** | **Chứng chỉ của Google** (Play App Signing) | Play Console → Setup → App integrity |

> ⚠️ **Bẫy chết người:** khi bật **Play App Signing**, Google **ký lại** app bằng chứng chỉ của họ.
> App test nội bộ (ký bằng keystore của bạn) đăng nhập Google được, nhưng bản tải từ Play Store
> thì **không** — vì SHA-1 đã khác. Phải khai **cả ba** fingerprint. Đây đúng là loại lỗi "chỉ xảy
> ra ở production" mà JD nhắc tới ở gạch "xử lý vấn đề phát sinh trong Production".

**Nhân lên với số flavor:** 3 flavor × 3 loại chứng chỉ = **9 fingerprint** phải khai. Thiếu một
cái là một môi trường chết. Đây cũng là lý do vấn đề này rất hay xuất hiện ở SIT/UAT mà không thấy
ở dev.

### 3.2 Nhầm `serverClientId` với `androidClientId`

```kotlin
// ❌ SAI — dùng Android client ID -> idToken luôn null
.requestIdToken(BuildConfig.ANDROID_CLIENT_ID)

// ✅ ĐÚNG — phải là WEB client ID, kể cả khi đang viết app Android
.requestIdToken(BuildConfig.WEB_CLIENT_ID)
```

**Vì sao:** `idToken` được cấp cho **backend** để backend xác minh, nên `audience` phải là web
client. Triệu chứng khi sai: đăng nhập "thành công", có email và ảnh đại diện, nhưng
`account.idToken == null` → backend không xác thực được. Rất dễ tưởng là lỗi backend.

Trong repo `NativeKotlin` này, `GoogleAuthHelper.requestIdToken` đang bị **comment lại** đúng vì
chưa có web client ID — đó là một ví dụ thật ngay trong code.

### 3.3 `GoogleSignInApi` đã deprecated

`GoogleSignInClient` / `GoogleSignInOptions` đã bị khai tử, thay bằng **Credential Manager** +
**Sign in with Google**. Nếu bị hỏi "giờ làm lại thì sao":

```kotlin
val googleOption = GetGoogleIdOption.Builder()
    .setServerClientId(BuildConfig.WEB_CLIENT_ID)
    .setFilterByAuthorizedAccounts(false)     // true = chỉ hiện tài khoản đã từng đăng nhập
    .setAutoSelectEnabled(true)
    .build()

val request = GetCredentialRequest.Builder().addCredentialOption(googleOption).build()
val response = CredentialManager.create(context).getCredential(activity, request)
```

Lợi ích: gộp chung passkey + password + Google vào **một** bottom sheet của hệ thống, UX nhất quán,
và đúng hướng đi lâu dài của Android.

### 3.4 Google Sign-In trong app ngân hàng — vấn đề nghiệp vụ, không phải kỹ thuật

Đây là ý **quan trọng nhất** để nói, vì nó cho thấy bạn nghĩ như người làm ngân hàng:

> *"Với app ngân hàng, đăng nhập bằng Google thường **không được chấp nhận làm phương thức chính**.
> Lý do là danh tính khách hàng phải gắn với eKYC/CIF của ngân hàng, không thể uỷ thác cho bên thứ
> ba; và nếu tài khoản Google bị chiếm thì tài khoản ngân hàng bị chiếm theo. Ở dự án cũ, Google
> Sign-In chỉ dùng cho phần phi tài chính — đăng ký nhận tin, khảo sát. Luồng tài chính vẫn phải
> qua ForgeRock."*

Nếu người phỏng vấn hỏi ngược *"thế sao vẫn làm?"* — trả lời: giảm ma sát cho người dùng mới ở
bước tìm hiểu sản phẩm, sau đó vẫn phải định danh đầy đủ trước khi mở tài khoản.

---

## 4. Face / sinh trắc học — năm vấn đề

Phần này bạn cũng hỏi cụ thể. Cả năm đều là chuyện thật hay gặp.

### 4.1 Android "face unlock" phần lớn là Class 2, không dùng được cho banking

| | Android | iOS |
|---|---|---|
| Nhận diện khuôn mặt | Đa số máy là **camera 2D** → `BIOMETRIC_WEAK` (Class 2) | **Face ID luôn 3D** (TrueDepth) |
| Dùng được `CryptoObject`? | ❌ Không với Class 2 | ✅ Luôn được |
| Mở được bằng ảnh chụp? | Nhiều máy tầm trung: **có** | Không |

**Hệ quả thực tế:** cùng một yêu cầu "đăng nhập bằng khuôn mặt", iOS làm được, Android thì **rất
nhiều máy không**. Bên nghiệp vụ nhìn iPhone chạy được rồi hỏi "sao Android không làm được".

**Cách xử lý:** viết tài liệu giải thích Class 2 vs Class 3, thống nhất phương án: Android yêu cầu
`BIOMETRIC_STRONG` — máy nào chỉ có face 2D thì hiện **vân tay**; máy không có vân tay thì ẩn hẳn
tính năng và dùng mã PIN của app. **Không hạ chuẩn xuống Class 2**, dù bị thúc.

> Kể được đoạn "em từ chối hạ chuẩn bảo mật dù bị thúc, và thuyết phục bằng tài liệu" là một trong
> những câu trả lời gây ấn tượng nhất trong phỏng vấn ngân hàng.

### 4.2 `KeyPermanentlyInvalidatedException` — người dùng thêm vân tay là mất đăng nhập

Chi tiết đã ở [trang 02 §3](02-biometric-webauthn.md). Tóm tắt cho phần kể chuyện:

**S** — Người dùng báo "tự nhiên không đăng nhập bằng vân tay được nữa". QA không tái hiện được.
**A** — Tìm ra là do `setInvalidatedByBiometricEnrollment(true)`: thêm/xoá vân tay trong Cài đặt là
hệ thống huỷ khoá. Đây là **tính năng bảo mật, không phải bug** — nếu không có nó, người khác thêm
vân tay của họ vào máy là đăng nhập được vào app ngân hàng của bạn.
**A (sửa)** — Bắt exception, xoá khoá, dẫn người dùng đăng nhập lại bằng mật khẩu rồi bật lại tính
năng, kèm thông báo **giải thích lý do** thay vì báo "Đã có lỗi xảy ra".
**R** — Số ticket hỗ trợ giảm hẳn, chủ yếu nhờ **thông báo giải thích được**, không phải nhờ code.

### 4.3 Android và iOS lệch hành vi khi xác thực thất bại

| | Android `BiometricPrompt` | iOS `LAContext` |
|---|---|---|
| Sai 5 lần | `ERROR_LOCKOUT`, khoá **30 giây** | Khoá, phải nhập passcode |
| Sai nhiều lần nữa | `ERROR_LOCKOUT_PERMANENT` cho tới khi mở khoá máy | `LAErrorBiometryLockout` |
| Người dùng bấm "Huỷ" | `ERROR_NEGATIVE_BUTTON` | `LAErrorUserCancel` |
| Hệ thống huỷ (cuộc gọi đến) | `ERROR_CANCELED` | `LAErrorSystemCancel` |

Phải map hai bộ mã lỗi về **một** enum chung ở tầng bridge, nếu không Flutter phải viết `if
(Platform.isIOS)` khắp nơi. Đây là ví dụ cụ thể của việc "bridge không chỉ là chuyển tiếp lời gọi,
mà là **chuẩn hoá hai mô hình khác nhau**".

### 4.4 Xoay máy khi prompt đang hiện

Prompt biến mất, callback bắn vào Activity đã chết → crash hoặc treo. Cách sửa: giữ trạng thái
"đang xác thực" trong ViewModel, tạo `BiometricPrompt` trong `onCreate` (không phải `onResume`), và
dùng `ContextCompat.getMainExecutor()`. Phía plugin Flutter phải xử lý
`onReattachedToActivityForConfigChanges`.

### 4.5 Máy Samsung có Knox, máy Xiaomi có MIUI

Trên một số ROM tuỳ biến, `BiometricManager.canAuthenticate()` trả về `SUCCESS` nhưng
`authenticate()` lại lỗi ngay lập tức. Không có cách nào ngoài **danh sách thiết bị đặc biệt** và
fallback. Bài học nói được: *"Không tin tuyệt đối kết quả `canAuthenticate()`; luôn phải có đường
lùi sang mật khẩu."*

---

## 5. Cân Bluetooth: chạy trên Samsung, chết trên Xiaomi

Chi tiết ở [trang 13 §3](13-bluetooth-eatsy.md). Tóm tắt STAR:

**S** — App Eatsy đọc cân BLE. Chạy tốt trên Samsung, trên Xiaomi thì kết nối được nhưng không nhận
được số liệu.
**A** — Dùng nRF Connect kiểm tra thì thấy cân **có** gửi notify. Vấn đề nằm ở phía app: tôi gọi
liên tiếp nhiều thao tác GATT, mà Android **chỉ cho một thao tác tại một thời điểm**. Samsung có
buffer rộng hơn nên tình cờ chạy được. Ngoài ra tôi cũng quên bước ghi **descriptor CCCD**.
**A (sửa)** — Viết `GattQueue` xếp hàng thao tác, chỉ chạy cái sau khi cái trước có callback, kèm
timeout cho trường hợp thiết bị không trả lời.
**R** — Chạy ổn định trên mọi máy đã test. Bài học: **API bất đồng bộ không tài liệu thì phải giả
định điều kiện xấu nhất**, và "chạy được trên máy tôi" không có nghĩa là đúng.

---

## 6. Cấu hình 3 flavor: `google-services.json` sai package

**S** — Build SIT thành công nhưng FCM không bao giờ nhận được thông báo. Build prod thì bình thường.
**A** — `applicationIdSuffix = ".sit"` khiến applicationId thành `vn.bank.mobile.sit`, trong khi
`google-services.json` chỉ khai `vn.bank.mobile`. Firebase không báo lỗi lúc chạy — nó chỉ **im lặng
không đăng ký được token**.
**A (sửa)** — Tách file cho từng thư mục flavor (`app/src/sit/google-services.json`), và thêm bước
kiểm tra trong CI: build xong thì assert `applicationId` khớp `package_name` trong file JSON.
**R** — Bài học: **cấu hình sai thường không gây lỗi, chỉ gây im lặng**. Từ đó tôi luôn thêm một
"smoke test cấu hình" cho mỗi môi trường: sau khi cài, màn hình debug hiện base URL + FCM token +
tên flavor, tester nhìn là biết ngay đang chạy môi trường nào.

> Mẹo này rất được lòng QA và đáng nói ra: nó giải quyết đúng vấn đề "bug này ở môi trường nào?".

---

## 7. Chứng chỉ hết hạn làm cert pinning giết cả app ngoài thị trường

**S** — (Tình huống điển hình của ngành, nên chuẩn bị dù bạn chưa gặp trực tiếp.) Ngân hàng gia hạn
chứng chỉ TLS. App có pin cứng **một** SHA-256 của leaf certificate. Sau khi gia hạn, **toàn bộ**
app ngoài thị trường không gọi được API.
**T** — Không thể sửa bằng backend; app phải cập nhật, mà phát hành + người dùng cập nhật mất nhiều ngày.
**A** — Xử lý ngắn hạn: backend giữ song song chứng chỉ cũ (nếu còn hạn) hoặc chuyển tạm sang
endpoint chưa pin. Dài hạn: (1) pin theo **intermediate CA** thay vì leaf, (2) luôn khai **≥ 2 pin**
gồm một pin dự phòng, (3) thêm `expiration` trong `network_security_config` để pin tự hết hiệu lực
thay vì làm chết app, (4) đưa lịch gia hạn chứng chỉ vào lịch phát hành của app.
**R** — Bài học: **biện pháp bảo mật cũng là rủi ro vận hành**. Bất kỳ thứ gì có thể "hết hạn" đều
phải có kế hoạch cho ngày nó hết hạn.

---

## 8. Refresh token: 5 request song song đá người dùng ra ngoài

**S** — Màn hình Dashboard gọi 5 API song song. Người dùng thỉnh thoảng bị đăng xuất ngay khi mở app.
**A** — Access token hết hạn → cả 5 cùng nhận 401 → cả 5 cùng gọi refresh. Backend bật **refresh
token rotation**: lần đầu thành công làm token cũ vô hiệu, 4 lần sau thất bại → backend coi là dấu
hiệu token bị đánh cắp → **huỷ toàn bộ phiên**.
**A (sửa)** — Chuyển sang `Authenticator` của OkHttp với `Mutex`: chỉ một request refresh, các
request khác chờ và **so sánh token cũ với token hiện tại** để biết đã có người refresh xong rồi.
Chi tiết ở [trang 05 §3](05-api-bao-mat.md).
**R** — Hết đăng xuất bất thường. Bài học: **lỗi đồng thời chỉ xuất hiện khi có tải thật**, không
bao giờ thấy khi test tay từng màn hình.

---

## 9. ANR do `SharedPreferences.apply()`

**S** — Play Console báo tỷ lệ ANR tăng, tập trung ở `Activity.onStop`.
**A** — `apply()` ghi nền, nhưng hệ thống **chặn ở `onPause`/`onStop`** để chờ ghi xong
(`QueuedWork.waitToFinish()`). App ghi khá nhiều key mỗi lần chuyển màn → dồn lại thành ANR trên
máy yếu.
**A (sửa)** — Chuyển sang **DataStore** cho phần cấu hình, gộp nhiều lần ghi thành một, và bỏ hẳn
việc ghi trong `onPause`.
**R** — ANR rate giảm rõ. Bài học: `apply()` **không** hoàn toàn bất đồng bộ như tên gọi gợi ý.
Đây là câu trả lời rất tốt cho gạch "tối ưu hiệu năng" trong JD.

---

## 10. Bảo trì hệ thống lúc 2 giờ sáng mà app không biết

**S** — Ngân hàng bảo trì core banking hằng đêm. App hiện "Đã có lỗi xảy ra" cho mọi thao tác →
tổng đài nhận rất nhiều cuộc gọi.
**A** — Thêm xử lý riêng cho mã `503` + header `X-Maintenance-Until`: hiện màn hình bảo trì có
**thời gian dự kiến hoàn tất**, và tắt các nút giao dịch thay vì để người dùng bấm rồi thất bại.
Kết hợp Remote Config để bật/tắt thông báo bảo trì mà không cần phát hành bản mới.
**R** — Cuộc gọi tổng đài giảm mạnh trong khung giờ bảo trì. Bài học: **thông báo lỗi là một phần
của sản phẩm**, không phải phần phụ. "Đã có lỗi xảy ra" là câu nói vô nghĩa với người dùng.

---

## Cách chọn câu chuyện cho từng câu hỏi

| Câu hỏi | Kể chuyện số |
|---|---|
| "Lỗi khó nhất em từng gặp?" | **1** (treo im lặng) hoặc **8** (refresh đồng thời) |
| "Kể lần em phải bảo vệ quan điểm kỹ thuật" | **4.1** (từ chối hạ chuẩn xuống Class 2) |
| "Em xử lý sự cố production thế nào?" | **7** (cert pinning) hoặc **10** (bảo trì) |
| "Em tối ưu hiệu năng thế nào?" | **9** (ANR SharedPreferences) |
| "Em debug lỗi không tái hiện được ra sao?" | **2** (threading) hoặc **5** (BLE Xiaomi) |
| "Em phối hợp với QA/BA thế nào?" | **6** (màn hình debug cấu hình môi trường) |
| "Em học được gì từ sai lầm?" | **5** — "chạy trên máy tôi" không có nghĩa là đúng |

**Ba nguyên tắc khi kể:**

1. **Nói số liệu nếu có** — "ANR giảm từ 1.2% xuống 0.3%" mạnh hơn "cải thiện nhiều".
2. **Luôn kết bằng bài học**, không kết bằng "rồi em sửa xong".
3. **Nhận sai nếu là lỗi của mình.** "Em thiếu bước ghi CCCD" đáng tin hơn "SDK viết dở".
