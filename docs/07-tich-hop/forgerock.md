# ForgeRock — đăng nhập doanh nghiệp

ForgeRock (nay thuộc Ping Identity) là nền tảng quản lý danh tính. SDK Android của nó lo trọn gói
xác thực, quản lý token, refresh, và các luồng nhiều bước (MFA, OTP, đổi mật khẩu bắt buộc).

> **Trạng thái:** dự án **chưa tích hợp** ForgeRock. Luồng đăng nhập hiện tại là stub
> (`LoginDataSource` trả về "Jane Doe" với UUID ngẫu nhiên). Trang này mô tả cách thay thế.

## 1. Hai kiểu tích hợp

| | **Journey / Authentication Tree** | **OIDC / OAuth 2.0** |
|---|---|---|
| Giao diện | App tự vẽ, SDK trả về callback | Custom Tabs, ForgeRock tự vẽ |
| Kiểm soát UI | Toàn quyền | Hạn chế (tuỳ chỉnh trên server) |
| MFA / bước phụ | Xử lý trong app | Xử lý trong trình duyệt |
| Đúng chuẩn RFC 8252 | — | **Có** |
| Phù hợp khi | Cần UI thương hiệu riêng | Cần SSO với app khác |

Dự án đã có layout đăng nhập hoàn chỉnh (`activity_login.xml` với `til_username`, `til_password`,
`btn_google`, `btn_biometric`) → **Journey** giữ được giao diện đó. Nếu đổi sang OIDC, layout này
gần như bỏ đi.

## 2. Cài đặt

```toml
# gradle/libs.versions.toml
[versions]
forgerock = "4.5.0"

[libraries]
forgerock-auth = { group = "org.forgerock", name = "forgerock-auth", version.ref = "forgerock" }
```

```kotlin
implementation(libs.forgerock.auth)
```

Cấu hình qua `res/values/strings.xml` hoặc file riêng:

```xml
<resources>
    <string name="forgerock_url">https://openam.example.com/openam</string>
    <string name="forgerock_realm">alpha</string>
    <string name="forgerock_cookie_name">iPlanetDirectoryPro</string>
    <string name="forgerock_auth_service">Login</string>
    <string name="forgerock_oauth_client_id">android-client</string>
    <string name="forgerock_oauth_redirect_uri">org.forgerock.demo://oauth2redirect</string>
    <string name="forgerock_oauth_scope">openid profile email offline_access</string>
</resources>
```

> ⚠️ Những giá trị này **không phải bí mật** nhưng cũng không nên hardcode cho mọi môi trường.
> Dùng `buildConfigField` hoặc product flavor để tách dev/staging/prod. `buildConfig = true` đã
> được bật sẵn.

Khởi tạo — cần một lớp `Application` (dự án **chưa có**, xem [Hilt & DI](../05-jetpack/hilt-di.md)):

```kotlin
class AiAssistantApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FRAuth.start(this)
    }
}
```

## 3. Luồng Journey

Journey là một cây các node trên server. SDK trả về từng `Node` chứa danh sách `Callback` mà app
phải điền và gửi lại.

```kotlin
FRSession.authenticate(context, "Login", object : NodeListener<FRSession> {

    override fun onCallbackReceived(node: Node) {
        node.callbacks.forEach { callback ->
            when (callback) {
                is NameCallback     -> callback.setName(username)
                is PasswordCallback -> callback.setPassword(password.toCharArray())

                // MFA: mã OTP
                is TextOutputCallback -> showMessage(callback.message)
                is ChoiceCallback     -> callback.setSelectedIndex(0)

                else -> { /* callback chưa hỗ trợ */ }
            }
        }
        node.next(context, this)      // gửi lên server, có thể trả về node tiếp theo
    }

    override fun onSuccess(result: FRSession) {
        // Đăng nhập xong — token đã được SDK lưu an toàn
    }

    override fun onException(e: Exception) {
        // Sai mật khẩu, mạng lỗi, journey bị từ chối...
    }
})
```

Điểm mấu chốt: `onCallbackReceived` có thể được gọi **nhiều lần** — một lần cho username/password,
một lần nữa cho OTP, một lần nữa cho câu hỏi bảo mật... App phải hiển thị UI tương ứng cho từng
bước chứ không giả định chỉ có một màn hình.

## 4. Ánh xạ vào kiến trúc hiện tại

ForgeRock nên nằm **sau** `LoginDataSource` — ViewModel không được biết ForgeRock tồn tại.

```
LoginActivity  ──►  LoginViewModel  ──►  LoginRepository  ──►  LoginDataSource
                                                                    │
                                                          ForgeRockAuthenticator
                                                                    │
                                                             FRSession / FRUser
```

### 4.1. Bọc callback thành `suspend`

SDK dùng callback; dự án đang chuyển sang coroutine. Dùng `suspendCancellableCoroutine`:

```kotlin
// data/remote/ForgeRockAuthenticator.kt
class ForgeRockAuthenticator(private val context: Context) {

    suspend fun login(username: String, password: String): Result<FRUser> =
        suspendCancellableCoroutine { cont ->
            FRSession.authenticate(context, "Login", object : NodeListener<FRSession> {

                override fun onCallbackReceived(node: Node) {
                    node.callbacks.forEach {
                        when (it) {
                            is NameCallback     -> it.setName(username)
                            is PasswordCallback -> it.setPassword(password.toCharArray())
                            else -> {
                                // Journey yêu cầu bước mà API đơn giản này không xử lý được
                                cont.resume(Result.Error(UnsupportedOperationException("Cần MFA")))
                                return
                            }
                        }
                    }
                    node.next(context, this)
                }

                override fun onSuccess(result: FRSession) {
                    val user = FRUser.getCurrentUser()
                    cont.resume(
                        if (user != null) Result.Success(user)
                        else Result.Error(IllegalStateException("Không lấy được người dùng"))
                    )
                }

                override fun onException(e: Exception) {
                    cont.resume(Result.Error(e))
                }
            })
        }
}
```

`Result` ở đây là `data/Result.kt` của dự án — nhớ import tường minh vì nó che `kotlin.Result`.

### 4.2. Thay `LoginDataSource`

```kotlin
class LoginDataSource(private val authenticator: ForgeRockAuthenticator) {

    suspend fun login(username: String, password: String): Result<LoggedInUser> =
        when (val result = authenticator.login(username, password)) {
            is Result.Success -> Result.Success(
                LoggedInUser(
                    userId = result.data.userInfo?.sub ?: "",
                    displayName = result.data.userInfo?.name ?: username,
                )
            )
            is Result.Error -> result
        }

    suspend fun logout() {
        FRUser.getCurrentUser()?.logout()
    }
}
```

Chú ý hàm giờ là `suspend` — kéo theo `LoginRepository.login()` và `LoginViewModel.login()` cũng
phải đổi. Đây chính là việc đã ghi trong
[Coroutines §9](../06-kotlin/coroutines-flow.md#9-việc-cần-làm-cho-dự-án) và là lý do phải làm
trước khi nối bất kỳ backend nào.

## 5. Token — SDK tự lo

Đây là lợi ích lớn nhất so với tự viết:

```kotlin
FRUser.getCurrentUser()?.getAccessToken(object : FRListener<AccessToken> {
    override fun onSuccess(result: AccessToken) {
        // SDK tự refresh nếu token sắp hết hạn
        val header = "Bearer ${result.value}"
    }
    override fun onException(e: Exception) { /* refresh token cũng hết hạn -> bắt đăng nhập lại */ }
})
```

SDK lưu token trong Android Keystore, tự refresh, tự xử lý hết hạn. Không cần
`EncryptedSharedPreferences` thủ công như mô tả ở
[Room & DataStore §4](../05-jetpack/room-datastore.md#4-lưu-token-đăng-nhập--không-dùng-cách-thường).

### Interceptor cho Retrofit

Dự án vừa thêm Retrofit + OkHttp. Gắn token tự động:

```kotlin
class ForgeRockAuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { currentAccessToken() }     // trong interceptor được phép block
            ?: return chain.proceed(chain.request())

        return chain.proceed(
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        )
    }
}
```

Kèm `Authenticator` để tự đăng nhập lại khi gặp 401:

```kotlin
class ForgeRockTokenAuthenticator : okhttp3.Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.header("Authorization") != null) return null   // đã thử rồi -> bỏ
        val fresh = runBlocking { refreshToken() } ?: return null
        return response.request.newBuilder()
            .header("Authorization", "Bearer $fresh")
            .build()
    }
}
```

Chuỗi `error_unauthorized` ("Phiên đăng nhập đã hết hạn") đã có sẵn trong `strings.xml` cho trường
hợp refresh cũng thất bại.

## 6. Biometric + ForgeRock

ForgeRock hỗ trợ callback sinh trắc học ngay trong Journey:

```kotlin
is DeviceBindingCallback -> callback.bind(context, listener)
is DeviceSigningVerifierCallback -> callback.sign(context, listener)
```

Đây là cách **đúng** để nối `BiometricHelper` vào một phiên đăng nhập thật — giải quyết đúng vấn đề
đã nêu ở [Biometric §2.4](biometric.md#24-️-nghiêm-trọng-nhất-xác-thực-xong-không-có-phiên-đăng-nhập-thật):
vân tay không tạo ra danh tính, nó mở khoá khoá ký thiết bị đã đăng ký với ForgeRock.

Luồng đầy đủ:

```
Lần đầu:  username/password  ──►  ForgeRock  ──►  DeviceBindingCallback
                                                  tạo cặp khoá trong Keystore,
                                                  khoá riêng bảo vệ bằng sinh trắc học
Lần sau:  vân tay  ──►  mở khoá khoá riêng  ──►  ký challenge  ──►  ForgeRock cấp token
```

## 7. Social login (Google)

`util/GoogleAuthHelper` hiện tồn tại nhưng **không được gọi ở đâu** — nút Google chỉ hiện Toast,
và `requestIdToken` đang bị comment vì thiếu web client ID.

Với ForgeRock, không nên tự làm Google Sign-In rồi tự gửi token lên backend. Thay vào đó dùng
`IdPCallback` của ForgeRock — server điều phối, app chỉ thực thi:

```kotlin
is IdPCallback -> callback.signIn(null, object : FRListener<Void> {
    override fun onSuccess(result: Void?) { node.next(context, nodeListener) }
    override fun onException(e: Exception) { /* ... */ }
})
```

Như vậy `GoogleAuthHelper` và dependency `play-services-auth` có thể **bỏ hẳn**.

## 8. Đăng xuất

```kotlin
FRUser.getCurrentUser()?.logout()      // xoá token cục bộ + huỷ phiên trên server
```

Phải gọi **trước** khi điều hướng. `AppRouter.logout()` hiện đã dọn `SessionManager` và cache
repository — thêm bước huỷ phiên ForgeRock vào cùng chỗ đó:

```kotlin
fun logout(from: Activity) {
    SessionManager.clear()
    from.appContainer.clearUserData()
    // + FRUser.getCurrentUser()?.logout()
    ...
}
```

Chỉ xoá cục bộ mà không huỷ phiên server thì refresh token vẫn còn hiệu lực — rủi ro bảo mật thật.

## 9. Việc cần chuẩn bị trước khi tích hợp

| Việc | Vì sao trước |
|---|---|
| Chuyển `LoginDataSource`/`Repository`/`ViewModel` sang `suspend` | SDK bất đồng bộ, không thể gọi từ code đồng bộ |
| Tạo lớp `Application` | `FRAuth.start()` phải chạy lúc khởi động |
| Tách config theo môi trường | URL dev/prod khác nhau |
| Thống nhất Journey hay OIDC | Quyết định này đổi cả layout đăng nhập |
| Lấy tên Journey + client ID từ đội identity | Không đoán được |

## 10. Gỡ lỗi thường gặp

| Triệu chứng | Nguyên nhân |
|---|---|
| `Invalid redirect_uri` | `redirect_uri` trong app khác cấu hình trên server |
| Journey dừng ở node lạ | Có callback chưa xử lý — log toàn bộ `node.callbacks` |
| Token hết hạn ngay | Sai `realm` hoặc `cookieName` |
| `disallowed_useragent` khi social login | Đang dùng WebView thay vì Custom Tabs — xem [WebView §1](webview.md) |
| Đăng nhập được nhưng gọi API 401 | Chưa gắn interceptor, hoặc sai scope |

## Xem thêm

- [Biometric](biometric.md)
- [WebView](webview.md) — Custom Tabs cho OIDC
- [Repository Pattern](../01-kien-truc/repository-pattern.md)
- [Coroutines & Flow](../06-kotlin/coroutines-flow.md)
