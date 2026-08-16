# Native Bridge

"Native bridge" là lớp trung gian cho code không-native (JavaScript, Dart, C/C++) gọi được API
Android, và ngược lại. Tuỳ ngữ cảnh, cụm từ này chỉ ba thứ khác nhau — trang này phân biệt cả ba.

> **Trạng thái:** dự án là Android thuần Kotlin, **không có bridge nào**. Trang này là tài liệu
> tham chiếu/định hướng.

## 1. Ba loại bridge

| Loại | Hai bên | Dùng khi |
|---|---|---|
| **JS ↔ Native** (WebView) | Kotlin ↔ JavaScript trong WebView | Nhúng web app vào app native |
| **Cross-platform bridge** | Kotlin ↔ React Native / Flutter | App viết bằng RN/Flutter cần tính năng native |
| **JNI / NDK** | Kotlin ↔ C/C++ | Xử lý ảnh/âm thanh, thư viện C có sẵn, thuật toán nặng |

## 2. JS ↔ Native qua WebView

Cách đơn giản nhất, và cũng là cách rủi ro nhất nếu làm sai. Chi tiết ở
[WebView §3](webview.md#3-javascript-bridge--cầu-nối-web--native).

Tóm lại:

```kotlin
class WebAppBridge(private val activity: Activity) {
    @JavascriptInterface
    fun openSchedule() {
        activity.runOnUiThread { AppRouter.openTab(activity, R.id.nav_schedule) }
    }
}
webView.addJavascriptInterface(WebAppBridge(this), "AndroidBridge")
```

> ⚠️ Mọi hàm `@JavascriptInterface` là bề mặt tấn công. Chỉ đăng ký khi tải domain trong allowlist.

Cách an toàn hơn cho dự án mới: **`WebMessagePort`** (API 23+), có kiểm tra origin:

```kotlin
val (portA, portB) = webView.createWebMessageChannel()
portA.setWebMessageCallback(object : WebMessagePort.WebMessageCallback() {
    override fun onMessage(port: WebMessagePort, message: WebMessage) {
        handle(message.data)
    }
})
webView.postWebMessage(
    WebMessage("init", arrayOf(portB)),
    Uri.parse("https://example.com"),     // chỉ origin này nhận được
)
```

## 3. Cross-platform bridge

### 3.1. React Native — Turbo Native Module

RN mới (0.68+) dùng **TurboModules** với codegen từ TypeScript spec, thay cho bridge cũ dựa trên
JSON. Nhanh hơn và có type safety.

```typescript
// NativeBiometric.ts — spec
import type { TurboModule } from 'react-native';
import { TurboModuleRegistry } from 'react-native';

export interface Spec extends TurboModule {
  isAvailable(): Promise<boolean>;
  authenticate(title: string, subtitle: string): Promise<boolean>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('NativeBiometric');
```

```kotlin
// Cài đặt phía Android — bọc lại BiometricHelper đã có
class NativeBiometricModule(
    reactContext: ReactApplicationContext,
) : NativeBiometricSpec(reactContext) {

    override fun isAvailable(promise: Promise) {
        val activity = currentActivity as? AppCompatActivity
            ?: return promise.reject("NO_ACTIVITY", "Không có Activity")
        promise.resolve(BiometricHelper(activity).isAvailable())
    }

    override fun authenticate(title: String, subtitle: String, promise: Promise) {
        val activity = currentActivity as? AppCompatActivity
            ?: return promise.reject("NO_ACTIVITY", "Không có Activity")

        BiometricHelper(activity).showBiometricPrompt(
            title = title,
            subtitle = subtitle,
            onSuccess = { promise.resolve(true) },
            onError = { message -> promise.reject("AUTH_FAILED", message) },
        )
    }
}
```

Ba luật của module RN:

1. **Mỗi promise chỉ resolve/reject đúng một lần.** Gọi hai lần → crash.
2. **`currentActivity` có thể null** (app ở background). Luôn kiểm tra.
3. **Kiểu dữ liệu bị giới hạn:** `String`, `Double`, `Boolean`, `ReadableMap`, `ReadableArray`.
   Không truyền được object Kotlin tuỳ ý.

Gửi sự kiện từ native lên JS:

```kotlin
reactApplicationContext
    .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
    .emit("onNetworkChanged", Arguments.createMap().apply { putBoolean("connected", true) })
```

Đây là cách đưa `NetworkMonitor` (`StateFlow<Boolean>`) lên JS.

### 3.2. Flutter — Platform Channel

```dart
// Dart
const channel = MethodChannel('com.ntp.aiassistant/biometric');

final available = await channel.invokeMethod<bool>('isAvailable') ?? false;
```

```kotlin
// Android
class MainActivity : FlutterActivity() {
    override fun configureFlutterEngine(engine: FlutterEngine) {
        super.configureFlutterEngine(engine)
        MethodChannel(engine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "isAvailable" -> result.success(BiometricHelper(this).isAvailable())
                    "authenticate" -> {
                        val title = call.argument<String>("title") ?: ""
                        BiometricHelper(this).showBiometricPrompt(
                            title = title,
                            subtitle = "",
                            onSuccess = { result.success(true) },
                            onError = { result.error("AUTH_FAILED", it, null) },
                        )
                    }
                    else -> result.notImplemented()
                }
            }
    }
    companion object { private const val CHANNEL = "com.ntp.aiassistant/biometric" }
}
```

Ba loại channel:

| Channel | Dùng cho |
|---|---|
| `MethodChannel` | Gọi hàm, trả một kết quả |
| `EventChannel` | Luồng sự kiện liên tục (đúng cho `NetworkMonitor`) |
| `BasicMessageChannel` | Truyền tin nhắn hai chiều |

`EventChannel` cho `NetworkMonitor`:

```kotlin
EventChannel(messenger, "network").setStreamHandler(object : EventChannel.StreamHandler {
    private var job: Job? = null

    override fun onListen(arguments: Any?, sink: EventChannel.EventSink) {
        job = lifecycleScope.launch {
            networkMonitor.isConnected.collect { sink.success(it) }
        }
    }

    override fun onCancel(arguments: Any?) { job?.cancel() }
})
```

### 3.3. Kotlin Multiplatform — không phải bridge

KMP **chia sẻ code Kotlin** giữa Android và iOS thay vì bắc cầu qua runtime khác. Không có bridge,
không có serialization, không có overhead.

```
commonMain/     logic nghiệp vụ, repository, ViewModel — chia sẻ
androidMain/    UI Android (XML/ViewBinding như hiện tại)
iosMain/        UI iOS (SwiftUI)
```

Với kiến trúc hiện tại của dự án (MVVM + Repository), `commonMain` sẽ chứa được
`LoginRepository`, `LoginDataSource`, `Result`, các model — chỉ tầng UI là riêng.

Đây là hướng đáng cân nhắc nhất nếu mục tiêu là làm cả iOS mà **không** muốn viết lại logic:
[WidgetKit](../04-app-widget/widgetkit-ios.md) mô tả phía UI iOS tương ứng.

## 4. JNI / NDK

Khi cần C/C++ (xử lý ảnh, mã hoá, mô hình AI on-device):

```kotlin
class NativeProcessor {
    external fun processImage(input: ByteArray, width: Int, height: Int): ByteArray

    companion object {
        init { System.loadLibrary("processor") }
    }
}
```

```cpp
// processor.cpp
extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_example_application_1ai_1assisstant_NativeProcessor_processImage(
    JNIEnv* env, jobject /* this */, jbyteArray input, jint width, jint height) {
    // ...
}
```

Chú ý tên hàm: dấu `_` trong package thành `_1`. Sai một ký tự là `UnsatisfiedLinkError`. Package
của dự án (`application_ai_assisstant`) có tới ba dấu `_` → rất dễ sai; dùng `RegisterNatives` để
tránh phụ thuộc vào quy tắc đặt tên.

Cần thêm `externalNativeBuild` + `CMakeLists.txt` vào `app/build.gradle.kts`. Điều này làm build
phức tạp lên đáng kể và ảnh hưởng tới configuration cache — chỉ làm khi thực sự cần.

## 5. Nguyên tắc thiết kế bridge tốt

Áp dụng cho cả ba loại:

**a) Bridge càng mỏng càng tốt.** Logic nghiệp vụ nằm ở một bên, bridge chỉ chuyển dữ liệu. Đừng
để logic bị chia đôi hai bên bridge — debug sẽ rất khổ.

**b) Bọc helper có sẵn, đừng viết lại.** Module RN/Flutter nên gọi `BiometricHelper`,
`BluetoothHelper`, `NetworkMonitor` đã có, không tự gọi `BiometricPrompt`.

**c) Lỗi phải đi qua bridge được.** Exception Kotlin không tự thành Error của JS. Định nghĩa mã lỗi
rõ ràng:

```kotlin
promise.reject("BIOMETRIC_NOT_ENROLLED", "Chưa đăng ký sinh trắc học")
```

**d) Cẩn thận thread.** Callback từ JS/Dart không chạy trên main thread. Đụng UI phải chuyển thread.

**e) Bridge có chi phí.** Mỗi lần qua bridge là một lần serialize. Đừng gọi bridge trong vòng lặp
hay mỗi frame — gộp lại thành một lần gọi.

## 6. Nếu dự án phải thêm bridge

Kiến trúc hiện tại thuận lợi: các helper trong `util/` đã là những lớp nhỏ, độc lập, không dính
UI — đúng thứ cần để bọc thành module.

```
util/BiometricHelper.kt   ──►  bọc thành 1 module   ──►  JS/Dart gọi
util/BluetoothHelper.kt   ──►  bọc thành 1 module
util/NetworkMonitor.kt    ──►  EventChannel / EventEmitter (vì là StateFlow)
util/NotificationHelper.kt ──► bọc thành 1 module
util/AppRouter.kt         ──►  KHÔNG bọc — điều hướng nên do phía cross-platform lo
```

`AppRouter` là ngoại lệ: nếu UI do RN/Flutter vẽ thì điều hướng phải nằm bên đó, và các Activity
riêng lẻ của dự án sẽ biến thành một Activity host duy nhất.

## Xem thêm

- [WebView](webview.md) — JS bridge
- [WidgetKit (iOS)](../04-app-widget/widgetkit-ios.md)
- [Biometric](biometric.md), [Bluetooth](bluetooth.md) — các helper đáng bọc
