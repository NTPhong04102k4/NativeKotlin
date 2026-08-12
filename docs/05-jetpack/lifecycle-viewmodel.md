# Lifecycle & ViewModel

Nhóm `androidx.lifecycle` là thư viện Jetpack quan trọng nhất với dự án này — nó là nền của cả
MVVM lẫn việc quản lý tài nguyên theo vòng đời.

## 1. Các thành phần

| Thành phần | Việc | Dùng trong dự án |
|---|---|---|
| `ViewModel` | Giữ state, sống qua đổi cấu hình | ✅ `LoginViewModel` |
| `LiveData` | Luồng dữ liệu lifecycle-aware | ✅ `loginFormState`, `loginResult` |
| `LifecycleOwner` | Activity/Fragment có vòng đời | ✅ mọi Activity |
| `DefaultLifecycleObserver` | Component tự lắng nghe vòng đời | ❌ *chưa dùng* |
| `viewModelScope` | CoroutineScope gắn với ViewModel | ❌ *chưa dùng* |
| `lifecycleScope` | CoroutineScope gắn với Activity | ✅ `LoginActivity` |
| `repeatOnLifecycle` | Collect Flow đúng vòng đời | ❌ *chưa dùng — nên dùng* |
| `SavedStateHandle` | State sống qua process death | ❌ *chưa dùng* |

## 2. ViewModel

### 2.1. Vì sao sống sót qua xoay màn hình

`ViewModel` được lưu trong `ViewModelStore` của `ComponentActivity`. Khi đổi cấu hình, Activity bị
huỷ nhưng `ViewModelStore` được chuyển sang instance mới. Chỉ khi Activity `finish()` thật sự thì
`onCleared()` mới được gọi.

```
Xoay màn hình:
  Activity#1.onDestroy()  →  Activity#2.onCreate()
                ViewModel giữ nguyên, KHÔNG onCleared()

Bấm Back:
  Activity.onDestroy()  →  ViewModel.onCleared()
```

**Nhưng ViewModel không cứu được process death** — xem
[Vòng đời §3](../02-android-core/vong-doi.md#3-ba-kiểu-chết-khác-nhau).

### 2.2. Ba cách lấy ViewModel

```kotlin
// a) Cách dự án đang dùng — tường minh, cần factory
loginViewModel = ViewModelProvider(this, LoginViewModelFactory())[LoginViewModel::class.java]

// b) Delegate của activity-ktx — gọn hơn, cùng kết quả
private val loginViewModel: LoginViewModel by viewModels { LoginViewModelFactory() }

// c) Không cần factory nếu constructor rỗng
private val viewModel: SomeViewModel by viewModels()
```

Cách (b) đọc dễ hơn và **lười khởi tạo** (chỉ tạo khi lần đầu truy cập). Dự án đã có
`activity-ktx` nên dùng được ngay.

### 2.3. `onCleared` — dọn dẹp

```kotlin
class SomeViewModel : ViewModel() {
    override fun onCleared() {
        super.onCleared()
        // huỷ listener, đóng kết nối
        // KHÔNG cần huỷ coroutine của viewModelScope — tự huỷ
    }
}
```

### 2.4. Luật cấm

```kotlin
class BadViewModel(
    private val activity: Activity,      // ✗ rò rỉ Activity
    private val context: Context,        // ✗ nếu là Activity context
    private val binding: ActivityXBinding // ✗ giữ cả cây View
) : ViewModel()
```

Cần `Context` thì dùng `AndroidViewModel(application)` — chỉ có `Application` context, sống cùng
process nên không rò rỉ.

Lint `StaticFieldLeak` ở mức `error` sẽ bắt phần lớn các trường hợp này.

## 3. `viewModelScope` — điều dự án còn thiếu

Hiện `LoginViewModel.login()` chạy **đồng bộ**:

```kotlin
fun login(username: String, password: String) {
    val result = loginRepository.login(username, password)   // chặn main thread
    ...
}
```

Chạy được vì `LoginDataSource` là stub trả về ngay. Thay bằng network thật là **ANR**.

Cách đúng:

```kotlin
fun login(username: String, password: String) {
    viewModelScope.launch {
        _uiState.value = _uiState.value.copy(isLoading = true)

        when (val result = loginRepository.login(username, password)) {   // suspend
            is Result.Success -> _uiState.value = LoginUiState(
                loggedIn = true,
                user = LoggedInUserView(result.data.displayName),
            )
            is Result.Error -> _uiState.value = LoginUiState(error = R.string.login_failed)
        }
    }
}
```

`viewModelScope` tự huỷ mọi coroutine khi `onCleared()` — không bao giờ có coroutine mồ côi cập
nhật state của ViewModel đã chết.

## 4. LiveData

### 4.1. Cách dùng chuẩn (dự án làm đúng)

```kotlin
private val _loginResult = MutableLiveData<LoginResult>()   // mutable, private
val loginResult: LiveData<LoginResult> = _loginResult        // immutable, public
```

### 4.2. `setValue` vs `postValue`

```kotlin
_loginResult.value = x        // CHỈ từ main thread. Sai thread -> crash
_loginResult.postValue(x)     // từ thread nào cũng được, cập nhật bất đồng bộ
```

Bẫy: `postValue` gọi liên tiếp nhiều lần thì **chỉ giá trị cuối cùng** đến được observer.

### 4.3. Biến đổi

```kotlin
val displayName: LiveData<String> = loginResult.map { it.success?.displayName ?: "" }

val userDetail: LiveData<Detail> = userId.switchMap { id -> repository.observeDetail(id) }
```

### 4.4. Bẫy sự kiện một lần

LiveData giữ giá trị cuối. Xoay màn hình → observer chạy lại → **Toast/điều hướng lặp lại**.

Đây là rủi ro có thật với `LoginActivity`: nếu `loginResult` phát ra `success` rồi Activity bị tạo
lại, observer sẽ điều hướng lần nữa.

Cách xử lý — bọc trong `Event`:

```kotlin
class Event<out T>(private val content: T) {
    private var handled = false
    fun getContentIfNotHandled(): T? = if (handled) null else { handled = true; content }
}

// ViewModel
private val _navigateHome = MutableLiveData<Event<LoggedInUserView>>()
val navigateHome: LiveData<Event<LoggedInUserView>> = _navigateHome

// Activity
viewModel.navigateHome.observe(this) { event ->
    event.getContentIfNotHandled()?.let { goToHome(it) }
}
```

Với Flow thì dùng `SharedFlow(replay = 0)` — không giữ lại giá trị nên không có vấn đề này.

## 5. StateFlow / SharedFlow

| | `StateFlow` | `SharedFlow` |
|---|---|---|
| Giá trị khởi tạo | Bắt buộc | Không |
| Giữ giá trị cuối | Có | Tuỳ `replay` |
| Bỏ giá trị trùng | Có (`distinctUntilChanged`) | Không |
| Dùng cho | **State** (danh sách, isLoading) | **Sự kiện** (toast, điều hướng) |

`util/NetworkMonitor` dùng `StateFlow` — đúng, vì "có mạng hay không" là state.

### Collect đúng cách

```kotlin
// ✗ Cách hiện tại trong LoginActivity: vẫn chạy khi app xuống background
lifecycleScope.launch {
    networkMonitor.isConnected.collect { ... }
}

// ✓ Tự dừng ở STOPPED, tự chạy lại ở STARTED
lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        networkMonitor.isConnected.collect { ... }
    }
}
```

`repeatOnLifecycle` nằm trong `lifecycle-runtime-ktx`, **đã có** trong version catalog và
`app/build.gradle.kts` — dùng được ngay, chỉ cần import:

```kotlin
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
```

## 6. `DefaultLifecycleObserver`

Thay vì Activity phải nhớ gọi start/stop, cho component tự lo:

```kotlin
class NetworkMonitor(context: Context) : DefaultLifecycleObserver {

    override fun onStart(owner: LifecycleOwner) { startMonitoring() }
    override fun onStop(owner: LifecycleOwner)  { stopMonitoring() }

    // ... phần còn lại giữ nguyên
}
```

```kotlin
// LoginActivity — thay 2 chỗ gọi thủ công bằng 1 dòng
lifecycle.addObserver(networkMonitor)
```

Lợi ích: không thể quên `stopMonitoring()`. Hiện `LoginActivity` phải tự gọi trong `onDestroy()` —
màn hình thứ hai dùng `NetworkMonitor` mà quên là rò rỉ ngay.

## 7. ProcessLifecycleOwner

Theo dõi vòng đời của **cả app** thay vì từng Activity:

```kotlin
ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
    override fun onStart(owner: LifecycleOwner) { /* app vào foreground */ }
    override fun onStop(owner: LifecycleOwner)  { /* app xuống background */ }
})
```

Hữu ích cho: khoá app bằng sinh trắc học khi quay lại, dừng đồng bộ khi ở nền, ghi nhận phiên
sử dụng.

Cần `androidx.lifecycle:lifecycle-process`.

## 8. Việc nên làm với dự án

Theo thứ tự giá trị/công sức:

1. Đổi `ViewModelProvider(...)` sang `by viewModels { ... }` — gọn hơn, không rủi ro
2. Chuyển `LoginViewModel.login()` sang `viewModelScope` + `suspend` repository — **bắt buộc** trước
   khi nối API thật
3. Dùng `repeatOnLifecycle(STARTED)` cho `NetworkMonitor`
4. Cho `NetworkMonitor` implement `DefaultLifecycleObserver`
5. Gộp `LoginFormState` + `LoginResult` thành một `LoginUiState`
6. Bọc sự kiện điều hướng trong `Event` hoặc `SharedFlow`

## Xem thêm

- [MVVM](../01-kien-truc/mvvm.md)
- [Vòng đời](../02-android-core/vong-doi.md)
- [Coroutines & Flow](../06-kotlin/coroutines-flow.md)
