# BroadcastReceiver

`BroadcastReceiver` là thành phần nhận **sự kiện phát tán** — từ hệ thống (có SMS, pin yếu, khởi
động xong) hoặc từ chính app (báo thức đến giờ).

## 1. Hai cách đăng ký

| | Static (manifest) | Dynamic (code) |
|---|---|---|
| Khai báo | `<receiver>` trong `AndroidManifest.xml` | `context.registerReceiver(...)` |
| Hoạt động khi app đã tắt | Có | Không |
| Bị giới hạn từ Android 8 | **Có, rất nặng** | Không |
| Phải tự huỷ đăng ký | Không | **Có** — quên là rò rỉ |
| Dùng cho | Sự kiện phải nhận kể cả khi app không chạy | Sự kiện chỉ quan tâm khi UI đang hiện |

## 2. Giới hạn từ Android 8 (API 26)

Đây là thứ hay làm receiver "không chạy mà không hiểu vì sao".

Từ API 26, **receiver khai báo static trong manifest không còn nhận được phần lớn implicit
broadcast** của hệ thống. Google làm vậy vì mỗi lần phát tán, hệ thống phải đánh thức hàng loạt app
→ tốn pin.

Vẫn nhận được (danh sách ngoại lệ):
- `BOOT_COMPLETED`
- `SMS_RECEIVED` / `SMS_DELIVER`
- `ACTION_LOCALE_CHANGED`
- `ACTION_MY_PACKAGE_REPLACED`
- Explicit broadcast (chỉ đích danh package của bạn) — **luôn nhận được**

Không còn nhận được qua manifest: `CONNECTIVITY_CHANGE`, `ACTION_POWER_CONNECTED`,
`NEW_PICTURE`... Muốn theo dõi mạng thì dùng `ConnectivityManager.NetworkCallback` — đúng như
`util/NetworkMonitor` đang làm.

## 3. Trong dự án

### 3.1. `AlarmReceiver` — explicit broadcast, hoạt động tốt

```kotlin
// receiver/AlarmReceiver.kt
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        NotificationHelper(context).showNotification("Báo thức", "Đã đến giờ hẹn!")
    }
}
```

```xml
<receiver android:name=".receiver.AlarmReceiver" android:exported="false" />
```

`exported="false"` là đúng: chỉ `AlarmHelper` trong chính app này gửi tới nó, không app nào khác
được phép. Vì là explicit broadcast (`Intent(context, AlarmReceiver::class.java)`) nên không dính
giới hạn API 26.

### 3.2. `SystemEventReceiver` — đang nghe SMS và cuộc gọi

```kotlin
// receiver/SystemEventReceiver.kt
class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            TelephonyManager.ACTION_PHONE_STATE_CHANGED -> {
                val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
                Log.d("SystemEventReceiver", "Trạng thái cuộc gọi: $state")
            }
            Telephony.Sms.Intents.SMS_RECEIVED_ACTION -> {
                Log.d("SystemEventReceiver", "Đã nhận tin nhắn mới!")
            }
        }
    }
}
```

Hiện tại nó **chỉ ghi log**, chưa làm gì thêm.

Ba điểm cần biết về receiver này:

**a) `SMS_RECEIVED` chạy được, `PHONE_STATE` thì không chắc.** `SMS_RECEIVED` nằm trong danh sách
ngoại lệ nên manifest-declared vẫn nhận. `PHONE_STATE` thì tuỳ phiên bản và OEM — cách đáng tin cậy
hơn là `TelephonyCallback` (API 31+) / `PhoneStateListener` đăng ký động.

**b) Cần quyền runtime mà app chưa xin.** Manifest có khai báo:

```xml
<uses-permission android:name="android.permission.RECEIVE_SMS" />
<uses-permission android:name="android.permission.READ_PHONE_STATE" />
```

nhưng đây là **dangerous permission** — khai báo thôi không đủ, phải xin lúc chạy. Hiện chưa có chỗ
nào trong code xin quyền, nên trên thiết bị thật receiver sẽ không nhận được gì. Xem
[Permissions](permissions.md).

**c) `exported="true"` là quá rộng.**

```xml
<receiver android:name=".receiver.SystemEventReceiver" android:exported="true">
```

`exported="true"` nghĩa là **app khác cũng gửi broadcast giả tới đây được**. Với receiver nhận
broadcast hệ thống thì đúng là cần `true`, nhưng phải phòng thủ: đừng tin dữ liệu trong `intent`,
và cân nhắc thêm `android:permission` để chỉ hệ thống mới gửi được:

```xml
<receiver
    android:name=".receiver.SystemEventReceiver"
    android:exported="true"
    android:permission="android.permission.BROADCAST_SMS">
```

## 4. Quy tắc viết `onReceive`

> **`onReceive()` chạy trên main thread và chỉ có ~10 giây.** Quá thời gian là ANR.

Cấm trong `onReceive`:
- Gọi mạng, truy vấn database, đọc/ghi file
- `Thread.sleep`, chờ đồng bộ
- Bắt đầu coroutine rồi để receiver kết thúc (process có thể bị giết ngay sau đó)

Việc nặng thì bàn giao cho WorkManager:

```kotlin
override fun onReceive(context: Context, intent: Intent) {
    val work = OneTimeWorkRequestBuilder<SyncWorker>().build()
    WorkManager.getInstance(context).enqueue(work)   // trả về ngay
}
```

Cần chạy async ngắn ngay trong receiver thì dùng `goAsync()`:

```kotlin
override fun onReceive(context: Context, intent: Intent) {
    val pending = goAsync()                     // xin thêm thời gian (~30s)
    CoroutineScope(Dispatchers.IO).launch {
        try { doWork() } finally { pending.finish() }   // BẮT BUỘC gọi finish()
    }
}
```

Quên `pending.finish()` là process bị treo cho tới khi hệ thống cưỡng chế giết.

## 5. Đăng ký động — nhớ huỷ

```kotlin
class SomeActivity : AppCompatActivity() {
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { /* ... */ }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_LOW),
            ContextCompat.RECEIVER_NOT_EXPORTED,     // BẮT BUỘC từ API 34
        )
    }

    override fun onStop() {
        unregisterReceiver(receiver)     // cặp đối xứng với onStart
        super.onStop()
    }
}
```

Từ **Android 14 (API 34)**, mọi receiver đăng ký động phải khai báo rõ `RECEIVER_EXPORTED` hoặc
`RECEIVER_NOT_EXPORTED`, nếu không app sẽ crash. Dùng `ContextCompat.registerReceiver` để tự xử lý
tương thích ngược. Dự án có `targetSdk 36` nên quy định này áp dụng đầy đủ.

## 6. Khi nào KHÔNG nên dùng BroadcastReceiver

| Nhu cầu | Dùng cái này thay thế |
|---|---|
| Theo dõi kết nối mạng | `ConnectivityManager.NetworkCallback` → `util/NetworkMonitor` |
| Chạy việc nền có điều kiện (có mạng, đang sạc) | `WorkManager` |
| Giao tiếp giữa các thành phần **trong** app | `StateFlow` / `SharedFlow` trong repository singleton |
| Việc định kỳ | `WorkManager` (`PeriodicWorkRequest`), không phải `AlarmManager` |

`LocalBroadcastManager` đã **deprecated** — đừng dùng cho giao tiếp nội bộ nữa.

## Xem thêm

- [Permissions](permissions.md) — vì sao receiver SMS chưa chạy được
- [Vòng đời](vong-doi.md) — cặp đăng ký/huỷ đăng ký
