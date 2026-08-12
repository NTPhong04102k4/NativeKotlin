# Jetpack — bản đồ toàn bộ

Jetpack là bộ thư viện AndroidX của Google. Không phải một framework mà là **hàng chục thư viện độc
lập** — dùng cái nào thì thêm cái đó.

## 1. Dự án đang dùng gì

Đọc từ `gradle/libs.versions.toml` và `app/build.gradle.kts`:

| Thư viện | Phiên bản | Dùng ở đâu |
|---|---|---|
| `androidx.core:core-ktx` | 1.10.1 | Extension Kotlin cho API framework |
| `androidx.appcompat` | 1.6.1 | `AppCompatActivity`, tương thích ngược |
| `androidx.constraintlayout` | 2.1.4 | Layout của hầu hết màn hình |
| `androidx.activity:activity-ktx` | 1.8.0 | `enableEdgeToEdge`, `onBackPressedDispatcher`, `addCallback` |
| `androidx.lifecycle:lifecycle-viewmodel-ktx` | 2.6.1 | `ViewModel`, `viewModelScope` |
| `androidx.lifecycle:lifecycle-livedata-ktx` | 2.6.1 | `LiveData` trong `LoginViewModel` |
| `androidx.lifecycle:lifecycle-runtime-ktx` | 2.6.1 | `repeatOnLifecycle` |
| `androidx.recyclerview` | 1.3.2 | Danh sách Discovery / Schedule |
| `androidx.swiperefreshlayout` | 1.1.0 | Kéo xuống để làm mới |
| `androidx.preference` | 1.2.0 | `SettingsActivity` + `root_preferences.xml` |
| `androidx.annotation` | 1.6.0 | `@StringRes`, `@DrawableRes` |
| `androidx.core:core-splashscreen` | 1.0.1 | `installSplashScreen()` |
| `androidx.biometric` | 1.1.0 | `BiometricHelper` |
| `com.google.android.material` | 1.5.0 | Material 3 |
| `kotlinx-coroutines-android` | 1.8.1 | `lifecycleScope`, `StateFlow`, `suspend` |
| `retrofit` + `converter-gson` | 2.11.0 | HTTP client (chưa có API nào được wire) |
| `okhttp-logging-interceptor` | 4.12.0 | Log HTTP khi debug |

`buildConfig = true` đã được bật để `ApiClient` đọc được `BuildConfig.DEBUG` — mặc định AGP 8+ tắt
tính năng này.

## 2. Chưa dùng nhưng nhiều khả năng sẽ cần

| Thư viện | Giải quyết vấn đề gì | Tài liệu |
|---|---|---|
| **Room** | Lưu lịch trình/bài viết offline. Hiện chưa có persistence nào | [Room & DataStore](room-datastore.md) |
| **DataStore** | Thay `SharedPreferences`, lưu token đăng nhập | [Room & DataStore](room-datastore.md) |
| **WorkManager** | Đồng bộ nền, làm mới widget dày hơn 30 phút | [WorkManager](workmanager.md) |
| **Paging 3** | Phân trang danh sách Discovery | [Paging & RecyclerView](paging-recyclerview.md) |
| **Hilt** | Thay `LoginViewModelFactory` viết tay khi số ViewModel tăng | [Hilt & DI](hilt-di.md) |
| **SavedStateHandle** | Sống sót qua process death | [Vòng đời](../02-android-core/vong-doi.md) |

## 3. Toàn cảnh Jetpack

### Foundation — nền tảng

| Thư viện | Việc |
|---|---|
| `core-ktx` | Extension Kotlin cho API framework ✅ *đang dùng* |
| `appcompat` | Tương thích ngược ✅ *đang dùng* |
| `annotation` | `@StringRes`, `@WorkerThread`, `@RequiresApi` ✅ *đang dùng* |
| `test` | Espresso, JUnit ext ✅ *có nhưng chỉ 2 test mẫu* |
| `multidex` | Vượt giới hạn 64K method (hiếm cần với minSdk 24+) |

### Architecture — kiến trúc

| Thư viện | Việc |
|---|---|
| `lifecycle` | `ViewModel`, `LiveData`, `LifecycleObserver` ✅ *đang dùng* |
| `room` | ORM trên SQLite |
| `datastore` | Lưu key-value / Proto, thay `SharedPreferences` |
| `workmanager` | Việc nền có ràng buộc, đảm bảo chạy |
| `paging` | Tải danh sách theo trang |
| `navigation` | Điều hướng Fragment ❌ *cố ý không dùng — xem [Navigation](../02-android-core/navigation.md)* |
| `hilt` | Dependency injection |
| `startup` | Khởi tạo thư viện có thứ tự lúc app khởi động |

### UI

| Thư viện | Việc |
|---|---|
| `constraintlayout` | Layout phẳng, linh hoạt ✅ *đang dùng* |
| `recyclerview` | Danh sách hiệu năng cao ✅ *đã khai báo, chưa có adapter* |
| `viewpager2` | Vuốt ngang giữa các trang |
| `fragment` | ❌ *chỉ dùng cho `PreferenceFragmentCompat`* |
| `preference` | Màn hình cài đặt ✅ *đang dùng* |
| `compose` | UI khai báo ❌ *cố ý không dùng* |
| `glance` | Widget bằng Compose — xem [Jetpack Glance](../04-app-widget/jetpack-glance.md) |
| `splashscreen` | API splash chuẩn ✅ *đang dùng* |
| `window` | Thiết bị gập, window size class |

### Behavior — hành vi

| Thư viện | Việc |
|---|---|
| `biometric` | Vân tay / khuôn mặt ✅ *đang dùng* — xem [Biometric](../07-tich-hop/biometric.md) |
| `camerax` | Camera |
| `media3` | Phát nhạc/video (thay ExoPlayer) |
| `browser` | Custom Tabs — xem [WebView](../07-tich-hop/webview.md) |
| `security-crypto` | `EncryptedSharedPreferences` |
| `sharetarget` | Chia sẻ trực tiếp |

## 4. Version catalog — quy ước bắt buộc

Mọi dependency mới **phải** khai báo qua `gradle/libs.versions.toml`:

```toml
[versions]
room = "2.6.1"

[libraries]
androidx-room-runtime  = { group = "androidx.room", name = "room-runtime",  version.ref = "room" }
androidx-room-ktx      = { group = "androidx.room", name = "room-ktx",      version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
```

```kotlin
// app/build.gradle.kts
implementation(libs.androidx.room.runtime)
implementation(libs.androidx.room.ktx)
ksp(libs.androidx.room.compiler)
```

Dấu `-` trong catalog thành `.` khi tham chiếu: `androidx-room-ktx` → `libs.androidx.room.ktx`.

> Hiện còn ba dependency hardcode chuỗi toạ độ (splashscreen, biometric, play-services-auth).
> Đó là nợ kỹ thuật — đừng thêm cái thứ tư. Các dependency thêm gần đây (coroutines, retrofit,
> okhttp, recyclerview, swiperefreshlayout, lifecycle-runtime) đều đã đi qua catalog, đúng quy ước.

## 5. Hai lưu ý riêng của dự án

**a) Không apply plugin Kotlin.** AGP 9 đã có sẵn hỗ trợ Kotlin. Đừng thêm
`org.jetbrains.kotlin.android` trừ khi làm lại toàn bộ phần plugin. Điều này ảnh hưởng tới các thư
viện cần annotation processor — muốn dùng KSP (Room, Hilt) thì phải thêm plugin KSP và kiểm tra kỹ
tương thích với AGP 9.

**b) Configuration cache đang bật.** Mọi thay đổi build script buộc Gradle chạy lại pha
configuration. Giữ build logic an toàn với configuration cache — tránh đọc `System.getenv` hay truy
cập `Project` lúc thực thi task.

## Mục lục

- [Lifecycle & ViewModel](lifecycle-viewmodel.md)
- [Room & DataStore](room-datastore.md)
- [WorkManager](workmanager.md)
- [Paging & RecyclerView](paging-recyclerview.md)
- [Hilt & Dependency Injection](hilt-di.md)
