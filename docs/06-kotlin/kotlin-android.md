# Kotlin cho Android

Các đặc trưng Kotlin hay gặp nhất khi làm Android, kèm ví dụ từ chính codebase này.

## 1. Null safety

```kotlin
var a: String = "x"      // không bao giờ null
var b: String? = null    // có thể null
```

| Toán tử | Nghĩa |
|---|---|
| `?.` | Gọi nếu khác null, ngược lại trả null |
| `?:` | Elvis — giá trị thay thế khi null |
| `!!` | Ép non-null, null thì **ném NPE** |
| `?.let { }` | Chạy khối lệnh nếu khác null |

Ví dụ thật — ViewBinding sinh field nullable cho view không có ở mọi biến thể layout:

```kotlin
// LoginActivity.kt
loading?.visibility = View.GONE                 // 'loading' chỉ có trong layout/
binding.btnGoogle?.setOnClickListener { ... }
```

Xem [ViewBinding §4](../03-ui/viewbinding.md#4-field-nullable--bẫy-lớn-nhất-của-dự-án-này).

> **Tránh `!!`.** Mỗi `!!` là một chỗ crash tiềm tàng. Thay bằng `?.let`, `?:` hoặc
> `requireNotNull(x) { "thông báo rõ ràng" }` để khi lỗi còn biết vì sao.

### `lateinit`

```kotlin
private lateinit var binding: ActivityLoginBinding
```

Dùng khi biết chắc sẽ khởi tạo trước lần dùng đầu tiên nhưng không thể gán lúc khai báo. Truy cập
trước khi gán → `UninitializedPropertyAccessException` (thông báo rõ ràng hơn NPE).

Kiểm tra: `if (::binding.isInitialized)`.

## 2. Data class

```kotlin
data class LoggedInUser(val userId: String, val displayName: String)
```

Tự sinh `equals()`, `hashCode()`, `toString()`, `copy()`, `componentN()`.

`equals()` tự sinh là lý do `DiffUtil.areContentsTheSame` chỉ cần viết `a == b` — xem
[Paging & RecyclerView](../05-jetpack/paging-recyclerview.md).

`copy()` là nền của việc cập nhật state bất biến:

```kotlin
_uiState.value = _uiState.value.copy(isLoading = true)   // chỉ đổi 1 field
```

## 3. Sealed class / sealed interface

Tập con **đóng** — trình biên dịch biết hết các nhánh.

```kotlin
// data/Result.kt
sealed class Result<out T : Any> {
    data class Success<out T : Any>(val data: T) : Result<T>()
    data class Error(val exception: Exception) : Result<Nothing>()
}
```

Lợi ích lớn nhất: `when` **exhaustive**, không cần `else`:

```kotlin
when (result) {
    is Result.Success -> showUser(result.data)
    is Result.Error   -> showError(result.exception)
}
```

Thêm nhánh mới vào sealed class → mọi `when` thiếu nhánh đó **fail build**. Đây là cách bắt lỗi
"quên xử lý trạng thái mới" ngay lúc biên dịch.

`sealed interface` linh hoạt hơn (một lớp implement được nhiều interface) — ưu tiên dùng khi không
cần state chung.

## 4. Extension function

Thêm hàm cho class có sẵn mà không kế thừa.

```kotlin
// LoginActivity.kt — extension cho EditText
fun EditText.afterTextChanged(afterTextChanged: (String) -> Unit) {
    addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(editable: Editable?) {
            afterTextChanged.invoke(editable.toString())
        }
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
    })
}
```

```kotlin
// util/AppRouter.kt — extension cho AppCompatActivity
fun AppCompatActivity.setupBottomNavigation(
    bottomNavigationView: BottomNavigationView,
    currentItemId: Int,
) = AppRouter.bind(this, bottomNavigationView, currentItemId)
```

Extension **không thực sự** thêm gì vào class — nó là hàm static với receiver làm tham số đầu.
Nghĩa là không override được và không truy cập được thành viên `private`.

Extension `private` bên trong `object` (như `Activity.applyTabTransition()` trong `AppRouter`) là
cách hay để giữ helper gần chỗ dùng mà không làm rác namespace.

## 5. Scope function

Năm hàm hay nhầm lẫn nhất:

| Hàm | Tham chiếu | Trả về | Dùng khi |
|---|---|---|---|
| `let` | `it` | Kết quả lambda | Xử lý giá trị nullable |
| `run` | `this` | Kết quả lambda | Tính toán trên object |
| `with` | `this` | Kết quả lambda | Gọi nhiều hàm của cùng object |
| `apply` | `this` | **Object gốc** | Cấu hình object |
| `also` | `it` | **Object gốc** | Side effect (log, kiểm tra) |

```kotlin
// apply — cấu hình rồi trả lại chính nó
val intent = Intent(context, AlarmReceiver::class.java).apply {
    putExtra("id", 1)
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

// let — chỉ chạy khi khác null
loginResult.success?.let { user ->
    setResult(RESULT_OK)
    goToHome(user)
}

// with — gọi nhiều thứ trên binding
fun bind(item: Schedule) = with(binding) {
    tvTime.text = item.timeText
    tvTaskTitle.text = item.title
}

// also — side effect, không đổi giá trị trả về
val repo = LoginRepository(dataSource).also { instance = it }
```

**Mẹo nhớ:** `apply`/`also` trả object gốc (dùng để cấu hình), `let`/`run`/`with` trả kết quả
lambda (dùng để biến đổi).

## 6. Object và companion object

```kotlin
// Singleton thật sự — khởi tạo lười, thread-safe
object AppRouter {
    val START_TAB_ID: Int = R.id.nav_discovery
    fun openTab(from: Activity, itemId: Int): Boolean { ... }
}

// Thành viên "static" của một class
class ScheduleAdapter : ListAdapter<...>(DIFF) {
    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Schedule>() { ... }
    }
}
```

> ⚠️ `object` giữ tham chiếu suốt đời process. **Không bao giờ** lưu `Activity`, `View`, hay
> Activity `Context` trong `object` — lint `StaticFieldLeak` (mức `error`) sẽ chặn build.
> `AppRouter` nhận `Activity` làm **tham số**, không lưu lại — đó là cách đúng.

## 7. Delegated property

```kotlin
// by lazy — tính một lần, lười
private val alarmManager by lazy {
    context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
}

// by viewModels() — lấy ViewModel
private val viewModel: LoginViewModel by viewModels { LoginViewModelFactory() }

// by preferencesDataStore
private val Context.settings by preferencesDataStore(name = "settings")
```

## 8. Higher-order function & lambda

```kotlin
// BiometricHelper nhận callback làm tham số
fun showBiometricPrompt(
    title: String,
    subtitle: String,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
)

// Gọi — lambda cuối đưa ra ngoài ngoặc
biometricHelper.showBiometricPrompt(
    getString(R.string.biometric_title),
    getString(R.string.biometric_subtitle),
    onSuccess = { goToHome(...) },
    onError = { message -> Toast.makeText(this, message, LENGTH_SHORT).show() },
)
```

Đây là cách Kotlin thay thế interface callback của Java — gọn hơn nhiều so với
`new OnSuccessListener() { ... }`.

## 9. Default & named argument

```kotlin
data class LoginFormState(
    val usernameError: Int? = null,
    val passwordError: Int? = null,
    val isDataValid: Boolean = false,
)

LoginFormState(usernameError = R.string.invalid_username)   // các field khác lấy mặc định
```

Thay thế hoàn toàn cho việc nạp chồng hàm (overload) kiểu Java. Named argument cũng làm chỗ gọi
đọc rõ hơn nhiều:

```kotlin
setupBottomNavigation(binding.bottomNavigation, R.id.nav_discovery)                 // mơ hồ
setupBottomNavigation(bottomNavigationView = ..., currentItemId = R.id.nav_discovery) // rõ
```

## 10. Collection

```kotlin
val names = devices.map { it.name }
val active = schedules.filter { !it.isDone }
val byDay = schedules.groupBy { it.day }
val first = schedules.firstOrNull { it.isDone }
val total = items.sumOf { it.duration }

// util/BluetoothHelper.kt — map + Elvis
return bluetoothAdapter?.bondedDevices?.map { it.name } ?: emptyList()
```

Với chuỗi thao tác trên collection lớn, dùng `asSequence()` để tránh tạo list trung gian:

```kotlin
items.asSequence().filter { ... }.map { ... }.take(20).toList()
```

## 11. Quy ước code của dự án

Từ `.editorconfig`:

```
indent_size = 4
max_line_length = 120
insert_final_newline = true
disabled_rules = no-wildcard-imports    # cho phép import .*
```

> `make format` gọi `./gradlew ktlintFormat` nhưng **không có plugin ktlint nào được apply** —
> task đó không tồn tại. Định dạng hiện chỉ được `.editorconfig` đảm bảo qua IDE.
> Muốn cưỡng chế thật thì phải thêm plugin ktlint vào `app/build.gradle.kts` + version catalog.

**Comment, log và chuỗi hiển thị viết bằng tiếng Việt** — bám theo phần còn lại của codebase.

## 12. Bẫy Kotlin ↔ Android

**a) `Result` bị che khuất.** `data/Result.kt` che `kotlin.Result`; thêm Worker vào là có ba
`Result` khác nhau. Luôn import tường minh.

**b) `it` lồng nhau.** Hai lambda lồng nhau thì `it` bên trong che `it` bên ngoài — đặt tên rõ:

```kotlin
loginResult.success?.let { user -> ... }     // ✓ có tên
loginResult.success?.let { ... it ... }      // ✗ mơ hồ khi lồng
```

**c) `object : Interface { }` giữ tham chiếu ngoài.** Anonymous object bên trong Activity giữ
tham chiếu tới Activity — nguồn gốc của lint `HandlerLeak`.

**d) `lateinit` không dùng được với kiểu nguyên thuỷ** (`Int`, `Boolean`). Dùng `by Delegates.notNull()`.

## Xem thêm

- [Coroutines & Flow](coroutines-flow.md)
- [MVVM](../01-kien-truc/mvvm.md)
