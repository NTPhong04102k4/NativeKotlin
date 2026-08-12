# Tài liệu dự án Application_AI_Assisstant

Bộ tài liệu này giải thích **các mô hình kiến trúc và thành phần Android** dùng trong dự án, và
quan trọng hơn: **dự án này đang ở đâu so với chuẩn**. Mỗi trang đều bám vào code thật trong repo,
không phải lý thuyết chung chung.

> Quy ước đọc: phần "Trong dự án" là hiện trạng thực tế, phần "Còn thiếu" là khoảng cách so với
> chuẩn. Khi sửa code, cập nhật luôn tài liệu tương ứng.

## Mục lục

### 01 — Kiến trúc

| Trang | Nội dung |
|---|---|
| [MVVM](01-kien-truc/mvvm.md) | Mô hình chính của dự án. Phân tích lát cắt Login đã wire đầy đủ, LiveData vs StateFlow, lỗi thường gặp |
| [MVC · MVP · MVI](01-kien-truc/mvc-mvp-mvi.md) | So sánh 4 mô hình, khi nào chọn cái nào, ví dụ cùng một màn hình viết theo từng kiểu |
| [Clean Architecture](01-kien-truc/clean-architecture.md) | Chia tầng domain/data/presentation, UseCase, quy tắc phụ thuộc, khi nào KHÔNG nên dùng |
| [Repository Pattern](01-kien-truc/repository-pattern.md) | Vai trò Repository, DataSource, single source of truth, cache, mapping model |

### 02 — Android core

| Trang | Nội dung |
|---|---|
| [Vòng đời](02-android-core/vong-doi.md) | Activity lifecycle, process death, `SavedStateHandle`, vì sao ViewModel không cứu được mọi thứ |
| [Navigation](02-android-core/navigation.md) | `AppRouter` của dự án: back stack, giữ state tab, so sánh với Navigation Component |
| [BroadcastReceiver](02-android-core/broadcast-receiver.md) | Static vs dynamic receiver, giới hạn từ Android 8+, `AlarmReceiver`/`SystemEventReceiver` |
| [Permissions](02-android-core/permissions.md) | Install-time vs runtime vs special permission, luồng xin quyền chuẩn, hiện trạng dự án |

### 03 — UI

| Trang | Nội dung |
|---|---|
| [Material 2 vs Material 3](03-ui/material2-vs-material3.md) | Khác biệt thực tế, bảng ánh xạ tên component/thuộc tính, bẫy khi trộn hai bộ |
| [Theming & Design tokens](03-ui/theming-tokens.md) | `colors.xml` → `themes.xml` → `styles.xml`, dark mode, dynamic color, quy tắc không hardcode |
| [ViewBinding](03-ui/viewbinding.md) | Cơ chế sinh class, field nullable, so sánh `findViewById`/DataBinding/Compose |
| [Responsive layout](03-ui/responsive-layout.md) | Qualifier `-w600dp/-w936dp/-w1240dp`, window size class, quy tắc đồng bộ giữa các biến thể |

### 04 — Widget (Android & iOS)

| Trang | Nội dung |
|---|---|
| [App Widget](04-app-widget/app-widget.md) | `AppWidgetProvider`, `RemoteViews`, màn hình cấu hình, `PendingIntent`, giới hạn |
| [Jetpack Glance](04-app-widget/jetpack-glance.md) | Viết widget bằng Compose — kèm cảnh báo dự án đang cấm Compose |
| [WidgetKit (iOS)](04-app-widget/widgetkit-ios.md) | `TimelineProvider`, App Group, widget tương tác — tài liệu định hướng |

### 05 — Jetpack

| Trang | Nội dung |
|---|---|
| [Bản đồ Jetpack](05-jetpack/README.md) | Toàn cảnh các thư viện, dự án đang dùng gì, quy ước version catalog |
| [Lifecycle & ViewModel](05-jetpack/lifecycle-viewmodel.md) | `viewModelScope`, `repeatOnLifecycle`, `SavedStateHandle`, bẫy sự kiện một lần |
| [Room & DataStore](05-jetpack/room-datastore.md) | Entity/DAO/migration, DataStore, lưu token an toàn |
| [WorkManager](05-jetpack/workmanager.md) | Worker, ràng buộc, chuỗi việc, foreground worker |
| [Paging & RecyclerView](05-jetpack/paging-recyclerview.md) | `ListAdapter`, `DiffUtil`, Paging 3, SwipeRefresh |
| [Hilt & DI](05-jetpack/hilt-di.md) | Từ factory thủ công → Service Locator → Hilt/Koin |

### 06 — Kotlin

| Trang | Nội dung |
|---|---|
| [Kotlin cho Android](06-kotlin/kotlin-android.md) | Null safety, sealed class, extension, scope function, delegate |
| [Coroutines & Flow](06-kotlin/coroutines-flow.md) | Scope, dispatcher, `StateFlow`/`SharedFlow`, `callbackFlow`, test |

### 07 — Tích hợp SDK & nền tảng ngoài

| Trang | Nội dung |
|---|---|
| [Tổng quan + quy ước chung](07-tich-hop/README.md) | 7 nguyên tắc áp dụng cho mọi SDK, 3 điều kiện chung còn thiếu |
| [Networking — Retrofit/OkHttp](07-tich-hop/networking-retrofit.md) | ApiClient, DTO/mapper, interceptor, bảo mật, MockWebServer |
| [Biometric](07-tich-hop/biometric.md) | `BiometricPrompt`, `CryptoObject`, 4 vấn đề của code hiện tại |
| [Bluetooth](07-tich-hop/bluetooth.md) | Classic vs BLE, quyền chia đôi ở API 31, GATT, `callbackFlow` |
| [WebView](07-tich-hop/webview.md) | Custom Tabs vs WebView, JS bridge, bảo mật, vòng đời |
| [ForgeRock](07-tich-hop/forgerock.md) | Journey vs OIDC, token, biometric binding, interceptor |
| [Twilio](07-tich-hop/twilio.md) | OTP qua backend, SMS Retriever, vì sao nên bỏ quyền SMS |
| [Tuya IoT](07-tich-hop/tuya-iot.md) | Home/Device/DP, ghép đôi Wi-Fi & BLE, bọc SDK |
| [TIO SDK](07-tich-hop/tio-sdk.md) | 📝 Khung tài liệu — **cần bổ sung thông tin nội bộ** |
| [Native Bridge](07-tich-hop/native-bridge.md) | JS bridge, React Native TurboModule, Flutter channel, JNI, KMP |

## Bối cảnh dự án (tóm tắt)

- Android thuần Kotlin, **XML + ViewBinding**, không Compose, không Navigation Component.
- Mỗi màn hình là **một Activity riêng**; điều hướng đi qua
  [`util/AppRouter.kt`](../app/src/main/java/com/example/application_ai_assisstant/util/AppRouter.kt).
- Material 3 (`Theme.Material3.DayNight.NoActionBar`) + design token trong `res/values/colors.xml`.
- Chi tiết build, lệnh Gradle, quy ước lint: xem [`CLAUDE.md`](../CLAUDE.md) ở thư mục gốc.

## Trạng thái thực tế (cập nhật 11/08/2026)

Codebase đang thay đổi nhanh. Bảng này là mốc đối chiếu — **nếu nó lệch với code thì tin code**,
và cập nhật lại bảng.

**Đã có:**

| Thành phần | Ở đâu |
|---|---|
| Lớp `Application` | `AiAssistantApp.kt` — khai báo trong manifest, gọi `SessionManager.init()` |
| Service Locator | `di/AppContainer.kt` — repository là singleton, `by lazy` |
| Phiên đăng nhập bền | `data/local/SessionManager.kt` — SharedPreferences + `StateFlow<LoggedInUser?>` |
| Dọn phiên tập trung | `data/SessionRepository.kt` — xoá token + cache mọi repository |
| Tầng network | `data/remote/ApiClient.kt`, `ApiService.kt`, `dto/`, `SafeApiCall.kt`, `AppException.kt` |
| Lát cắt Discovery đầy đủ | `ui/discovery/` — ViewModel + Factory + UiState + Adapter + Detail |
| Lát cắt Schedule | `ui/schedule/` — ViewModel + Factory |
| Ánh xạ lỗi → chuỗi | `ui/common/ErrorMessages.kt` |
| Router tập trung | `util/AppRouter.kt` — giữ state tab, Back đúng chuẩn |

**Còn thiếu / còn nợ:**

| Việc | Chi tiết |
|---|---|
| **Login vẫn đồng bộ** | `LoginViewModel.login()` gọi repository trực tiếp trên main thread. Chạy được vì `LoginDataSource` còn là stub — nối API thật vào là **ANR**. → [Coroutines §9](06-kotlin/coroutines-flow.md) |
| **`LoginDataSource` là stub** | Bỏ qua username/password, trả "Jane Doe" + UUID ngẫu nhiên |
| **Login bỏ qua `AppContainer`** | `LoginViewModelFactory` tự `new LoginRepository(LoginDataSource())` nên tồn tại **hai** instance; `appContainer.loginRepository` không được dùng |
| **Dark mode hỏng** | Chỉ có `values-night/themes.xml`, thiếu `values-night/colors.xml` → nền vẫn trắng ở chế độ tối. Sửa mất 6 dòng. → [Theming §5](03-ui/theming-tokens.md) |
| **Chưa xin quyền runtime** | Không có `checkSelfPermission`/`RequestPermission` ở bất kỳ đâu, dù manifest khai 13 quyền. → [Permissions](02-android-core/permissions.md) |
| **Widget chưa mở được app** | Chưa gắn `PendingIntent`. → [App Widget §6](04-app-widget/app-widget.md) |
| **Quyền SMS nên bỏ** | `RECEIVE_SMS`/`READ_SMS` chỉ để ghi log, rủi ro bị Play từ chối. → [Twilio §4](07-tich-hop/twilio.md) |
| **Chưa có test thật** | Chỉ 2 test mẫu do IDE sinh |
