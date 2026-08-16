# Quyền (Permissions)

## 1. Ba loại quyền

| Loại | Xin thế nào | Ví dụ |
|---|---|---|
| **Normal** (install-time) | Chỉ cần khai báo manifest, hệ thống tự cấp | `INTERNET`, `ACCESS_NETWORK_STATE`, `USE_BIOMETRIC` |
| **Dangerous** (runtime) | Manifest **+** hỏi người dùng lúc chạy | `RECEIVE_SMS`, `READ_PHONE_STATE`, `POST_NOTIFICATIONS`, `BLUETOOTH_CONNECT` |
| **Special** | Manifest **+** đưa người dùng sang màn hình Cài đặt hệ thống | `SCHEDULE_EXACT_ALARM`, `SYSTEM_ALERT_WINDOW`, `MANAGE_EXTERNAL_STORAGE` |

Sai lầm phổ biến nhất: khai báo trong manifest rồi tưởng là đã có quyền. Với hai loại sau, **khai
báo chỉ là điều kiện cần**.

## 2. Hiện trạng dự án

`AndroidManifest.xml` khai báo 13 quyền:

```xml
<!-- Normal: tự động được cấp -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.USE_BIOMETRIC" />
<uses-permission android:name="android.permission.BLUETOOTH" />          <!-- ≤ API 30 -->
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />    <!-- ≤ API 30 -->

<!-- Dangerous: PHẢI xin lúc chạy -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.READ_PHONE_STATE" />
<uses-permission android:name="android.permission.RECEIVE_SMS" />
<uses-permission android:name="android.permission.READ_SMS" />

<!-- Special: phải đưa sang màn hình Cài đặt -->
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
```

> ⚠️ **Trong toàn bộ codebase hiện chưa có một chỗ nào xin quyền runtime.** Không có
> `requestPermissions`, không có `ActivityResultContracts.RequestPermission`.

Hệ quả cụ thể trên thiết bị thật:

| Thành phần | Trạng thái |
|---|---|
| `util/BluetoothHelper.getPairedDevicesNames()` | Trả về list rỗng hoặc ném `SecurityException` (API 31+). Hàm đang có `@SuppressLint("MissingPermission")` — chỉ tắt cảnh báo lint, **không cấp quyền** |
| `receiver/SystemEventReceiver` | Không nhận được SMS / trạng thái cuộc gọi |
| `util/NotificationHelper.showNotification()` | Im lặng không hiện gì trên Android 13+ |
| `util/AlarmHelper.setExactAlarm()` | **Đã xử lý** — tự kiểm tra và hạ cấp, xem §5 |

## 3. Luồng xin quyền runtime chuẩn

Dùng Activity Result API (`startActivityForResult` và `onRequestPermissionsResult` đã deprecated):

```kotlin
class SomeActivity : AppCompatActivity() {

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) doTheThing() else showRationaleOrSettings()
    }

    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return  // < 13: không cần

        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED -> doTheThing()

            // Người dùng đã từ chối 1 lần -> giải thích trước khi hỏi lại
            shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) ->
                showRationaleDialog {
                    requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }

            else -> requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
```

Xin nhiều quyền cùng lúc dùng `ActivityResultContracts.RequestMultiplePermissions()`.

**Ba trạng thái, không phải hai:**

```
Chưa hỏi bao giờ         → launch() sẽ hiện dialog hệ thống
Đã từ chối 1 lần         → shouldShowRequestPermissionRationale() == true
                            nên giải thích lý do rồi mới hỏi lại
Từ chối vĩnh viễn        → launch() KHÔNG hiện gì cả, callback trả false ngay
   (từ chối 2 lần)          phải hướng dẫn người dùng vào Cài đặt thủ công
```

Đưa sang Cài đặt của app:

```kotlin
startActivity(
    Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null)
    )
)
```

## 4. Quyền Bluetooth — chia đôi ở API 31

Bluetooth là trường hợp phức tạp nhất trong dự án vì bộ quyền đổi hoàn toàn ở Android 12.

| API | Quyền cần | Loại |
|---|---|---|
| ≤ 30 | `BLUETOOTH`, `BLUETOOTH_ADMIN` + `ACCESS_FINE_LOCATION` (để scan) | normal + dangerous |
| ≥ 31 | `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` | **dangerous** |

Manifest hiện khai báo cả bốn nhưng không giới hạn theo API. Nên thêm `maxSdkVersion` để không xin
thừa trên máy mới:

```xml
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"
    android:usesPermissionFlags="neverForLocation" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
```

`neverForLocation` nói rõ app không dùng Bluetooth để suy ra vị trí → Play Store không đòi khai báo
quyền vị trí.

Sửa `BluetoothHelper` cho đúng thay vì `@SuppressLint`:

```kotlin
fun getPairedDevicesNames(): List<String> {
    if (!hasConnectPermission()) return emptyList()
    return try {
        bluetoothAdapter?.bondedDevices?.map { it.name } ?: emptyList()
    } catch (e: SecurityException) {
        emptyList()
    }
}

private fun hasConnectPermission(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
```

> `@SuppressLint("MissingPermission")` chỉ nên dùng khi **đã** kiểm tra quyền ở nơi khác mà lint
> không nhận ra. Dùng nó để làm im cảnh báo mà không kiểm tra gì là che giấu crash.

## 5. `SCHEDULE_EXACT_ALARM` — quyền đặc biệt (đã xử lý)

Từ Android 12, báo thức chính xác cần quyền mà **người dùng phải tự bật trong Cài đặt** — không có
dialog nào xin được. Từ Android 13 quyền này còn bị thu hồi mặc định với đa số app.

`AlarmHelper` đã xử lý đúng: kiểm tra trước, hạ cấp nếu không có quyền, và cung cấp đường dẫn sang
Cài đặt.

```kotlin
fun setExactAlarm(timeInMillis: Long): Boolean {
    return if (canScheduleExactAlarms()) {
        alarmManager.setExactAndAllowWhileIdle(RTC_WAKEUP, timeInMillis, pendingIntent)
        true
    } else {
        alarmManager.setAndAllowWhileIdle(RTC_WAKEUP, timeInMillis, pendingIntent)  // hạ cấp
        false
    }
}

fun canScheduleExactAlarms(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alarmManager.canScheduleExactAlarms()
    else true
```

Chỗ gọi xử lý kết quả:

```kotlin
if (!alarmHelper.setExactAlarm(time)) {
    // Đã đặt được báo thức nhưng không chính xác tuyệt đối.
    // Nếu tính năng thực sự cần chính xác thì mời người dùng cấp quyền:
    alarmHelper.requestExactAlarmPermission()
}
```

**Lưu ý về Play Store:** `SCHEDULE_EXACT_ALARM` bị Google kiểm duyệt gắt — chỉ app đồng hồ báo
thức, lịch, hoặc app có lý do rõ ràng mới được duyệt. Nếu độ chính xác ±vài phút là chấp nhận được
thì dùng `setAndAllowWhileIdle` hoặc WorkManager và bỏ hẳn quyền này.

## 6. Lint `MissingPermission`

`lint.xml` để `MissingPermission` ở mức **`error`**, tức là gọi API cần quyền mà không kiểm tra
trước sẽ **fail build**:

```xml
<issue id="MissingPermission" severity="error" />
```

Đây là hàng rào tốt — nhưng chỉ khi không bị vô hiệu hoá bằng `@SuppressLint`. Hiện `BluetoothHelper`
đang dùng đúng cách đó để đi vòng qua lint.

## 7. Việc cần làm

Theo thứ tự ưu tiên:

1. **`POST_NOTIFICATIONS`** — xin trước lần đầu hiện thông báo. Không có nó thì `NotificationHelper`
   và `AlarmReceiver` im lặng hoàn toàn trên Android 13+.
2. **`BLUETOOTH_CONNECT`/`BLUETOOTH_SCAN`** — xin trước khi gọi `BluetoothHelper`, và bỏ
   `@SuppressLint`.
3. **SMS / phone state** — cân nhắc **bỏ hẳn** các quyền này nếu `SystemEventReceiver` chỉ để ghi
   log. Quyền SMS khiến app bị Google Play kiểm duyệt rất chặt và có thể bị từ chối nếu không phải
   app nhắn tin mặc định. Đây là quyền đắt nhất trong danh sách so với giá trị nó đang mang lại.

## Xem thêm

- [BroadcastReceiver](broadcast-receiver.md) — vì sao receiver SMS chưa chạy
- [Vòng đời](vong-doi.md)
