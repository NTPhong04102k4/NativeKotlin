# TIO SDK — khung tài liệu (cần bổ sung chi tiết)

> ⚠️ **Trang này chưa hoàn chỉnh.** "TIO" được xác định là SDK/nền tảng nội bộ đang được dùng, nhưng
> tôi chưa có tài liệu hay code của nó nên **không thể viết chi tiết kỹ thuật mà không bịa**.
>
> Bên dưới là khung tài liệu theo đúng chuẩn của các trang tích hợp khác trong `docs/07-tich-hop/`,
> kèm danh sách cụ thể những thông tin cần điền. Cung cấp một trong các thứ sau là tôi hoàn thiện
> được ngay:
>
> - Toạ độ Maven / đường dẫn file `.aar` của SDK
> - Link tài liệu nội bộ, hoặc file README của SDK
> - Một đoạn code đang gọi TIO (dù chỉ vài dòng)
> - Tên package gốc để tôi tự đọc lớp public trong `.aar`

## 1. TIO là gì

*(Cần điền)*

- Tên đầy đủ:
- Do đội nào/nhà cung cấp nào phát triển:
- Giải quyết vấn đề gì:
- Có SDK Android riêng, hay chỉ là REST API:
- Có bản iOS tương ứng không (liên quan tới định hướng đa nền tảng):

## 2. Cài đặt

*(Cần điền)*

Nếu là dependency Maven, theo quy ước của dự án **phải** đi qua version catalog:

```toml
# gradle/libs.versions.toml
[versions]
tio = "?"

[libraries]
tio-sdk = { group = "?", name = "?", version.ref = "tio" }
```

```kotlin
// app/build.gradle.kts
implementation(libs.tio.sdk)
```

Nếu là `.aar` cục bộ:

```kotlin
dependencies {
    implementation(files("libs/tio-sdk.aar"))
}
```

Nếu ở Maven repository nội bộ, khai báo trong `settings.gradle.kts` — chú ý dự án đang dùng
`RepositoriesMode.FAIL_ON_PROJECT_REPOS`, nên repository **phải** khai báo ở
`dependencyResolutionManagement`, không phải trong `app/build.gradle.kts`:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://nexus.noi-bo.example.com/repository/maven-releases/")
            credentials {
                username = providers.gradleProperty("tioRepoUser").get()
                password = providers.gradleProperty("tioRepoPassword").get()
            }
        }
    }
}
```

Credential để trong `local.properties` / `~/.gradle/gradle.properties`, **không commit**.

> ⚠️ Configuration cache đang bật. Dùng `providers.gradleProperty(...)` thay vì
> `System.getenv(...)` trực tiếp để giữ build an toàn với configuration cache.

## 3. Khởi tạo

*(Cần điền)*

Hầu hết SDK cần khởi tạo trong `Application.onCreate()`. Dự án **chưa có lớp `Application` tuỳ
biến** — đây là điều kiện chung với ForgeRock, Tuya và Hilt, nên nếu phải tạo thì tạo một lần dùng
cho tất cả:

```kotlin
class AiAssistantApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // TioSdk.init(this, BuildConfig.TIO_APP_KEY)
        // if (BuildConfig.DEBUG) TioSdk.setLogLevel(...)
    }
}
```

```xml
<application android:name=".AiAssistantApplication" ... >
```

Khoá/bí mật đưa qua `buildConfigField` (`buildConfig = true` đã bật) chứ không hardcode:

```kotlin
defaultConfig {
    buildConfigField("String", "TIO_APP_KEY", "\"${project.findProperty("tioAppKey") ?: ""}\"")
}
```

## 4. Quyền cần thêm

*(Cần điền — kiểm tra manifest của SDK sau khi merge)*

Sau khi thêm SDK, kiểm tra manifest đã merge để biết SDK tự thêm quyền gì:

```powershell
.\gradlew.bat :app:processDebugManifest
# rồi mở app/build/intermediates/merged_manifests/debug/AndroidManifest.xml
```

Đây là bước **không được bỏ qua**: nhiều SDK tự thêm quyền (vị trí, danh bạ, `READ_PHONE_STATE`)
mà bạn không hề khai báo, và Play Store sẽ hỏi về chúng. Muốn loại bỏ:

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"
    tools:node="remove" />
```

## 5. Bọc vào kiến trúc dự án

Đây là phần **không phụ thuộc vào việc TIO là gì** — quy ước áp dụng cho mọi SDK bên thứ ba trong
dự án này:

### 5.1. SDK không được rò lên tầng UI

```
Activity  ──►  ViewModel  ──►  Repository  ──►  DataSource  ──►  TIO SDK
                                                              ▲
                                          SDK chỉ xuất hiện từ đây trở đi
```

ViewModel và Activity **không được import** package của TIO. Nếu sau này đổi SDK, chỉ tầng
DataSource phải sửa.

### 5.2. Callback → coroutine

Nếu SDK dùng callback (phần lớn đều vậy), bọc lại:

```kotlin
// data/remote/TioDataSource.kt
class TioDataSource {

    suspend fun fetchSomething(id: String): Result<Something> =
        suspendCancellableCoroutine { cont ->
            TioSdk.fetch(id, object : TioCallback<TioResponse> {
                override fun onSuccess(response: TioResponse) {
                    cont.resume(Result.Success(response.toDomain()))
                }
                override fun onError(code: Int, message: String) {
                    cont.resume(Result.Error(IOException("TIO $code: $message")))
                }
            })

            // Nếu SDK hỗ trợ huỷ, gắn vào đây
            cont.invokeOnCancellation { /* TioSdk.cancel(id) */ }
        }

    /** Nếu SDK có luồng sự kiện liên tục. */
    fun observeSomething(): Flow<Something> = callbackFlow {
        val listener = object : TioListener {
            override fun onUpdate(data: TioData) { trySend(data.toDomain()) }
        }
        TioSdk.addListener(listener)
        awaitClose { TioSdk.removeListener(listener) }   // BẮT BUỘC
    }
}
```

Hai điểm bắt buộc:

- **`Result` là `com.example.application_ai_assisstant.data.Result`**, không phải `kotlin.Result` —
  import tường minh, xem [Repository Pattern §3](../01-kien-truc/repository-pattern.md#3-result--bọc-thành-côngthất-bại)
- **`awaitClose` phải huỷ đăng ký listener.** Đây là nguồn rò rỉ số một khi tích hợp SDK

### 5.3. Map model của SDK sang domain model

```kotlin
private fun TioResponse.toDomain() = Something(
    id = this.identifier,
    name = this.displayName ?: "",
)
```

Đừng đưa `TioResponse` lên UI. Xem
[Repository Pattern §5](../01-kien-truc/repository-pattern.md#5-mapping-model-giữa-các-tầng).

### 5.4. Dọn dẹp khi đăng xuất

Nếu TIO giữ state theo người dùng, thêm vào `AppRouter.logout()` — nơi đã dọn `SessionManager` và
cache repository:

```kotlin
fun logout(from: Activity) {
    SessionManager.clear()
    from.appContainer.clearUserData()
    // + TioSdk.clearUserSession()
    ...
}
```

Bỏ sót bước này thì người dùng tiếp theo sẽ thấy dữ liệu của người trước — lỗi rất khó phát hiện
trong test nhưng rất dễ gặp trên máy thật.

## 6. ProGuard / R8

*(Cần điền)*

SDK thường cần keep rule. Dự án hiện có `release { optimization { enable = false } }` nên **chưa
minify**, nhưng khi bật thì:

```proguard
# app/proguard-rules.pro
-keep class com.tio.** { *; }
-keepclassmembers class com.tio.** { *; }
-dontwarn com.tio.**
```

Dự án cũng có `app/src/main/keepRules/rules.keep` — kiểm tra xem có cần thêm vào đó.

## 7. Kiểm thử

*(Cần điền)*

- Có môi trường sandbox/staging không:
- Có tài khoản/thiết bị thử nghiệm không:
- Cách bật log chi tiết của SDK:

## 8. Lỗi thường gặp

*(Cần điền sau khi tích hợp — ghi lại mỗi lỗi gặp phải và cách sửa, đây là phần giá trị nhất của
tài liệu nội bộ)*

| Triệu chứng | Nguyên nhân | Cách sửa |
|---|---|---|
| | | |

## 9. Checklist tích hợp

Danh sách này áp dụng được ngay, không cần biết TIO là gì:

- [ ] Dependency khai báo qua version catalog (không hardcode chuỗi toạ độ)
- [ ] Repository nội bộ khai báo ở `settings.gradle.kts`, không ở module
- [ ] Khoá/bí mật qua `buildConfigField`, giá trị trong `local.properties`, không commit
- [ ] Kiểm tra manifest đã merge xem SDK thêm quyền gì
- [ ] Tạo lớp `Application` nếu SDK cần khởi tạo sớm
- [ ] Bọc callback thành `suspend`/`Flow`, có `awaitClose`
- [ ] Map model SDK → domain model
- [ ] SDK không xuất hiện ngoài tầng `data/`
- [ ] Dọn state trong `AppRouter.logout()`
- [ ] Keep rule cho R8 (trước khi bật minify)
- [ ] Kiểm tra ảnh hưởng kích thước APK và danh sách ABI

## Xem thêm

- [Repository Pattern](../01-kien-truc/repository-pattern.md) — nơi SDK được phép xuất hiện
- [Coroutines & Flow](../06-kotlin/coroutines-flow.md) — bọc callback
- [Tuya IoT](tuya-iot.md), [ForgeRock](forgerock.md) — hai ví dụ đã viết đầy đủ theo cùng khuôn mẫu
