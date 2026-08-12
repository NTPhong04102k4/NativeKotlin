# MVC · MVP · MVVM · MVI

Bốn mô hình đều nhằm một mục đích: **tách logic ra khỏi View**. Khác nhau ở chỗ ai giữ state và
giao tiếp theo chiều nào.

## 1. Bảng so sánh nhanh

| | MVC | MVP | MVVM | MVI |
|---|---|---|---|---|
| Thành phần trung gian | Controller | Presenter | ViewModel | Reducer + Store |
| Biết gì về View | Có | Có (qua interface) | **Không** | **Không** |
| Chiều giao tiếp | Hai chiều | Hai chiều | Một chiều (observe) | Một chiều nghiêm ngặt |
| State | Rải rác | Rải rác trong Presenter | Gom trong ViewModel | **Một state bất biến duy nhất** |
| Sống qua xoay màn hình | Không | Không (phải tự lưu) | **Có** | Có |
| Test độ khó | Khó | Dễ (mock View) | Dễ (không cần mock View) | Rất dễ (hàm thuần) |
| Boilerplate | Ít | Nhiều | Trung bình | Nhiều |

## 2. Từng mô hình

### 2.1. MVC — Model View Controller

Trên Android, MVC "nguyên bản" hầu như không tồn tại: Activity vừa là View vừa là Controller.
Kết quả là **Activity phình to** (God Activity) hàng nghìn dòng.

```kotlin
// Anti-pattern: mọi thứ nằm trong Activity
class LoginActivity : AppCompatActivity() {
    override fun onCreate(s: Bundle?) {
        btnLogin.setOnClickListener {
            if (edtUser.text.length < 6) {          // validate
                edtUser.error = "Quá ngắn"          // UI
                return@setOnClickListener
            }
            ApiClient.login(...) { result ->        // network
                database.save(result)               // persistence
                startActivity(...)                  // navigation
            }
        }
    }
}
```

Vấn đề: không test được (mọi thứ dính vào framework Android), không tái dùng, xoay màn hình là mất
sạch.

### 2.2. MVP — Model View Presenter

View định nghĩa một **interface**, Presenter gọi ngược lại qua interface đó.

```kotlin
interface LoginView {
    fun showLoading()
    fun showError(message: String)
    fun navigateToHome()
}

class LoginPresenter(private val view: LoginView, private val repo: LoginRepository) {
    fun login(user: String, pass: String) {
        view.showLoading()
        val result = repo.login(user, pass)
        when (result) {
            is Result.Success -> view.navigateToHome()
            is Result.Error   -> view.showError("Sai tài khoản")
        }
    }
}

class LoginActivity : AppCompatActivity(), LoginView { ... }
```

**Ưu:** test dễ (mock `LoginView`), tách bạch rõ.
**Nhược:** phải tự huỷ tham chiếu View trong `onDestroy()` (không thì rò rỉ), interface phình theo
số lượng trạng thái UI, và Presenter chết theo Activity nên xoay màn hình phải tự khôi phục.

### 2.3. MVVM — mô hình của dự án này

ViewModel **không biết View là ai**. Nó chỉ phát ra state; ai muốn thì observe.

```kotlin
// LoginViewModel.kt (code thật trong dự án)
private val _loginResult = MutableLiveData<LoginResult>()
val loginResult: LiveData<LoginResult> = _loginResult

fun login(username: String, password: String) {
    val result = loginRepository.login(username, password)
    _loginResult.value = if (result is Result.Success) {
        LoginResult(success = LoggedInUserView(result.data.displayName))
    } else {
        LoginResult(error = R.string.login_failed)
    }
}
```

**Ưu:** không cần interface View, `ViewModel` sống sót qua xoay màn hình, test không cần mock UI.
**Nhược:** dễ rơi vào cảnh có quá nhiều LiveData rời rạc → UI render ở trạng thái nửa vời.

Chi tiết: [MVVM](mvvm.md).

### 2.4. MVI — Model View Intent

Đẩy MVVM đi xa hơn: **một** state bất biến duy nhất, và mọi thay đổi phải đi qua một hàm thuần
`reduce(state, intent) -> state`.

```kotlin
// State: bất biến, mô tả TOÀN BỘ màn hình tại một thời điểm
data class LoginState(
    val username: String = "",
    val isLoading: Boolean = false,
    @StringRes val error: Int? = null,
    val loggedIn: Boolean = false,
)

// Intent: mọi việc người dùng có thể làm
sealed interface LoginIntent {
    data class UsernameChanged(val value: String) : LoginIntent
    data object Submit : LoginIntent
}

// Reducer: hàm thuần, không side effect -> test cực dễ
fun reduce(state: LoginState, intent: LoginIntent): LoginState = when (intent) {
    is LoginIntent.UsernameChanged -> state.copy(username = intent.value, error = null)
    LoginIntent.Submit             -> state.copy(isLoading = true)
}
```

**Ưu:** state luôn nhất quán, tua lại được lịch sử (debug rất mạnh), reducer là hàm thuần.
**Nhược:** nhiều boilerplate; với màn hình đơn giản là dùng dao mổ trâu.

## 3. Cùng một việc, bốn cách viết

Yêu cầu: bấm nút → gọi API → hiện loading → thành công thì chuyển màn.

| Mô hình | Ai giữ "đang loading" | Cách UI biết |
|---|---|---|
| MVC | Activity tự giữ biến | Tự set `progressBar.visibility` ngay tại chỗ |
| MVP | Presenter gọi ngược | `view.showLoading()` |
| MVVM | ViewModel giữ trong LiveData | Activity `observe` rồi set visibility |
| MVI | Nằm trong `state.isLoading` | Activity render lại toàn bộ từ state mới |

## 4. Chọn cái nào?

```
Màn hình tĩnh, không state (About, Splash)
   └─► Không cần mô hình nào cả. Activity + XML là đủ.

Màn hình có state vừa phải (Login, danh sách, form)
   └─► MVVM   ◄── dự án này

State phức tạp, nhiều nguồn dữ liệu đồng thời, cần undo/replay
   └─► MVI

Codebase Java cũ, đội đã quen
   └─► MVP (đừng viết mới bằng MVC)
```

**Với dự án này:** giữ MVVM. Ba màn hình Discovery / Schedule / Personal khi được wire nên theo
đúng khuôn mẫu ở [mvvm.md §5](mvvm.md#5-khuôn-mẫu-khi-thêm-màn-hình-mvvm-mới). Nếu màn hình
Discovery về sau phải xử lý đồng thời search + filter chip + phân trang + cache, lúc đó cân nhắc gom
về một `UiState` duy nhất theo tinh thần MVI — không cần đổi hẳn sang MVI đầy đủ.

## Xem thêm

- [MVVM](mvvm.md)
- [Clean Architecture](clean-architecture.md) — trực giao với các mô hình trên: MVVM nói về tầng UI,
  Clean nói về cách chia toàn bộ hệ thống
