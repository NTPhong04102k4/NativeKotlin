# Tích hợp SDK & nền tảng ngoài

Mỗi trang trong nhóm này theo cùng một cấu trúc: **hiện trạng trong dự án → cách làm đúng → việc cần
làm**. Phần "hiện trạng" luôn nói rõ cái gì đã có, cái gì chưa.

## Mục lục

| Trang | Trạng thái trong dự án |
|---|---|
| [Networking — Retrofit & OkHttp](networking-retrofit.md) | ⚠️ Dependency **đã có**, chưa wire endpoint nào |
| [Biometric](biometric.md) | ⚠️ `BiometricHelper` **đã có và đã nối UI**, nhưng chưa tạo phiên đăng nhập thật |
| [Bluetooth](bluetooth.md) | ⚠️ `BluetoothHelper` **đã có**, chưa nơi nào gọi, chưa xin quyền |
| [WebView](webview.md) | ❌ Chưa dùng — tài liệu tham chiếu |
| [ForgeRock](forgerock.md) | ❌ Chưa tích hợp — luồng login hiện là stub |
| [Twilio](twilio.md) | ❌ Chưa tích hợp — nhưng quyền SMS **đã khai báo sẵn** (nên bỏ) |
| [Tuya IoT](tuya-iot.md) | ❌ Chưa tích hợp |
| [TIO SDK](tio-sdk.md) | 📝 **Khung tài liệu — cần bổ sung thông tin** |
| [Native Bridge](native-bridge.md) | ❌ Không áp dụng (Android thuần) — tài liệu định hướng |

## Quy ước chung cho mọi tích hợp

Bảy nguyên tắc này áp dụng cho **mọi** SDK bên thứ ba trong dự án, bất kể SDK nào:

### 1. SDK không được rò ra khỏi tầng `data/`

```
Activity  ──►  ViewModel  ──►  Repository  ──►  DataSource  ──►  SDK
                                                            ▲
                                        SDK chỉ tồn tại từ đây trở đi
```

Activity và ViewModel **không import** package của SDK. Đổi SDK thì chỉ DataSource phải sửa.

### 2. Dependency đi qua version catalog

Khai báo trong `gradle/libs.versions.toml`, tham chiếu bằng `libs.*`. Ba dependency hiện đang
hardcode chuỗi toạ độ (splashscreen, biometric, play-services-auth) là nợ kỹ thuật — đừng thêm cái
thứ tư.

### 3. Callback → `suspend` / `Flow`

```kotlin
// Một kết quả
suspend fun doThing(): Result<T> = suspendCancellableCoroutine { cont -> ... }

// Luồng sự kiện — awaitClose là BẮT BUỘC
fun observe(): Flow<T> = callbackFlow {
    sdk.addListener(listener)
    awaitClose { sdk.removeListener(listener) }
}
```

Thiếu `awaitClose` là nguồn rò rỉ số một khi tích hợp SDK.

### 4. `Result` là của dự án, không phải `kotlin.Result`

```kotlin
import com.ntp.application_ai_assisstant.data.Result
```

`data/Result.kt` che khuất `kotlin.Result`. Thêm WorkManager vào là có ba `Result` khác nhau cùng
tồn tại.

### 5. Model của SDK phải được map sang domain model

`DeviceBean`, `FRUser`, `LoginResponseDto` — không thứ nào được đưa lên UI. Viết mapper.

### 6. Kiểm tra manifest đã merge

```powershell
.\gradlew.bat :app:processDebugManifest
# app/build/intermediates/merged_manifests/debug/AndroidManifest.xml
```

SDK thường tự thêm quyền mà bạn không khai báo. Loại bỏ bằng `tools:node="remove"`.

### 7. Dọn state khi đăng xuất

`AppRouter.logout()` là nơi duy nhất dọn phiên. Mỗi SDK giữ state theo người dùng phải được dọn ở
đó, nếu không người dùng tiếp theo thấy dữ liệu của người trước.

## Ba điều kiện chung còn thiếu

Ba thứ này bị **nhiều** tích hợp cùng yêu cầu — làm một lần dùng cho tất cả:

| Việc | ForgeRock | Tuya | TIO | Hilt | Networking |
|---|:---:|:---:|:---:|:---:|:---:|
| **Lớp `Application` tuỳ biến** | ✓ | ✓ | ✓ | ✓ | |
| **Luồng login bất đồng bộ** (`suspend` + `viewModelScope`) | ✓ | | | | ✓ |
| **Luồng xin quyền runtime** | | ✓ | ✓ | | |

Không có cái nào trong ba thứ đó tồn tại hiện tại. Làm chúng trước sẽ tiết kiệm công cho mọi tích
hợp về sau.

## Xem thêm

- [Permissions](../02-android-core/permissions.md) — hiện chưa có chỗ nào xin quyền runtime
- [Repository Pattern](../01-kien-truc/repository-pattern.md) — nơi SDK được phép xuất hiện
- [Coroutines & Flow](../06-kotlin/coroutines-flow.md) — bọc callback của SDK
