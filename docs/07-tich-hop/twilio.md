# Twilio — OTP / SMS / Voice

> **Trạng thái:** dự án **chưa tích hợp** Twilio. Tuy nhiên manifest **đã khai báo** các quyền
> `RECEIVE_SMS` / `READ_SMS` và có `SystemEventReceiver` nghe `SMS_RECEIVED` — trang này giải thích
> quan hệ giữa hai thứ đó và Twilio.

## 1. Ba dịch vụ hay dùng

| Dịch vụ | Việc | Có SDK Android? |
|---|---|---|
| **Verify** | Gửi & kiểm tra OTP (SMS, gọi, WhatsApp, email) | Không — gọi qua backend |
| **Programmable SMS** | Gửi/nhận SMS tuỳ ý | Không — gọi qua backend |
| **Voice / Video** | Gọi thoại, video trong app | **Có** SDK |
| **Conversations** | Chat đa kênh | **Có** SDK |

## 2. ⚠️ Luật quan trọng nhất: không gọi Twilio từ app

```kotlin
// ✗ TUYỆT ĐỐI KHÔNG
val client = TwilioRestClient.Builder(ACCOUNT_SID, AUTH_TOKEN).build()
```

`AUTH_TOKEN` trong APK là **bị lộ**. APK giải nén và dịch ngược được trong vài phút; kẻ tấn công lấy
được token sẽ gửi SMS trên hoá đơn của bạn — đây là kiểu lạm dụng tốn kém và rất phổ biến
("SMS pumping").

ProGuard/R8 **không** giúp gì: chuỗi vẫn nằm trong binary.

```
✓ Kiến trúc đúng:

  App  ──HTTPS──►  Backend của bạn  ──►  Twilio API
                   (giữ ACCOUNT_SID + AUTH_TOKEN)
                   (rate limit theo user/IP)
```

App **chỉ** gọi backend. Backend mới gọi Twilio.

## 3. Luồng OTP qua backend

### 3.1. Hợp đồng API

```
POST /api/otp/request     { "phone": "+84901234567" }
  → 200 { "requestId": "abc123", "expiresInSeconds": 300 }

POST /api/otp/verify      { "requestId": "abc123", "code": "123456" }
  → 200 { "accessToken": "...", "refreshToken": "..." }
  → 400 { "error": "INVALID_CODE", "attemptsLeft": 2 }
```

### 3.2. Retrofit — dự án đã có sẵn

```kotlin
// data/remote/OtpApi.kt
interface OtpApi {
    @POST("api/otp/request")
    suspend fun requestOtp(@Body body: OtpRequestDto): OtpRequestResponseDto

    @POST("api/otp/verify")
    suspend fun verifyOtp(@Body body: OtpVerifyDto): AuthResponseDto
}

data class OtpRequestDto(val phone: String)
data class OtpRequestResponseDto(val requestId: String, val expiresInSeconds: Int)
data class OtpVerifyDto(val requestId: String, val code: String)
```

### 3.3. Repository

```kotlin
class OtpRepository(private val api: OtpApi) {

    suspend fun requestOtp(phone: String): Result<OtpChallenge> = try {
        val response = api.requestOtp(OtpRequestDto(phone.toE164()))
        Result.Success(OtpChallenge(response.requestId, response.expiresInSeconds))
    } catch (e: HttpException) {
        Result.Error(e)
    } catch (e: IOException) {
        Result.Error(e)
    }

    suspend fun verifyOtp(requestId: String, code: String): Result<AuthTokens> = try {
        Result.Success(api.verifyOtp(OtpVerifyDto(requestId, code)).toDomain())
    } catch (e: HttpException) {
        Result.Error(e)
    }
}
```

`Result` là `data/Result.kt` của dự án — import tường minh.

### 3.4. Chuẩn hoá số điện thoại

Twilio yêu cầu định dạng **E.164** (`+84901234567`). Người dùng Việt Nam thường gõ `0901234567`.

```kotlin
// Nên dùng libphonenumber thay vì tự viết
fun String.toE164(region: String = "VN"): String {
    val util = PhoneNumberUtil.getInstance()
    val number = util.parse(this, region)
    return util.format(number, PhoneNumberUtil.PhoneNumberFormat.E164)
}
```

Cần `com.googlecode.libphonenumber:libphonenumber`. Tự viết regex sẽ sai với số đầu 84, +84, 084,
số 10 vs 11 chữ số cũ...

## 4. Tự động điền OTP — dùng SMS Retriever, KHÔNG dùng quyền SMS

Đây là điểm quan trọng nhất liên quan tới hiện trạng dự án.

**Manifest hiện khai báo `RECEIVE_SMS` và `READ_SMS`.** Hai quyền này:

- Là **dangerous permission**, cần xin lúc chạy (app **chưa** xin — xem
  [Permissions](../02-android-core/permissions.md))
- Bị **Google Play kiểm duyệt rất chặt**. Chỉ app nhắn tin mặc định hoặc có lý do được duyệt trước
  mới được dùng. Đọc OTP **không** phải lý do được chấp nhận
- **Không cần thiết** — có API riêng cho đúng việc này

### SMS Retriever API — không cần quyền nào

```kotlin
// Cần com.google.android.gms:play-services-auth-api-phone
private val smsRetrieverReceiver = object : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != SmsRetriever.SMS_RETRIEVED_ACTION) return

        val extras = intent.extras ?: return
        val status = extras.get(SmsRetriever.EXTRA_STATUS) as Status

        if (status.statusCode == CommonStatusCodes.SUCCESS) {
            val message = extras.getString(SmsRetriever.EXTRA_SMS_MESSAGE) ?: return
            extractCode(message)?.let { binding.otpInput.setText(it) }
        }
    }
}

private fun startSmsRetriever() {
    SmsRetriever.getClient(this).startSmsRetriever()

    ContextCompat.registerReceiver(
        this,
        smsRetrieverReceiver,
        IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION),
        SmsRetriever.SEND_PERMISSION,
        null,
        ContextCompat.RECEIVER_EXPORTED,
    )
}
```

Điều kiện: nội dung SMS phải kết thúc bằng **app hash 11 ký tự** của app bạn:

```
Ma xac thuc AI Assistant cua ban la 123456
FA+9qCX9VSu
```

Lấy app hash:

```kotlin
// Chỉ chạy ở bản debug để lấy hash, KHÔNG để lại trong release
AppSignatureHelper(this).appSignatures.forEach { Log.d("AppHash", it) }
```

> Hash khác nhau giữa bản debug và release (khác keystore). Phải cấu hình cả hai ở backend.

Backend gửi SMS qua Twilio với template có sẵn hash đó.

### Khuyến nghị cho dự án

| Việc | Lý do |
|---|---|
| **Bỏ `RECEIVE_SMS`, `READ_SMS`, `READ_PHONE_STATE`** khỏi manifest | Rủi ro bị Play từ chối, và không cần cho OTP |
| Bỏ hoặc thu hẹp `SystemEventReceiver` | Hiện chỉ ghi log, không mang lại giá trị nào |
| Dùng SMS Retriever nếu cần tự điền OTP | Không cần quyền |

Đây là kết luận đã nêu ở
[Permissions §7](../02-android-core/permissions.md#7-việc-cần-làm) — Twilio làm nó rõ ràng hơn:
**không có lý do kỹ thuật nào để giữ quyền SMS.**

## 5. Twilio Voice SDK

Nếu cần gọi thoại trong app (đây là thứ **có** SDK Android):

```toml
[versions]
twilioVoice = "6.6.4"

[libraries]
twilio-voice = { group = "com.twilio", name = "voice-android", version.ref = "twilioVoice" }
```

Access token vẫn phải lấy từ backend, không bao giờ nhúng credential:

```kotlin
class VoiceRepository(private val api: VoiceApi) {

    suspend fun connect(to: String): Result<Call> {
        val token = api.fetchVoiceToken().token      // backend sinh, TTL ngắn
        val options = ConnectOptions.Builder(token)
            .params(mapOf("To" to to))
            .build()
        // ...
    }
}
```

Voice cần thêm:

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
```

`RECORD_AUDIO` là dangerous permission — cần luồng xin quyền runtime.

Cuộc gọi đến cần Firebase Cloud Messaging + foreground service với
`foregroundServiceType="phoneCall"` (bắt buộc từ Android 14).

## 6. Kiểm thử

| Việc | Cách |
|---|---|
| Số điện thoại thử | Twilio Test Credentials + magic numbers (`+15005550006`) |
| SMS đến trên emulator | `adb emu sms send +84901234567 "Ma xac thuc la 123456 FA+9qCX9VSu"` |
| Mô phỏng backend | MockWebServer của OkHttp (dự án đã có OkHttp) |

## 7. Checklist bảo mật

- [ ] `ACCOUNT_SID` / `AUTH_TOKEN` **chỉ** ở backend
- [ ] Rate limit theo số điện thoại **và** theo IP ở backend
- [ ] OTP hết hạn (5 phút) và giới hạn số lần thử (3–5 lần)
- [ ] Không trả về mã OTP trong response API
- [ ] Không log số điện thoại và mã OTP (kể cả `Log.d`)
- [ ] Chặn số điện thoại từ vùng không phục vụ (chống SMS pumping)
- [ ] Dùng SMS Retriever thay vì quyền đọc SMS

## Xem thêm

- [Permissions](../02-android-core/permissions.md) — vì sao nên bỏ quyền SMS
- [BroadcastReceiver](../02-android-core/broadcast-receiver.md) — `SystemEventReceiver`
- [ForgeRock](forgerock.md) — nếu OTP là một bước trong Journey thì Twilio do server gọi
