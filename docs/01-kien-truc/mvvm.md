# MVVM (Model – View – ViewModel)

MVVM là mô hình kiến trúc chính của dự án và cũng là mô hình Google khuyến nghị cho Android.

## 1. Ba vai trò

```
┌──────────┐   sự kiện người dùng   ┌─────────────┐   yêu cầu dữ liệu   ┌───────────┐
│   View   │ ─────────────────────► │  ViewModel  │ ──────────────────► │   Model   │
│ Activity │ ◄───────────────────── │             │ ◄────────────────── │Repository │
└──────────┘   luồng state (observe) └─────────────┘   dữ liệu           └───────────┘
```

| Vai trò | Là gì | Được phép | KHÔNG được phép |
|---|---|---|---|
| **View** | Activity / layout XML | Vẽ UI, bắt sự kiện, observe state | Chứa logic nghiệp vụ, gọi thẳng network/DB |
| **ViewModel** | Giữ và biến đổi state | Gọi Repository, validate, expose state | Giữ tham chiếu tới `Activity`, `Context`, `View` |
| **Model** | Repository + DataSource + entity | Lấy/ghi dữ liệu, cache | Biết gì về UI |

**Quy tắc sống còn:** ViewModel **không bao giờ** giữ `Activity`/`View`/`Context` của Activity.
ViewModel sống lâu hơn Activity (qua xoay màn hình) → giữ tham chiếu là rò rỉ bộ nhớ. Lint bật
`StaticFieldLeak` ở mức `error` chính là để chặn lỗi này.

Cần Context? Dùng `AndroidViewModel` (chỉ có `Application` context) hoặc truyền qua Repository.

## 2. Luồng Login trong dự án

Đây là lát cắt duy nhất đã wire đầy đủ. Đọc theo thứ tự này:

```
ui/login/LoginActivity.kt          View
   └─ LoginViewModelFactory.kt     tạo ViewModel có tham số constructor
        └─ LoginViewModel.kt       ViewModel
             └─ data/LoginRepository.kt    Model: cache + điều phối
                  └─ data/LoginDataSource.kt   Model: nguồn dữ liệu (đang là STUB)
```

### 2.1. ViewModel expose state, không expose hành động UI

```kotlin
// LoginViewModel.kt
private val _loginForm = MutableLiveData<LoginFormState>()   // mutable: nội bộ
val loginFormState: LiveData<LoginFormState> = _loginForm     // immutable: lộ ra ngoài

private val _loginResult = MutableLiveData<LoginResult>()
val loginResult: LiveData<LoginResult> = _loginResult
```

Cặp `_x` (private, mutable) / `x` (public, immutable) là quy ước bắt buộc: View chỉ được **đọc**
state, không được tự ý sửa.

### 2.2. UI model tách khỏi data model

Dự án có ba lớp riêng cho tầng UI:

| Lớp | Mục đích |
|---|---|
| `LoginFormState` | Form hợp lệ chưa, lỗi ở ô nào (`usernameError`, `passwordError`) |
| `LoginResult` | Kết quả đăng nhập: `success` hoặc `error` |
| `LoggedInUserView` | Chỉ chứa `displayName` — thứ UI thật sự cần |

So sánh với `data/model/LoggedInUser` (có cả `userId`): View không cần `userId` nên UI model không
mang theo. Đây là ranh giới đúng — đừng ném thẳng entity của tầng data lên UI.

### 2.3. Lỗi truyền bằng resource ID, không phải chuỗi

```kotlin
// LoginFormState giữ @StringRes Int, không giữ String
_loginForm.value = LoginFormState(usernameError = R.string.invalid_username)
```

Vì sao? ViewModel không có `Context` nên không gọi được `getString()`. Trả về ID rồi để Activity
dịch sang chuỗi — vừa đúng phân tầng, vừa tự động đúng ngôn ngữ khi đổi locale.

### 2.4. Factory: vì sao cần

`ViewModelProvider` mặc định chỉ tạo được ViewModel có constructor rỗng. `LoginViewModel` cần
`LoginRepository` nên phải có factory:

```kotlin
// LoginViewModelFactory.kt
override fun <T : ViewModel> create(modelClass: Class<T>): T {
    if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
        return LoginViewModel(LoginRepository(LoginDataSource())) as T
    }
    throw IllegalArgumentException("Unknown ViewModel class")
}
```

Đây là **dependency injection thủ công**. Dự án lớn hơn thì thay bằng Hilt/Koin, nhưng với một
ViewModel thì factory là đủ và không thêm phụ thuộc.

## 3. LiveData hay StateFlow?

Dự án đang dùng **cả hai**, mỗi thứ một chỗ:

| | LiveData | StateFlow |
|---|---|---|
| Dùng ở đâu trong dự án | `LoginViewModel` | `util/NetworkMonitor` |
| Lifecycle-aware | Có, tự động | Không — phải `repeatOnLifecycle` |
| Cần giá trị khởi tạo | Không | Có |
| Toán tử biến đổi | Ít (`map`, `switchMap`) | Đầy đủ bộ Flow |
| Thuộc về | `androidx.lifecycle` | Kotlin coroutines |

**Lưu ý quan trọng về cách dự án đang collect StateFlow:**

```kotlin
// LoginActivity.kt — cách hiện tại
lifecycleScope.launch {
    networkMonitor.isConnected.collect { ... }
}
```

`lifecycleScope.launch` chỉ huỷ khi Activity bị destroy, nên khi app xuống background thì coroutine
vẫn chạy. Cách chuẩn là:

```kotlin
lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        networkMonitor.isConnected.collect { ... }
    }
}
```

`repeatOnLifecycle` tự dừng collect khi xuống `STOPPED` và collect lại khi quay lại `STARTED`.
Với LiveData thì hành vi này là mặc định — đó là lợi thế chính của LiveData.

## 4. Còn thiếu so với chuẩn

Ghi lại để không nhầm tài liệu này với hiện trạng:

1. **`LoginDataSource.login()` là stub** — bỏ qua username/password, luôn trả `Result.Success` với
   user "Jane Doe" và UUID ngẫu nhiên. Chưa có network thật.
2. **Không có tính bất đồng bộ.** `LoginViewModel.login()` gọi repository **đồng bộ** trên main
   thread. Khi thay stub bằng API thật, phải chuyển sang `viewModelScope.launch { ... }` và cho
   DataSource thành `suspend fun`, nếu không sẽ ANR.
3. **Repository không phải singleton.** `LoginViewModelFactory` tạo `LoginRepository(LoginDataSource())`
   mới mỗi lần → cache `user` trong repository là cache rỗng, và `logout()` không xoá được gì cả.
   Muốn cache thật thì repository phải là singleton (`object`, hoặc giữ trong lớp `Application`,
   hoặc để DI container quản lý).
4. **Không có state Loading.** `LoginResult` chỉ có `success`/`error`; trạng thái đang tải đang được
   Activity tự bật/tắt `loading.visibility`. Chuẩn hơn là để ViewModel phát ra một state có đủ ba
   nhánh (`Loading` / `Success` / `Error`) để UI chỉ việc phản chiếu.
5. **Ba màn hình còn lại chưa có ViewModel.** Discovery / Schedule / Personal mới chỉ inflate
   binding và nối bottom navigation.

## 5. Khuôn mẫu khi thêm màn hình MVVM mới

```
ui/<tenman>/
├── <Ten>Activity.kt        View: inflate binding, observe state, gọi viewModel.xxx()
├── <Ten>ViewModel.kt       state + hành động
├── <Ten>ViewModelFactory.kt   nếu ViewModel có tham số constructor
└── <Ten>UiState.kt         data class mô tả TOÀN BỘ state của màn hình
data/
├── <Ten>Repository.kt
└── <Ten>DataSource.kt
```

Gợi ý: gộp state vào **một** `UiState` duy nhất thay vì nhiều LiveData rời rạc — tránh được cảnh UI
render ở trạng thái nửa vời khi các LiveData cập nhật lệch nhau.

```kotlin
data class DiscoveryUiState(
    val isLoading: Boolean = false,
    val items: List<DiscoveryItem> = emptyList(),
    @StringRes val error: Int? = null,
)
```

## Xem thêm

- [MVC · MVP · MVI](mvc-mvp-mvi.md) — vì sao chọn MVVM
- [Repository Pattern](repository-pattern.md) — tầng Model chi tiết
- [Vòng đời](../02-android-core/vong-doi.md) — ViewModel sống sót qua xoay màn hình như thế nào
