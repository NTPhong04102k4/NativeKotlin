# Networking — Retrofit & OkHttp

Retrofit 2.11.0, Gson converter và OkHttp logging interceptor đã được thêm vào dự án. Chưa có API
nào được wire — trang này là khuôn mẫu để làm việc đó đúng ngay từ đầu.

## 1. Điều kiện tiên quyết

> ⚠️ **Trước khi gọi request đầu tiên, phải chuyển luồng login sang bất đồng bộ.**
>
> `LoginViewModel.login()` hiện gọi repository **đồng bộ** trên main thread. Nó chạy được vì
> `LoginDataSource` là stub trả về ngay. Nối Retrofit vào mà không sửa → **ANR ngay request đầu**.
>
> Xem [Coroutines §9](../06-kotlin/coroutines-flow.md#9-việc-cần-làm-cho-dự-án).

## 2. Cấu trúc đề xuất

```
data/
├── remote/
│   ├── ApiClient.kt            cấu hình Retrofit + OkHttp
│   ├── AuthApi.kt              interface endpoint
│   ├── DiscoveryApi.kt
│   ├── dto/                    model khớp JSON — KHÔNG dùng ngoài tầng data
│   │   ├── LoginRequestDto.kt
│   │   └── LoginResponseDto.kt
│   ├── interceptor/
│   │   ├── AuthInterceptor.kt
│   │   └── ErrorInterceptor.kt
│   └── mapper/                 DTO -> domain model
└── local/
    └── SessionManager.kt       token
```

## 3. ApiClient

```kotlin
// data/remote/ApiClient.kt
object ApiClient {

    private const val BASE_URL = "https://api.example.com/"
    private const val TIMEOUT_SECONDS = 30L

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        // CHỈ log ở bản debug — bản release sẽ in cả token vào logcat
        level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .addInterceptor(AuthInterceptor())
        .addInterceptor(loggingInterceptor)     // logging đặt CUỐI để thấy cả header đã thêm
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val authApi: AuthApi by lazy { retrofit.create(AuthApi::class.java) }
    val discoveryApi: DiscoveryApi by lazy { retrofit.create(DiscoveryApi::class.java) }
}
```

Ba chi tiết dễ sai:

1. **Thứ tự interceptor quan trọng.** OkHttp gọi theo thứ tự thêm vào. `loggingInterceptor` đặt
   **cuối** thì log mới thấy header `Authorization` mà `AuthInterceptor` vừa gắn.
2. **`BuildConfig.DEBUG` cho log.** `Level.BODY` ở bản release là lỗ hổng — nó in token, mật khẩu,
   dữ liệu cá nhân vào logcat. `buildConfig = true` đã được bật nên `BuildConfig.DEBUG` dùng được.
3. **`BASE_URL` nên qua `buildConfigField`**, không hardcode, để dev/staging/prod khác nhau:

```kotlin
// app/build.gradle.kts
buildTypes {
    debug   { buildConfigField("String", "BASE_URL", "\"https://api-dev.example.com/\"") }
    release { buildConfigField("String", "BASE_URL", "\"https://api.example.com/\"") }
}
```

## 4. Định nghĩa endpoint

```kotlin
// data/remote/AuthApi.kt
interface AuthApi {

    @POST("v1/auth/login")
    suspend fun login(@Body body: LoginRequestDto): LoginResponseDto

    @GET("v1/discovery/items")
    suspend fun getItems(
        @Query("page") page: Int,
        @Query("size") size: Int = 20,
        @Query("category") category: String? = null,   // null -> tham số bị bỏ khỏi URL
    ): PagedResponseDto<DiscoveryItemDto>

    @GET("v1/schedules/{id}")
    suspend fun getSchedule(@Path("id") id: String): ScheduleDto
}
```

Dùng `suspend fun` chứ không `Call<T>` — Retrofit hỗ trợ coroutine từ 2.6, và nó tự chạy trên
dispatcher I/O. **Không cần** bọc `withContext(Dispatchers.IO)` quanh lời gọi Retrofit.

## 5. DTO và mapper

DTO khớp JSON, domain model khớp nhu cầu nghiệp vụ — **hai thứ khác nhau**:

```kotlin
// data/remote/dto/LoginResponseDto.kt
data class LoginResponseDto(
    @SerializedName("access_token") val accessToken: String,
    @SerializedName("refresh_token") val refreshToken: String,
    @SerializedName("user") val user: UserDto,
)

data class UserDto(
    @SerializedName("id") val id: String,
    @SerializedName("display_name") val displayName: String?,
)

// data/remote/mapper/AuthMapper.kt
fun UserDto.toDomain() = LoggedInUser(
    userId = id,
    displayName = displayName ?: "",        // xử lý null ở đây, không đẩy lên UI
)
```

> ⚠️ **Gson không tôn trọng non-null của Kotlin.** Khai báo `val displayName: String` mà JSON trả
> `null` thì Gson vẫn gán null qua reflection → NPE ở chỗ dùng, rất khó truy. Nên khai báo DTO là
> nullable rồi xử lý trong mapper, hoặc đổi sang **kotlinx.serialization** / **Moshi** (cả hai tôn
> trọng nullability).

Với dự án mới thì `kotlinx-serialization` là lựa chọn tốt hơn Gson, nhưng nó cần plugin
serialization — mà dự án **không apply plugin Kotlin tường minh** (xem
[Jetpack README §5](../05-jetpack/README.md)). Moshi là đường trung gian: không cần plugin nếu dùng
reflection, hoặc dùng KSP nếu muốn codegen.

## 6. Xử lý lỗi

Chuyển exception của Retrofit thành `Result` của dự án **trong repository**:

```kotlin
class AuthRepository(private val api: AuthApi) {

    suspend fun login(username: String, password: String): Result<LoggedInUser> = try {
        val response = api.login(LoginRequestDto(username, password))
        SessionManager.save(response.accessToken, response.refreshToken)
        Result.Success(response.user.toDomain())
    } catch (e: HttpException) {
        Result.Error(e)                          // 4xx / 5xx
    } catch (e: IOException) {
        Result.Error(e)                          // mất mạng, timeout
    } catch (e: CancellationException) {
        throw e                                  // BẮT BUỘC ném lại
    }
}
```

Ánh xạ sang chuỗi hiển thị — các resource này đã có trong `strings.xml`:

```kotlin
@StringRes
fun Throwable.toMessageRes(): Int = when {
    this is IOException -> R.string.error_network
    this is HttpException && code() == 401 -> R.string.error_unauthorized
    this is HttpException && code() >= 500 -> R.string.error_server
    else -> R.string.error_unknown
}
```

| Resource | Nội dung |
|---|---|
| `error_network` | "Không có kết nối mạng. Vui lòng kiểm tra lại." |
| `error_server` | "Máy chủ đang gặp sự cố. Vui lòng thử lại sau." |
| `error_unauthorized` | "Phiên đăng nhập đã hết hạn." |
| `error_unknown` | "Đã xảy ra lỗi không xác định." |
| `action_retry` | "Thử lại" |

Đúng nguyên tắc của dự án: ViewModel giữ **`@StringRes Int`**, không giữ chuỗi — vì ViewModel không
có `Context`. Xem [MVVM §2.3](../01-kien-truc/mvvm.md).

## 7. AuthInterceptor

```kotlin
class AuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // Bỏ qua endpoint không cần token
        if (request.url.encodedPath.contains("/auth/login")) {
            return chain.proceed(request)
        }

        val token = SessionManager.accessToken ?: return chain.proceed(request)

        return chain.proceed(
            request.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        )
    }
}
```

Tự refresh khi 401 — dùng `Authenticator`, **không** dùng interceptor (tránh vòng lặp vô hạn):

```kotlin
class TokenAuthenticator : okhttp3.Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        // Đã thử một lần rồi -> đừng thử nữa, tránh vòng lặp
        if (response.request.header("Authorization") != null &&
            response.priorResponse != null
        ) return null

        val newToken = runBlocking { SessionManager.refresh() } ?: return null

        return response.request.newBuilder()
            .header("Authorization", "Bearer $newToken")
            .build()
    }
}
```

`runBlocking` ở đây là chấp nhận được: interceptor/authenticator đã chạy trên thread của OkHttp,
không phải main thread.

## 8. Bảo mật

### 8.1. Chỉ HTTPS

`targetSdk 36` nên cleartext đã bị chặn mặc định. **Đừng** bật lại:

```xml
<!-- ✗ Không làm thế này -->
<application android:usesCleartextTraffic="true">
```

Cần gọi HTTP trong lúc phát triển thì dùng `network_security_config.xml` giới hạn theo domain và
**chỉ ở build type debug**.

### 8.2. Certificate pinning

Với app xử lý dữ liệu nhạy cảm:

```kotlin
val pinner = CertificatePinner.Builder()
    .add("api.example.com", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
    .add("api.example.com", "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=")  // backup
    .build()
```

> ⚠️ **Luôn khai báo pin dự phòng.** Chứng chỉ hết hạn mà app chỉ pin một cái là **toàn bộ app
> ngừng hoạt động** và chỉ sửa được bằng bản cập nhật mới. Đây là sự cố có thật và rất tốn kém.

### 8.3. Không log ở release

Đã nói ở §3 nhưng đáng nhắc lại: `HttpLoggingInterceptor.Level.BODY` ở bản release in toàn bộ token
và dữ liệu người dùng vào logcat, mà bất kỳ app nào có quyền đọc log hoặc ai cắm USB đều xem được.

## 9. Kiểm thử

`MockWebServer` đi cùng OkHttp:

```kotlin
class AuthRepositoryTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After fun tearDown() = server.shutdown()

    @Test
    fun `login thanh cong tra ve user`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"access_token":"a","refresh_token":"r","user":{"id":"1","display_name":"Jane"}}""")
        )

        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AuthApi::class.java)

        val result = AuthRepository(api).login("jane@example.com", "password")

        assertTrue(result is Result.Success)
    }
}
```

Cần thêm vào catalog:

```toml
okhttp-mockwebserver = { group = "com.squareup.okhttp3", name = "mockwebserver", version.ref = "okhttp" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
```

## 10. Checklist

- [ ] Chuyển `LoginDataSource`/`Repository`/`ViewModel` sang `suspend` **trước** khi wire API
- [ ] `BASE_URL` qua `buildConfigField`, tách dev/prod
- [ ] `HttpLoggingInterceptor` chỉ `BODY` khi `BuildConfig.DEBUG`
- [ ] DTO tách khỏi domain model, có mapper
- [ ] DTO khai báo nullable (Gson không tôn trọng non-null)
- [ ] Exception bắt trong repository, trả `Result`
- [ ] `CancellationException` ném lại, không nuốt
- [ ] Không bật `usesCleartextTraffic`
- [ ] Certificate pinning có pin dự phòng (nếu dùng)
- [ ] Test bằng MockWebServer

## Xem thêm

- [Repository Pattern](../01-kien-truc/repository-pattern.md)
- [Coroutines & Flow](../06-kotlin/coroutines-flow.md)
- [ForgeRock](forgerock.md) — nếu auth do SDK lo thì interceptor lấy token từ đó
- [Paging & RecyclerView](../05-jetpack/paging-recyclerview.md) — phân trang cho API danh sách
