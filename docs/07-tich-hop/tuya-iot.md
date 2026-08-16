# Tuya IoT SDK

> **Trạng thái:** dự án **chưa tích hợp** Tuya. Có `util/BluetoothHelper` viết tay nhưng chưa dùng
> ở đâu. Trang này mô tả cách tích hợp và quan hệ với helper Bluetooth hiện có.

Tuya (Tuya Smart / Smart Life) là nền tảng IoT — SDK của họ bọc sẵn việc dò tìm, ghép đôi
(pairing), điều khiển và cập nhật firmware cho thiết bị thông minh.

## 1. Tuya thay thế được gì

Nếu mục tiêu là điều khiển thiết bị IoT, Tuya SDK làm sẵn phần lớn những gì
[Bluetooth](bluetooth.md) mô tả:

| Việc | Tự viết (BluetoothHelper) | Tuya SDK |
|---|---|---|
| Quét thiết bị | Tự viết `ScanCallback`, tự lọc trùng | `TuyaHomeSdk.getBleOperator().startLeScan()` |
| Ghép đôi Wi-Fi | Không làm được | Có (EZ mode / AP mode) |
| Ghép đôi BLE | Tự viết GATT + protocol riêng | Có |
| Điều khiển | Tự định nghĩa characteristic | Data Point (DP) chuẩn hoá |
| Điều khiển từ xa (qua mây) | Không có | Có (MQTT) |
| Cập nhật firmware OTA | Không có | Có |
| Quản lý nhà/phòng/thành viên | Không có | Có |

Nói cách khác: dùng Tuya thì `BluetoothHelper` gần như **không cần nữa** cho phần thiết bị Tuya.
Nó chỉ còn hữu ích nếu app cũng phải nói chuyện với thiết bị BLE không thuộc hệ Tuya.

## 2. Khái niệm cốt lõi

| Khái niệm | Nghĩa |
|---|---|
| **Home** | Đơn vị tổ chức trên cùng. Mọi thiết bị phải thuộc một Home |
| **Room** | Nhóm thiết bị trong Home |
| **Device** | Một thiết bị vật lý, có `devId` |
| **DP (Data Point)** | Một thuộc tính điều khiển được. `"1"` = bật/tắt, `"2"` = độ sáng… |
| **Pairing / Activation** | Đưa thiết bị mới vào Home |

**DP là thứ quan trọng nhất phải hiểu.** Điều khiển thiết bị = gửi map `dpId -> value`:

```kotlin
device.publishDps("""{"1": true, "2": 500}""", callback)   // bật, độ sáng 500
```

Danh sách DP của từng loại thiết bị nằm trên Tuya IoT Platform — **phải tra bảng DP của product
đó**, không đoán được. Đây là nguồn gốc của hầu hết lỗi "gửi lệnh mà thiết bị không phản ứng".

## 3. Cài đặt

### 3.1. Đăng ký trên Tuya IoT Platform

Cần trước khi viết code:

1. Tạo Cloud Project trên [iot.tuya.com](https://iot.tuya.com)
2. Lấy `AppKey` và `AppSecret`
3. Khai báo **package name** và **SHA256 của keystore** — Tuya khớp cả hai, sai là SDK từ chối
   khởi tạo

> ⚠️ Package name của dự án là `com.ntp.application_ai_assisstant` (có lỗi chính tả cố ý —
> `CLAUDE.md` yêu cầu giữ nguyên). Khai báo trên Tuya **phải khớp chính xác**, kể cả lỗi chính tả.
>
> SHA256 khác nhau giữa debug và release → phải khai báo **cả hai**.

```powershell
keytool -list -v -keystore "$env:USERPROFILE\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android
```

### 3.2. Gradle

```toml
[versions]
tuya = "5.15.0"

[libraries]
tuya-home-sdk = { group = "com.thingclips.smart", name = "thingsmart", version.ref = "tuya" }
```

```kotlin
dependencies {
    implementation(libs.tuya.home.sdk)
}

android {
    defaultConfig {
        ndk {
            // Tuya có thư viện native — giới hạn ABI để giảm kích thước APK
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    packaging {
        resources.excludes += setOf("META-INF/*")   // thường phải thêm để tránh trùng file
    }
}
```

> ⚠️ Tuya SDK khá lớn (thêm ~15–25 MB tuỳ ABI) và có nhiều thư viện `.so`. Cân nhắc App Bundle để
> Play Store chỉ giao ABI phù hợp cho từng máy.

### 3.3. Khởi tạo

Cần lớp `Application` — dự án **chưa có**:

```kotlin
class AiAssistantApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ThingHomeSdk.init(this, BuildConfig.TUYA_APP_KEY, BuildConfig.TUYA_APP_SECRET)
        if (BuildConfig.DEBUG) ThingHomeSdk.setDebugMode(true)
    }
}
```

Đặt key qua `buildConfigField` thay vì hardcode (`buildConfig = true` đã bật):

```kotlin
// app/build.gradle.kts
defaultConfig {
    buildConfigField("String", "TUYA_APP_KEY", "\"${project.findProperty("tuyaAppKey") ?: ""}\"")
    buildConfigField("String", "TUYA_APP_SECRET", "\"${project.findProperty("tuyaAppSecret") ?: ""}\"")
}
```

Giá trị thật để trong `local.properties` hoặc biến môi trường CI — **không commit**.

> ⚠️ Configuration cache đang bật. Đọc `project.findProperty` ở pha configuration là an toàn; đọc
> `System.getenv` trong task action thì **không**. Giữ nguyên như trên.

## 4. Quyền

Tuya cần nhiều hơn những gì manifest đang có:

```xml
<!-- Đã có -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"
    android:usesPermissionFlags="neverForLocation" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

<!-- CẦN THÊM cho ghép đôi Wi-Fi -->
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
```

> ⚠️ **`ACCESS_FINE_LOCATION` là bắt buộc để ghép đôi Wi-Fi**, không phải để định vị. Android yêu
> cầu quyền vị trí mới đọc được SSID của mạng Wi-Fi đang kết nối — mà Tuya cần SSID để cấu hình
> thiết bị. Đây là dangerous permission và phải giải thích rõ cho người dùng vì sao cần.
>
> Cũng lưu ý: nếu thêm `ACCESS_FINE_LOCATION` thì cờ `neverForLocation` trên `BLUETOOTH_SCAN` mất
> phần lớn ý nghĩa với Play Store — chuẩn bị giải trình khi submit.

## 5. Đăng nhập

Tuya có hệ danh tính **riêng**, tách biệt với hệ đăng nhập của app.

```kotlin
ThingHomeSdk.getUserInstance().loginWithEmail(
    countryCode, email, password,
    object : ILoginCallback {
        override fun onSuccess(user: User) { /* ... */ }
        override fun onError(code: String, error: String) { /* ... */ }
    }
)
```

Với app đã có hệ đăng nhập riêng (ForgeRock, hoặc `LoginRepository` hiện tại), dùng **custom
login**: backend của bạn gọi Tuya API để tạo/ánh xạ user, app nhận về credential Tuya.

```
Người dùng đăng nhập app  ──►  Backend  ──►  tạo/ánh xạ user Tuya
                                    │
                          trả credential Tuya về app
                                    │
                          ThingHomeSdk.getUserInstance().loginWithUid(...)
```

Đừng bắt người dùng đăng nhập hai lần.

## 6. Bọc vào kiến trúc dự án

Tuya SDK dùng callback. Theo mô hình Repository của dự án, bọc lại thành `suspend`/`Flow`:

```kotlin
// data/remote/TuyaDeviceDataSource.kt
class TuyaDeviceDataSource {

    suspend fun getDevices(homeId: Long): Result<List<DeviceBean>> =
        suspendCancellableCoroutine { cont ->
            ThingHomeSdk.newHomeInstance(homeId).getHomeDetail(
                object : IThingHomeResultCallback {
                    override fun onSuccess(bean: HomeBean) {
                        cont.resume(Result.Success(bean.deviceList))
                    }
                    override fun onError(code: String, msg: String) {
                        cont.resume(Result.Error(IOException("$code: $msg")))
                    }
                }
            )
        }

    /** Trạng thái thiết bị theo thời gian thực. */
    fun observeDevice(devId: String): Flow<Map<String, Any>> = callbackFlow {
        val device = ThingHomeSdk.newDeviceInstance(devId)

        device.registerDevListener(object : IDevListener {
            override fun onDpUpdate(devId: String, dpStr: String) {
                trySend(parseDps(dpStr))
            }
            override fun onRemoved(devId: String) { close() }
            override fun onStatusChanged(devId: String, online: Boolean) { }
            override fun onNetworkStatusChanged(devId: String, status: Boolean) { }
            override fun onDevInfoUpdate(devId: String) { }
        })

        awaitClose { device.onDestroy() }     // BẮT BUỘC — nếu không rò rỉ listener
    }

    suspend fun sendCommand(devId: String, dps: Map<String, Any>): Result<Unit> =
        suspendCancellableCoroutine { cont ->
            ThingHomeSdk.newDeviceInstance(devId).publishDps(
                Gson().toJson(dps),
                object : IResultCallback {
                    override fun onSuccess() { cont.resume(Result.Success(Unit)) }
                    override fun onError(code: String, error: String) {
                        cont.resume(Result.Error(IOException("$code: $error")))
                    }
                }
            )
        }
}
```

Ba điểm quan trọng:

1. `awaitClose { device.onDestroy() }` — Tuya listener rất dễ rò rỉ. `callbackFlow` gắn việc dọn dẹp
   vào vòng đời Flow nên không thể quên. Xem
   [Coroutines §7](../06-kotlin/coroutines-flow.md#7-callbackflow--bọc-api-callback).
2. `DeviceBean` là model của SDK → **phải map sang domain model** trước khi đưa lên UI, đúng nguyên
   tắc ở [Repository Pattern](../01-kien-truc/repository-pattern.md).
3. `Result` là của dự án, không phải `kotlin.Result`.

## 7. Ghép đôi thiết bị

### Wi-Fi EZ mode

```kotlin
// 1. Lấy token (cần có mạng)
ThingHomeSdk.getActivatorInstance().getActivatorToken(homeId,
    object : IThingActivatorGetToken {
        override fun onSuccess(token: String) { startEzActivator(token) }
        override fun onFailure(code: String, msg: String) { }
    })

// 2. Phát cấu hình Wi-Fi
private fun startEzActivator(token: String) {
    val builder = ActivatorBuilder()
        .setSsid(ssid)
        .setContext(context)
        .setPassword(password)
        .setActivatorModel(ThingActivatorModeEnum.TY_EZ)
        .setTimeOut(100)
        .setToken(token)
        .setListener(object : IThingSmartActivatorListener {
            override fun onActiveSuccess(devResp: DeviceBean) { }
            override fun onError(errorCode: String, errorMsg: String) { }
            override fun onStep(step: String, data: Any) { }
        })

    ThingHomeSdk.getActivatorInstance().newMultiActivator(builder).start()
}
```

Thứ tự bắt buộc: **lấy token → phát cấu hình**. Token có thời hạn ngắn (~10 phút).

Lỗi thường gặp khi ghép đôi:

| Lỗi | Nguyên nhân |
|---|---|
| Không tìm thấy thiết bị | Thiết bị chưa ở chế độ ghép đôi (thường phải nhấn giữ 5s tới khi đèn nháy nhanh) |
| Timeout | Đang ở mạng Wi-Fi 5GHz — hầu hết thiết bị Tuya **chỉ hỗ trợ 2.4GHz** |
| Sai mật khẩu | SSID có ký tự đặc biệt hoặc emoji |
| Không đọc được SSID | Thiếu `ACCESS_FINE_LOCATION` |

## 8. Giải phóng tài nguyên

```kotlin
// Khi rời màn hình danh sách thiết bị
ThingHomeSdk.newDeviceInstance(devId).onDestroy()
ThingHomeSdk.getActivatorInstance().onDestroy()

// Khi đăng xuất — thêm vào AppRouter.logout()
ThingHomeSdk.getUserInstance().logout(object : ILogoutCallback { /* ... */ })
```

`AppRouter.logout()` đã dọn `SessionManager` và cache repository — Tuya logout nên vào cùng chỗ đó,
nếu không người dùng tiếp theo vẫn thấy thiết bị của người trước.

## 9. Trước khi quyết định dùng Tuya

| Cân nhắc | Chi tiết |
|---|---|
| Kích thước APK | +15–25 MB. Đáng kể với app hiện tại rất nhẹ |
| Phụ thuộc nhà cung cấp | Toàn bộ thiết bị phải nằm trong hệ Tuya |
| Hai hệ danh tính | Phải ánh xạ user app ↔ user Tuya ở backend |
| Quyền vị trí | Bắt buộc cho ghép đôi Wi-Fi, cần giải trình với Play Store |
| Cần `Application` class | Dự án chưa có — cũng là điều kiện của ForgeRock và Hilt |
| Thiết bị không thuộc Tuya | Vẫn phải tự viết BLE — `BluetoothHelper` chưa bỏ được |

Nếu chỉ cần điều khiển vài thiết bị BLE của riêng bạn, tự viết GATT
([Bluetooth §7](bluetooth.md#7-ble--quét-và-kết-nối)) nhẹ hơn nhiều. Tuya đáng dùng khi phải hỗ trợ
**nhiều loại thiết bị thương mại** và cần điều khiển từ xa qua mây.

## Xem thêm

- [Bluetooth](bluetooth.md) — phần Tuya thay thế được
- [Permissions](../02-android-core/permissions.md)
- [Repository Pattern](../01-kien-truc/repository-pattern.md)
- [Coroutines & Flow](../06-kotlin/coroutines-flow.md)
