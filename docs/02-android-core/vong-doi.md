# Vòng đời Activity & quản lý state

## 1. Sơ đồ vòng đời

```
        onCreate()          ← tạo view, khôi phục state, đăng ký observer
            ↓
        onStart()           ← màn hình bắt đầu hiện, chưa tương tác được
            ↓
        onResume()          ← đang ở foreground, người dùng tương tác được
            ↓
      ┌─ ĐANG CHẠY ─┐
      ↓             ↑
   onPause()     onResume()      ← bị che một phần (dialog, chia đôi màn hình)
      ↓             ↑
   onStop()      onRestart()     ← bị che hoàn toàn (chuyển app, khoá màn hình)
      ↓
  onDestroy()                    ← Activity kết thúc HOẶC bị huỷ do đổi cấu hình
```

## 2. Làm gì ở callback nào

| Callback | Nên làm | Ví dụ trong dự án |
|---|---|---|
| `onCreate` | Inflate binding, tạo ViewModel, observe LiveData, đăng ký listener | `LoginActivity.onCreate` |
| `onStart` | Bắt đầu thứ cần UI đang hiện (định vị, camera) | — |
| `onResume` | Nối lại animation, sensor | — |
| `onPause` | Dừng thứ tốn pin. Chạy **rất nhanh** — không I/O ở đây | — |
| `onStop` | Lưu dữ liệu, huỷ đăng ký receiver nặng | — |
| `onDestroy` | Giải phóng tài nguyên cuối cùng | `LoginActivity.onDestroy` → `networkMonitor.stopMonitoring()` |

**Nguyên tắc đối xứng:** cái gì đăng ký ở `onCreate` thì huỷ ở `onDestroy`, đăng ký ở `onStart` thì
huỷ ở `onStop`. Lệch cặp là rò rỉ.

Ví dụ thật trong dự án — `NetworkMonitor` đăng ký callback vào `ConnectivityManager`:

```kotlin
// LoginActivity.kt
override fun onCreate(savedInstanceState: Bundle?) {
    networkMonitor = NetworkMonitor(this)
    networkMonitor.startMonitoring()          // đăng ký
    ...
}

override fun onDestroy() {
    networkMonitor.stopMonitoring()           // huỷ đăng ký — cặp đối xứng
    super.onDestroy()
}
```

Thiếu `stopMonitoring()` thì callback sống lâu hơn Activity và giữ tham chiếu tới nó.

## 3. Ba kiểu "chết" khác nhau

Đây là chỗ hay nhầm nhất. `onDestroy()` được gọi trong cả ba trường hợp nhưng hệ quả hoàn toàn khác:

| Tình huống | ViewModel | `savedInstanceState` | Biến static / singleton |
|---|---|---|---|
| **Đổi cấu hình** (xoay màn, đổi theme, đổi ngôn ngữ) | **Sống** | Còn | Còn |
| **Người dùng bấm Back / `finish()`** | Chết | Mất | Còn |
| **Process death** (hệ thống thu hồi RAM khi app ở background) | **Chết** | **Còn** | **Mất** |

Rút ra:

- **ViewModel cứu được xoay màn hình, KHÔNG cứu được process death.**
- **`savedInstanceState` cứu được process death, nhưng chỉ chứa được ít dữ liệu** (`Bundle` bị giới
  hạn ~500KB, vượt là `TransactionTooLargeException`).
- Biến toàn cục/singleton mất trắng khi process chết — đây là lý do cache chỉ-trong-RAM của
  `LoginRepository` không đáng tin (xem
  [Repository Pattern §2.1](../01-kien-truc/repository-pattern.md)).

### Cách kiểm thử process death

Không cần chờ hệ thống tự làm:

```powershell
# Đưa app xuống background trước, rồi:
adb shell am kill com.ntp.application_ai_assisstant
```

Mở lại app từ recents — nếu màn hình trắng, crash, hoặc mất dữ liệu thì đang thiếu xử lý.

Hoặc bật **Developer options → Don't keep activities** để tái hiện liên tục.

## 4. SavedStateHandle

Kết hợp ưu điểm của cả ViewModel lẫn `savedInstanceState`:

```kotlin
class DiscoveryViewModel(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // sống qua cả xoay màn hình LẪN process death
    val query: StateFlow<String> = savedStateHandle.getStateFlow("query", "")

    fun onQueryChanged(value: String) {
        savedStateHandle["query"] = value
    }
}
```

Chỉ lưu ở đây những gì **không tái tạo được**: từ khoá tìm kiếm, id item đang chọn, bước đang ở
trong form nhiều bước. Không lưu danh sách tải từ mạng — thứ đó tải lại được.

Dự án đã có `lifecycle-viewmodel-ktx` nên dùng được ngay; chỉ cần đổi factory sang
`AbstractSavedStateViewModelFactory` hoặc dùng `viewModels { ... }` với `CreationExtras`.

## 5. Vòng đời trong bối cảnh router của dự án

Vì các tab dùng `FLAG_ACTIVITY_REORDER_TO_FRONT` chứ không `finish()`
(xem [Navigation](navigation.md)), callback được gọi khác với trực giác:

```
Đang ở Discovery, bấm tab Schedule (lần đầu):
    Discovery.onPause → Schedule.onCreate → Schedule.onStart → Schedule.onResume
                      → Discovery.onStop
    Discovery KHÔNG bị onDestroy → state còn nguyên

Bấm lại tab Discovery:
    Schedule.onPause → Discovery.onRestart → Discovery.onStart → Discovery.onResume
                     → Schedule.onStop
    KHÔNG có onCreate → không tạo lại view, không bind lại dữ liệu
```

Hệ quả thực tế: **đừng đặt logic tải dữ liệu chỉ trong `onCreate`** nếu muốn dữ liệu làm mới mỗi
lần quay lại tab. Dùng `onStart`/`onResume`, hoặc tốt hơn là để ViewModel expose một `Flow` và
observe bằng `repeatOnLifecycle(STARTED)` — lúc đó việc làm mới tự động khớp với vòng đời.

## 6. Lifecycle-aware: cách làm đúng

Thay vì tự gọi start/stop ở các callback, cho component tự lắng nghe vòng đời:

```kotlin
class NetworkMonitor(context: Context) : DefaultLifecycleObserver {
    override fun onStart(owner: LifecycleOwner) = startMonitoring()
    override fun onStop(owner: LifecycleOwner)  = stopMonitoring()
}

// Trong Activity — một dòng, không thể quên huỷ
lifecycle.addObserver(networkMonitor)
```

`NetworkMonitor` hiện tại **chưa** làm vậy (phải tự gọi `startMonitoring()`/`stopMonitoring()`).
Đây là hướng cải thiện nếu monitor được dùng ở nhiều màn hình — càng nhiều chỗ gọi thủ công thì
càng dễ quên một chỗ.

## 7. Bẫy thường gặp

**a) Giữ Activity trong biến static** → lint `StaticFieldLeak` (đang ở mức `error`):

```kotlin
companion object { var instance: LoginActivity? = null }   // ✗ rò rỉ cả Activity
```

**b) Handler với inner class không static** → lint `HandlerLeak` (mức `error`). Message còn trong
queue sẽ giữ Activity sống.

**c) Coroutine không gắn scope đúng:**

```kotlin
GlobalScope.launch { ... }        // ✗ không bao giờ bị huỷ
lifecycleScope.launch { ... }     // ✓ huỷ khi Activity destroy
viewModelScope.launch { ... }     // ✓ huỷ khi ViewModel clear
```

**d) `collect` Flow mà không gắn với vòng đời** — xem
[MVVM §3](../01-kien-truc/mvvm.md#3-livedata-hay-stateflow).

## Xem thêm

- [Navigation](navigation.md)
- [MVVM](../01-kien-truc/mvvm.md)
