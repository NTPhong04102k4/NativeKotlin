# Bluetooth

Dự án có `util/BluetoothHelper.kt` nhưng **chưa nơi nào gọi tới**, và quyền runtime chưa được xin.

## 1. Hiện trạng

```kotlin
// util/BluetoothHelper.kt
class BluetoothHelper(context: Context) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled ?: false

    @SuppressLint("MissingPermission")
    fun getPairedDevicesNames(): List<String> =
        bluetoothAdapter?.bondedDevices?.map { it.name } ?: emptyList()
}
```

> ⚠️ `@SuppressLint("MissingPermission")` chỉ **tắt cảnh báo lint**, không cấp quyền. Trên Android
> 12+ mà chưa có `BLUETOOTH_CONNECT`, `bondedDevices` ném `SecurityException` và app crash.

## 2. Ba loại Bluetooth

| Loại | Dùng cho | API |
|---|---|---|
| **Classic** | Tai nghe, loa, truyền file, SPP | `BluetoothAdapter`, `BluetoothSocket` |
| **BLE** (Low Energy) | Cảm biến, vòng đeo, thiết bị IoT | `BluetoothLeScanner`, `BluetoothGatt` |
| **BLE Peripheral** | Máy đóng vai thiết bị | `BluetoothLeAdvertiser` |

Code hiện tại là **Classic** (`bondedDevices` = danh sách đã ghép đôi). Nếu mục tiêu là điều khiển
thiết bị IoT thì gần như chắc chắn cần **BLE** — xem [Tuya IoT](tuya-iot.md).

## 3. Quyền — chia đôi ở Android 12

Đây là nguồn gốc của hầu hết lỗi Bluetooth.

| API | Quyền | Loại |
|---|---|---|
| ≤ 30 | `BLUETOOTH`, `BLUETOOTH_ADMIN` | normal (tự cấp) |
| ≤ 30 | `ACCESS_FINE_LOCATION` | **dangerous** — bắt buộc để quét BLE |
| ≥ 31 | `BLUETOOTH_SCAN` | **dangerous** |
| ≥ 31 | `BLUETOOTH_CONNECT` | **dangerous** |
| ≥ 31 | `BLUETOOTH_ADVERTISE` | dangerous (chỉ khi làm peripheral) |

Manifest hiện khai báo cả bốn nhưng không giới hạn theo API:

```xml
<!-- Nên sửa thành -->
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"
    android:usesPermissionFlags="neverForLocation" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

<!-- Chỉ thêm nếu thực sự quét BLE trên API ≤ 30 -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"
    android:maxSdkVersion="30" />
```

`neverForLocation` khai báo rằng app không suy ra vị trí từ kết quả quét → không phải xin quyền
vị trí, và Play Store không hỏi thêm.

## 4. `BluetoothHelper` viết lại cho đúng

```kotlin
class BluetoothHelper(private val context: Context) {

    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter? = bluetoothManager?.adapter

    /** Máy có phần cứng Bluetooth không. */
    fun isSupported(): Boolean = adapter != null

    fun isEnabled(): Boolean = adapter?.isEnabled == true

    fun hasConnectPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    /**
     * Danh sách thiết bị đã ghép đôi. Trả về list rỗng nếu thiếu quyền hoặc Bluetooth tắt —
     * KHÔNG ném exception ra chỗ gọi.
     */
    fun getPairedDeviceNames(): List<String> {
        if (!isEnabled() || !hasConnectPermission()) return emptyList()
        return try {
            adapter?.bondedDevices?.mapNotNull { it.name } ?: emptyList()
        } catch (e: SecurityException) {
            emptyList()      // phòng thủ: quyền có thể bị thu hồi giữa chừng
        }
    }
}
```

Bốn thay đổi so với bản cũ:

1. `as?` thay `as` — máy không có Bluetooth thì `getSystemService` trả null, ép kiểu cứng là crash
2. Kiểm tra quyền thật thay vì `@SuppressLint`
3. Bắt `SecurityException` phòng khi quyền bị thu hồi trong lúc app chạy
4. `mapNotNull` — `device.name` có thể null

## 5. Xin quyền

```kotlin
private val requestBluetooth = registerForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
) { results ->
    if (results.values.all { it }) loadDevices() else showPermissionRationale()
}

private fun ensureBluetoothPermissions() {
    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    val missing = permissions.filter {
        ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
    }

    if (missing.isEmpty()) loadDevices() else requestBluetooth.launch(missing.toTypedArray())
}
```

Xem thêm [Permissions §3](../02-android-core/permissions.md#3-luồng-xin-quyền-runtime-chuẩn).

## 6. Bật Bluetooth

**Không** gọi `adapter.enable()` — hàm này đã deprecated từ API 33 và bị chặn. Phải hỏi người dùng:

```kotlin
private val enableBluetooth = registerForActivityResult(
    ActivityResultContracts.StartActivityForResult()
) { result ->
    if (result.resultCode == Activity.RESULT_OK) loadDevices()
}

if (!bluetoothHelper.isEnabled()) {
    enableBluetooth.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
}
```

## 7. BLE — quét và kết nối

```kotlin
private val scanner get() = adapter?.bluetoothLeScanner

private val scanCallback = object : ScanCallback() {
    override fun onScanResult(callbackType: Int, result: ScanResult) {
        // Gọi rất nhiều lần — lọc trùng theo địa chỉ MAC
        onDeviceFound(result.device)
    }
    override fun onScanFailed(errorCode: Int) { /* xử lý lỗi */ }
}

fun startScan(serviceUuid: UUID? = null) {
    if (!hasScanPermission()) return

    val filters = serviceUuid?.let {
        listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(it)).build())
    } ?: emptyList()

    val settings = ScanSettings.Builder()
        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
        .build()

    scanner?.startScan(filters, settings, scanCallback)
}

fun stopScan() { scanner?.stopScan(scanCallback) }
```

> ⚠️ **Quét BLE rất tốn pin.** Luôn `stopScan()` khi tìm thấy thiết bị hoặc sau timeout (10–30
> giây). Android còn giới hạn 5 lần start/stop trong 30 giây — vượt là bị chặn im lặng.

### Kết nối GATT

```kotlin
val gatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            gatt.discoverServices()      // BẮT BUỘC trước khi đọc/ghi
        }
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        val characteristic = gatt
            .getService(SERVICE_UUID)
            ?.getCharacteristic(CHAR_UUID) ?: return
        gatt.readCharacteristic(characteristic)
    }
})
```

Ba luật của BLE mà ai cũng vấp:

1. **Mỗi lần chỉ một thao tác GATT.** Gọi `readCharacteristic` trước khi `write` trước đó xong sẽ
   bị bỏ qua im lặng. Cần hàng đợi thao tác.
2. **Luôn gọi `gatt.close()`** khi ngắt kết nối, nếu không rò rỉ và hết slot kết nối.
3. **Callback chạy trên binder thread**, không phải main. Muốn cập nhật UI phải chuyển thread.

## 8. Bọc bằng Flow

Callback BLE hợp với `callbackFlow` — dọn dẹp gắn liền vòng đời:

```kotlin
fun scanDevices(): Flow<BluetoothDevice> = callbackFlow {
    val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            trySend(result.device)
        }
        override fun onScanFailed(errorCode: Int) {
            close(IllegalStateException("Quét thất bại: $errorCode"))
        }
    }

    scanner?.startScan(callback)
    awaitClose { scanner?.stopScan(callback) }    // không thể quên dừng quét
}
```

Dùng:

```kotlin
lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        bluetoothHelper.scanDevices()
            .distinctUntilChangedBy { it.address }
            .timeout(30.seconds)
            .catch { /* ... */ }
            .collect { device -> adapter.add(device) }
    }
}
```

Xem [Coroutines & Flow §7](../06-kotlin/coroutines-flow.md#7-callbackflow--bọc-api-callback).

## 9. Theo dõi trạng thái adapter

```kotlin
class BluetoothStateReceiver(private val onStateChanged: (Int) -> Unit) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
            onStateChanged(intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1))
        }
    }
}
```

Đăng ký **động** (không phải manifest) và nhớ `RECEIVER_NOT_EXPORTED` cho API 34+ — xem
[BroadcastReceiver §5](../02-android-core/broadcast-receiver.md#5-đăng-ký-động--nhớ-huỷ).

## 10. `uses-feature`

```xml
<uses-feature android:name="android.hardware.bluetooth" android:required="false" />
<uses-feature android:name="android.hardware.bluetooth_le" android:required="false" />
```

Manifest đã có dòng đầu với `required="false"` — đúng, vì app vẫn dùng được khi không có Bluetooth.
Đặt `required="true"` sẽ khiến Play Store ẩn app khỏi mọi thiết bị không có Bluetooth.

Thiếu dòng `bluetooth_le` nếu về sau dùng BLE.

## 11. Việc cần làm

- [ ] Bỏ `@SuppressLint("MissingPermission")`, kiểm tra quyền thật
- [ ] `as?` thay `as` khi lấy `BluetoothManager`
- [ ] Thêm `maxSdkVersion="30"` và `neverForLocation` vào manifest
- [ ] Viết luồng xin quyền runtime
- [ ] Quyết định Classic hay BLE — hiện code là Classic nhưng chưa rõ mục tiêu
- [ ] Nếu là IoT: cân nhắc dùng thẳng SDK của nhà sản xuất thay vì tự viết GATT

## Xem thêm

- [Permissions](../02-android-core/permissions.md)
- [Tuya IoT](tuya-iot.md) — SDK bọc sẵn BLE cho thiết bị Tuya
- [Coroutines & Flow](../06-kotlin/coroutines-flow.md)
