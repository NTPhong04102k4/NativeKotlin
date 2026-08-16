# Thiết kế: ForgeRock IAM + module `:forgerock-journey`

**Ngày:** 2026-08-12
**Trạng thái:** đã duyệt, chờ lập kế hoạch triển khai
**Sub-project:** A (đầu tiên trong 6)

---

## 1. Bối cảnh

Yêu cầu ban đầu gộp sáu hệ thống độc lập. Chúng được tách ra như sau, và tài liệu này
**chỉ mô tả sub-project A**:

| # | Sub-project | Phụ thuộc |
|---|---|---|
| **A** | **ForgeRock IAM + flavors + sinh trắc học** ← tài liệu này | — |
| B | Tích hợp API HRM (`hrapi.ttmedic.vn`) | A |
| C | Sentry + Firebase Crashlytics | — |
| D | Monetization: AdMob (IAA) + Play Billing (IAP) | A (flavors) |
| E | Phân phối: ký release, internal testing track | D |
| F | Dựng hạ tầng AM/IDM để test | **ngoài repo này** — việc devops, không phải Android |

Repo là sân tập chất lượng production: code phải đủ chuẩn để copy sang dự án thật, và
`docs/` là nửa mục đích của repo nên phải được cập nhật cùng code.

### Hiện trạng

- `LoginDataSource.login()` là stub, trả `LoggedInUser(UUID ngẫu nhiên, "Jane Doe")`.
- `LoginActivity` nhánh `btnBiometric` gọi thẳng `goToHome(LoggedInUserView("..."))` —
  **vân tay khớp là vào app, không tài khoản, không token, không gọi backend**. Đây là lỗ hổng
  thật, không phải thiếu sót thiết kế tương lai.
- `ApiClient.authInterceptor` đọc `SessionManager.token` (hiện luôn null).
- `BASE_URL` trỏ `jsonplaceholder.typicode.com`.
- Chỉ có 2 file test mẫu do IDE sinh; không có test thật.

### Tham chiếu: app HRM (`D:/Quizz_APP_HRM/app-hrm`)

App Flutter, dùng ForgeRock thật qua bridge Java `FRAuthSampleBridge.java` +
MethodChannel `forgerock.com/SampleBridge`. Là nguồn cho mọi giá trị cấu hình ở §8 và là
bằng chứng cho các quyết định ở §2.

---

## 2. Quyết định đã chốt

| Quyết định | Lý do |
|---|---|
| **Journey (authentication tree), không dùng OIDC/Custom Tabs** | Repo đã có `activity_login.xml` hoàn chỉnh với 3 layout variant. OIDC vứt bỏ toàn bộ UI đó. Thêm nữa, redirect URI của client tái sử dụng trỏ tới domain của app khác nên Custom Tab không quay về được app này. |
| **Tái sử dụng realm `development` + client `hrmAppClient` của HRM** | Không cần quản trị viên AM làm gì. `FRUser.login()` **không mở browser** — SDK bắt redirect ngay trong network layer — nên `oauthRedirectURI` chỉ cần khớp với client đã đăng ký, không cần App Links. Bằng chứng: HRM đang dùng `https://com.example.flutter.todo/callback` (giá trị leftover từ sample ForgeRock) và vẫn chạy. |
| **Không dùng WebAuthn/passkey** | `assetlinks.json` ở `id.ttmedic.vn` chỉ liệt kê `hrm.thaithinhmedic.vn`. Journey `webauth-android` sẽ fail với package của app này. |
| **Hướng 1 là façade mỏng trên Hướng 2, không phải implementation song song** | Chỉ một class duy nhất trong toàn codebase import `org.forgerock.*`. |
| **Tách Gradle module `:forgerock-journey`** | Phá quy ước single-module đang ghi trong CLAUDE.md — chấp nhận có ý thức, để tầng journey bọc được thành TurboModule cho React Native sau này. CLAUDE.md sẽ được cập nhật. |
| **Product flavors dev/staging/prod ngay trong sub-project này** | Đổi tên mọi task Gradle (`assembleDebug` → `assembleDevDebug`), làm sai toàn bộ lệnh trong CLAUDE.md và Makefile — chấp nhận trả giá một lần thay vì sửa build script lại ở sub-project E. |
| **Sinh trắc học là khoá mở phiên đã có, không phải một cách đăng nhập** | Lần đầu bắt buộc nhập mật khẩu. Không lưu mật khẩu ở đâu cả. |

---

## 3. Kiến trúc module

```
:app  ──depends──▶  :forgerock-journey  ──▶  org.forgerock:forgerock-auth:4.6.0
```

`:forgerock-journey` (namespace `com.ntp.forgerock.journey`, `com.android.library`, minSdk 24):

**Bất biến cứng — vi phạm là hỏng mục đích của module:**
- Không `Activity`, không `Fragment`, không `View`, không import `androidx.appcompat.*`.
- Chỉ được dùng `android.content.Context` (FR SDK bắt buộc), coroutines, Gson.
- Không kiểu dữ liệu nào ra khỏi module mà không serialize được sang JSON.
- Không đọc `BuildConfig` của `:app` — cấu hình được truyền vào qua `JourneyConfig`.

**Bề mặt export:**

| Thành phần | Vai trò | Ai dùng |
|---|---|---|
| `JourneyClient` | Động cơ: `start()` / `submit()` → `AuthStep` dữ liệu thuần | Hướng 2 — bề mặt RN |
| `JourneyJson` | `AuthStep` ⇄ JSON | Hướng 2 — bề mặt RN |
| `CredentialsAuthenticator` | Façade `suspend login(u, p)` | Hướng 1 — `:app` |
| `TokenProvider` | `currentAccessToken(): String?` (blocking) | `:app` — `ApiClient` |
| `JourneyConfig` | data class cấu hình | `:app` — `AppContainer` |

`:forgerock-journey` **không** chứa `BiometricPrompt`: nó cần `FragmentActivity`, nên
`BiometricHelper` ở lại `:app` và module chỉ export `currentUser()` + `TokenProvider`.

### Dependency của FR SDK

Theo `app/build.gradle` của HRM, `forgerock-auth` yêu cầu các dep sau được khai báo tường minh:

```
com.google.code.gson:gson:2.12.1
com.google.android.gms:play-services-location:21.3.0
net.openid:appauth:0.11.1
com.google.android.gms:play-services-fido:20.0.1
```

Tất cả khai báo trong `gradle/libs.versions.toml`, kể cả plugin `com.android.library` (hiện
`[plugins]` chỉ có `android-application`).

### 3.1. Toàn bộ thay đổi trong `:app`

| File | Thay đổi | Chi tiết |
|---|---|---|
| `data/LoginDataSource.kt` | tách thành interface `AuthDataSource` + `ForgeRockAuthDataSource` + `FakeAuthDataSource` | §5 |
| `data/LoginRepository.kt` | `logout()` gọi thêm `JourneyClient.logout()` | §5 |
| `data/local/SessionManager.kt` | thêm `lastUsername`/`biometricEnabled`/`encryptedMarker`/`iv`; xoá `token`/`saveToken` ở **bước sau** | §6.2, §9 |
| `data/remote/ApiClient.kt` | nguồn token → `TokenProvider`; thêm `Authenticator` bắt 401; `BASE_URL` → `BuildConfig.API_BASE_URL` | §8, §9 |
| `data/AppException.kt` | thêm `Kind.INVALID_CREDENTIALS`, `Kind.AUTH_CONFIG` | §10 |
| `ui/common/ErrorMessages.kt` | `when` exhaustive — compiler ép cập nhật | §10 |
| `di/AppContainer.kt` | dựng `JourneyConfig` từ `BuildConfig`, khởi tạo `JourneyClient`, chọn `AuthDataSource` | §8 |
| `ui/login/LoginActivity.kt` | **sửa nhánh `btnBiometric` đang bỏ qua xác thực**; ẩn/hiện nút theo phiên đã lưu; chạy `BiometricPrompt` với `CryptoObject` | §6 |
| `ui/login/LoginViewModel.kt` | thêm `resumeSession()`, kết quả đi ra qua `loginResult` sẵn có | §6.5 |
| `ui/settings/SettingsActivity.kt` | `ReauthGate` ở `onResume` | §7 |
| `ui/personal/PersonalActivity.kt` | `ReauthGate` trước khi `btnLogout` gọi `AppRouter.logout` | §7 |
| `ui/discovery`, `ui/schedule`, `ui/personal` Activity | một dòng observe `SessionExpiredNotifier` | §9 |
| `util/BiometricHelper.kt` | đổi chữ ký để nhận `CryptoObject` | §6.1 |
| `util/ReauthGate.kt` | **mới** | §7 |
| `res/layout*/activity_login.xml` (cả 3 variant) | nhãn "Đăng nhập với `<username>`" | §6.4 |
| `res/values/strings.xml` | chuỗi lỗi + chuỗi biometric mới (tiếng Việt) | §10 |
| `app/build.gradle.kts` | flavors + `buildConfigField` + `implementation(project(":forgerock-journey"))` | §8 |

`LoginFormState`, `LoginResult`, `LoggedInUserView`, `AppRouter`, `SessionRepository`,
`DiscoveryRepository`, `ScheduleRepository`, `safeApiCall`, `Result` — **không đổi**.

---

## 4. Mô hình dữ liệu journey

```kotlin
sealed interface AuthStep {
    data class Node(
        val stage: String?,
        val header: String?,
        val callbacks: List<AuthCallback>,
    ) : AuthStep

    data class Success(val user: JourneyUser) : AuthStep
    data class Failure(val kind: JourneyError, val message: String?) : AuthStep
}

sealed interface AuthCallback {
    val id: Int        // vị trí trong node — khoá khi submit
    val type: String   // "NameCallback", "PasswordCallback", ...

    data class Text(override val id: Int, override val type: String,
                    val prompt: String?, val value: String?) : AuthCallback

    /** KHÔNG có field value — mật khẩu chỉ đi một chiều vào SDK. */
    data class Secret(override val id: Int, override val type: String,
                      val prompt: String?) : AuthCallback

    data class Choice(override val id: Int, override val type: String,
                      val prompt: String?, val choices: List<String>,
                      val defaultIndex: Int) : AuthCallback

    data class Confirm(override val id: Int, override val type: String,
                       val prompt: String?, val options: List<String>) : AuthCallback

    data class Unsupported(override val id: Int, override val type: String,
                           val raw: String) : AuthCallback
}

data class JourneyUser(val id: String, val displayName: String, val username: String)

enum class JourneyError {
    NETWORK, INVALID_CREDENTIALS, JOURNEY_NOT_FOUND,
    TIMEOUT, UNSUPPORTED_CALLBACK, UNKNOWN,
}
```

**`Secret` cố tình không mang giá trị.** JSON này sẽ đi qua bridge sang JavaScript, nơi nó
lọt vào mọi log và mọi crash report của Sentry ở sub-project C. `JourneyJson.encode` phải
có test khẳng định điều này, không chỉ dựa vào việc data class thiếu field.

**Khoá `submit` theo `id` (vị trí trong node), không theo tên callback**, vì một node có thể
chứa hai callback cùng type (ví dụ đặt mật khẩu mới + xác nhận).

---

## 5. `JourneyClient` và façade

```kotlin
interface JourneyClient {
    suspend fun start(journeyName: String? = null): AuthStep
    suspend fun submit(answers: Map<Int, String>): AuthStep
    suspend fun currentUser(): JourneyUser?
    suspend fun logout()
}
```

`FrJourneyClient` là **class duy nhất** import `org.forgerock.*`. Nó giữ `currentNode` (đúng
cách `FRAuthSampleBridge` của HRM làm) và bọc `NodeListener` callback-based thành `suspend`.

**Hai điểm dễ sai:**

1. Journey là **stateful**. Hai lời gọi `submit` chồng nhau làm hỏng phiên. Bảo vệ bằng
   `Mutex`, không phải cờ boolean.
2. Mỗi `suspendCancellableCoroutine` phải resume **đúng một lần**. `NodeListener` có ba đường
   ra (`onCallbackReceived`, `onSuccess`, `onException`) và `onCallbackReceived` **không** phải
   sự kiện kết thúc. Resume hai lần là `IllegalStateException`.

```kotlin
class CredentialsAuthenticator(private val client: JourneyClient) {
    suspend fun login(username: String, password: String): AuthStep
}
```

Vòng lặp: `start()` → điền `NameCallback`/`PasswordCallback` từ tham số → `submit()` → lặp,
**giới hạn cứng 10 vòng** để journey cấu hình sai không treo app. Gặp callback ngoài
`Text`/`Secret`/`Confirm` → `Failure(UNSUPPORTED_CALLBACK)`.

Đây là đường thoát sang Hướng 2: khi realm bật MFA, `:app` bỏ façade và gọi thẳng
`JourneyClient` — repository không phải viết lại.

---

## 6. Sinh trắc học gắn tài khoản

### 6.1. Đăng ký

Sau lần đăng nhập bằng mật khẩu **thành công đầu tiên**, nếu
`BiometricManager.canAuthenticate(BIOMETRIC_STRONG) == BIOMETRIC_SUCCESS` và chưa bật, hỏi
người dùng. Đồng ý → tạo khoá AES trong Android Keystore:

```kotlin
setUserAuthenticationRequired(true)          // Keystore từ chối khoá nếu chưa xác thực
setInvalidatedByBiometricEnrollment(true)    // thêm vân tay mới ⇒ khoá chết
```

Thuộc tính thứ hai chặn kịch bản tấn công thật: mượn được máy đang mở khoá, thêm vân tay của
mình, mở app. Có nó thì khoá bị vô hiệu và `KeyPermanentlyInvalidatedException` được ném ra.

Khoá dùng để mã hoá một marker chứa `username`. Nó không bảo vệ bí mật nào — token nằm trong
secure storage của FR SDK — mà để **chứng minh bằng mật mã rằng sinh trắc học thực sự thoả
mãn**. `onSuccess` dạng boolean thuần có thể bị hook; `cipher.doFinal()` thành công thì không.

### 6.2. `SessionManager` lưu gì

`lastUsername`, `biometricEnabled`, `encryptedMarker`, `iv`. **Không token, không mật khẩu.**

Vì vậy SharedPreferences thường là đủ. **Không** thêm `androidx.security:security-crypto` như
comment hiện tại trong file gợi ý: thư viện đó đã bị Google deprecate, và ở đây không còn bí
mật nào cho nó bảo vệ.

### 6.3. Luồng mở khoá

```
BiometricPrompt(CryptoObject(cipher))
├─ success → cipher.doFinal → username khớp?
│   └─ JourneyClient.currentUser() != null → TokenProvider.currentAccessToken()
│       ├─ có token (FR tự refresh) → AppRouter.openHomeAfterLogin
│       └─ null → xoá cờ bio, điền sẵn username, báo "phiên đã hết hạn"
├─ KeyPermanentlyInvalidatedException → xoá khoá + cờ, buộc nhập mật khẩu, nêu rõ lý do
└─ huỷ / lockout → ở lại màn login, không báo lỗi
```

Nút biometric **chỉ hiện khi** `lastUsername != null && biometricEnabled &&
canAuthenticate(BIOMETRIC_STRONG) == SUCCESS`, và hiện kèm tên tài khoản
("Đăng nhập với `<username>`"). Không thoả → **ẩn** nút, không disable.

**Giới hạn thật, phải ghi vào docs:** sinh trắc học sống đúng bằng tuổi thọ refresh token do
AM cấu hình. Hết hạn là phải nhập mật khẩu lại — không có cách lách, vì ta cố tình không lưu
mật khẩu.

### 6.4. Bẫy ViewBinding

`binding.btnBiometric` hiện là **nullable** vì không có mặt trong mọi layout variant. Nhãn
"Đăng nhập với `<username>`" phải có ở **cả ba** `activity_login.xml` (`layout/`,
`layout-w936dp/`, `layout-w1240dp/`) hoặc chấp nhận nullable. Root element của cả ba variant
phải tiếp tục thống nhất ID — bất đồng là lỗi build cứng ở `dataBindingGenBaseClassesDebug`.

### 6.5. MVVM giữ nguyên

`BiometricPrompt` cần Activity nên nó chạy ở `LoginActivity`. Mở khoá xong, Activity gọi
`loginViewModel.resumeSession()`; kết quả đi ra qua đúng `loginResult` LiveData sẵn có. Không
thêm observer thứ hai, không thêm màn hình.

---

## 7. Cổng xác thực lại

`ReauthGate` trong `:app/util`, gọi từ Activity đích — **không** đặt trong `AppRouter`, giữ
bất biến "router chỉ điều hướng, không biết gì về auth".

Áp dụng cho **hai chỗ**:

1. **`SettingsActivity.onResume`** — không phải tab, mở bằng push thường qua
   `AppRouter.openSettings`, nên không va chạm với `FLAG_ACTIVITY_REORDER_TO_FRONT`.
2. **Hành động đăng xuất** trong `PersonalActivity` — xác thực lại trước khi `btnLogout` thực
   sự gọi `AppRouter.logout`, chống người khác cầm máy đá chủ máy ra khỏi phiên.

`PersonalActivity` **không** bị chặn ở mức màn hình (nó là tab; `onResume` chạy mỗi lần đổi
tab).

Grace period 5 phút kể từ lần xác thực gần nhất, giữ **in-memory** — process death là phải
xác thực lại.

---

## 8. Cấu hình và flavors

Dimension `env`, ba flavor. `applicationIdSuffix`: `.dev` / `.staging` / (không có).

| buildConfigField | dev | staging | prod |
|---|---|---|---|
| `AM_URL` | `https://id.ttmedic.vn/am` | ← | ← |
| `AM_REALM` | `development` | `staging` | `ttmedic` |
| `AM_COOKIE_NAME` | `iPlanetDirectoryPro` | ← | ← |
| `OAUTH_CLIENT_ID` | `hrmAppClient` | `hr-app` | `hrmAppClient` |
| `OAUTH_REDIRECT_URI` | `https://com.example.flutter.todo/callback` | ← | ← |
| `OAUTH_SCOPES` | `openid profile email address` | ← | ← |
| `AUTH_JOURNEY` | `Login` ⚠️ | `Login` ⚠️ | `Login` ⚠️ |
| `API_BASE_URL` | `https://dev.hrapi.ttmedic.vn/hr/api/v1` | `https://staging.hrapi.ttmedic.vn/hr/api/v1` | `https://hrapi.ttmedic.vn/hr/api/v1` |

Mọi giá trị lấy từ `FRAuthSampleBridge.java` và `lib/flavors/*.dart` của HRM. Chúng là giá trị
**thật của tenant ttmedic**, không phải placeholder — nhưng chỉ flavor `dev` là test được
trong sub-project này.

⚠️ **`AUTH_JOURNEY` là giả định chưa kiểm chứng.** Realm `development` mới chỉ biết chắc có
`webauth-android` (dùng WebAuthn — không dùng được) và `Registration`. `Login` là tên journey
mặc định của AM. **Nhiệm vụ đầu tiên của kế hoạch triển khai là xác minh nó**, trước khi viết
bất kỳ dòng Kotlin nào:

```
POST https://id.ttmedic.vn/am/json/realms/root/realms/development/authenticate
     ?authIndexType=service&authIndexValue=Login
Header: Accept-API-Version: resource=2.0, protocol=1.0
```

Trả về JSON có `authId` + `callbacks` ⇒ journey tồn tại. Trả về lỗi ⇒ hỏi quản trị viên AM tên
journey username/password của realm, sửa đúng một dòng `buildConfigField`. Vì tên journey là
cấu hình chứ không phải code, sai tên **không** kéo theo sửa code.

`API_BASE_URL` được đưa vào ngay ở sub-project này để `ApiClient.BASE_URL` hết hardcode, nhưng
các endpoint HRM thật thuộc sub-project B — `ApiService` chưa đổi ở đây.

**Hệ quả phải xử lý cùng lúc:** mọi lệnh Gradle đổi tên (`assembleDebug` → `assembleDevDebug`,
`installDebug` → `installDevDebug`, `testDebugUnitTest` → `testDevDebugUnitTest`). Phải cập
nhật `CLAUDE.md`, `AGENTS.md` và `Makefile` trong cùng change, nếu không mọi lệnh được tài
liệu hoá đều sai.

---

## 9. Token và phiên hết hạn

`TokenProvider` hiện thực bằng `FRUser.getCurrentUser()?.accessToken` — bản đồng bộ, chạy trên
thread của OkHttp nên hợp lệ; FR SDK tự refresh khi sắp hết hạn. Ném
`AuthenticationRequiredException` khi không còn phiên ⇒ bắt và trả `null`.

`ApiClient.authInterceptor` chuyển từ `SessionManager.token` sang `TokenProvider`. Vì
`SessionManager` ở mức risk **MEDIUM** (§13), việc này tách làm **hai bước không trùng nhau**:

1. `ApiClient` đổi nguồn token.
2. *Sau đó* mới xoá `SessionManager.token` và `SessionManager.saveToken`.

**Phiên chết giữa chừng.** Khi refresh token hết hạn, API trả 401 thật. Hiện `safeApiCall`
chỉ dịch thành `UNAUTHORIZED` rồi hiện toast — người dùng kẹt ở màn hình trống, không ai đá về
login. Thêm `SessionExpiredNotifier` (`StateFlow<Boolean>` trong `:app`), bật lên từ OkHttp
`Authenticator` khi gặp 401. Ba tab Activity observe qua một extension một dòng và gọi
`AppRouter.logout(this)`.

---

## 10. Lỗi

`JourneyError` (module) → `AppException.Kind` (`:app`) → `R.string` qua `ErrorMessages
.toMessageRes()` — đúng đường đã có. Thêm hai `Kind`:

- **`INVALID_CREDENTIALS`** — sai tài khoản/mật khẩu lúc đăng nhập. **Khác** `UNAUTHORIZED`
  (token hết hạn giữa phiên). Gộp hai cái là làm người dùng đọc sai thông báo.
- **`AUTH_CONFIG`** — journey không tồn tại, callback không hỗ trợ. Map vào `SERVER` thì lỗi
  cấu hình sẽ đội lốt "server lỗi" suốt quá trình dev.

`when` trong `toMessageRes()` là exhaustive nên compiler ép cập nhật — lưới an toàn, không
phải phiền toái. Chuỗi tiếng Việt mới trong `strings.xml` (lint `HardcodedText` ở mức error).

---

## 11. Test

Đây là test thật đầu tiên của repo. **Không** thêm mock framework — fake thủ công, hợp với
repo không có annotation processor. Chỉ thêm `kotlinx-coroutines-test`.

| Test | Kiểm cái gì |
|---|---|
| `JourneyJsonTest` | round-trip; **khẳng định `Secret` không bao giờ xuất hiện value trong JSON** |
| `CredentialsAuthenticatorTest` | happy path · sai mật khẩu · callback lạ → `UNSUPPORTED_CALLBACK` · journey lặp → dừng ở giới hạn 10 vòng |
| `FrJourneyClientTest` | hai `submit` song song không xen nhau (Mutex) |
| `LoginViewModelTest` | qua `FakeAuthDataSource` |

**Không unit test được** — verify thủ công trên thiết bị, checklist trong kế hoạch triển khai:
`FrJourneyClient` chạm AM thật, Keystore, `BiometricPrompt`, và
`KeyPermanentlyInvalidatedException` (phải thêm vân tay mới trên máy để tái hiện).

---

## 12. Docs phải cập nhật

`docs/` là nửa mục đích của repo, không phải phụ lục.

- `docs/07-tich-hop/forgerock.md` — đang ghi "chưa tích hợp" và version 4.5.0.
- `docs/07-tich-hop/biometric.md` — thêm phần gắn tài khoản, `CryptoObject`,
  `setInvalidatedByBiometricEnrollment`.
- `docs/07-tich-hop/native-bridge.md` — đang ghi "không có bridge nào"; nay có tầng dữ liệu
  sẵn sàng bọc TurboModule.
- `CLAUDE.md` + `AGENTS.md` — hai module thay vì một; lệnh Gradle đổi tên vì flavors; nguồn
  token; `LoginDataSource` hết là stub.
- `Makefile` — lệnh đổi tên.

---

## 13. Impact analysis

Chạy bằng GitNexus trước khi sửa, theo yêu cầu của CLAUDE.md.

| Symbol | Risk | Caller trực tiếp |
|---|---|---|
| `LoginDataSource` | LOW | `AppContainer.kt`, `LoginViewModelFactory.kt` |
| `SessionManager` | **MEDIUM** ⚠️ | `AiAssistantApp.kt`, `SessionRepository.kt`, `LoginRepository.kt`, `ApiClient.kt` |

`SessionManager` là chỗ rủi ro nhất của spec vì token đổi chủ. Giảm thiểu: giữ nguyên bề mặt
API, chỉ xoá `token`/`saveToken` **sau khi** `ApiClient` đã chuyển nguồn (§9).

Chạy `impact` trên `BiometricHelper`, `ApiClient`, `LoginActivity` ngay trước khi sửa từng cái,
và `detect_changes()` trước khi commit.

### Rủi ro còn lại

| Rủi ro | Giảm thiểu |
|---|---|
| Tên journey sai | Xác minh bằng probe HTTP **trước khi viết code** (§8). Là cấu hình, sửa một dòng. |
| Realm `development` bật MFA/ToS | Façade trả `UNSUPPORTED_CALLBACK` với thông báo rõ; đường thoát sang `JourneyClient` đã dựng sẵn. |
| Quản trị viên đổi/xoá client `hrmAppClient` | Ngoài tầm kiểm soát. Là cấu hình per-flavor nên sửa nhanh. |
| Flavors làm hỏng lệnh build đã tài liệu hoá | Cập nhật `CLAUDE.md`/`AGENTS.md`/`Makefile` trong **cùng** change. |
| FR SDK kéo theo `play-services-*` làm phình APK | Chấp nhận; đo lại khi tới sub-project E. |

---

## 14. Ngoài phạm vi

- Endpoint HRM thật, DTO, map vào Discovery/Schedule/Personal → **B**.
- Sentry, Crashlytics → **C**.
- AdMob, Play Billing → **D**.
- Ký release, internal testing track → **E**.
- Dựng AM/IDM để test → **F**, không thuộc repo này.
- Bọc TurboModule React Native — tầng dữ liệu được thiết kế sẵn sàng, nhưng **không** viết
  module RN nào trong sub-project này (chưa có project RN để chạy thử, code không verify được).
- Đăng ký tài khoản, quên mật khẩu, đổi mật khẩu (HRM có `Registration` journey và
  `callChangePassword`) — chưa có yêu cầu.
- Google Sign-In (`btnGoogle` vẫn chỉ hiện Toast), `GoogleAuthHelper` vẫn không được dùng.
