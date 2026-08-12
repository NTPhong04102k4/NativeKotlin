# Repository Pattern

Repository là **cánh cổng duy nhất** giữa tầng UI và dữ liệu. ViewModel hỏi Repository, không bao
giờ hỏi thẳng Retrofit / Room / SharedPreferences.

## 1. Vì sao cần

Không có Repository, ViewModel phải tự biết: gọi API nào, cache ở đâu, khi mất mạng thì lấy dữ liệu
cũ hay báo lỗi, dữ liệu local đã cũ chưa. Toàn bộ những thứ đó **không phải việc của UI**.

```
ViewModel ──► Repository ──┬──► RemoteDataSource   (Retrofit, API)
                           ├──► LocalDataSource    (Room, DataStore)
                           └──► quyết định: lấy ở đâu, cache bao lâu, gộp thế nào
```

Repository trả lời câu hỏi *"dữ liệu ở đâu"*, để ViewModel chỉ lo *"hiển thị thế nào"*.

## 2. Trong dự án

```kotlin
// data/LoginRepository.kt
class LoginRepository(val dataSource: LoginDataSource) {

    var user: LoggedInUser? = null      // cache trong bộ nhớ
        private set

    val isLoggedIn: Boolean get() = user != null

    fun login(username: String, password: String): Result<LoggedInUser> {
        val result = dataSource.login(username, password)
        if (result is Result.Success) setLoggedInUser(result.data)
        return result
    }

    fun logout() {
        user = null
        dataSource.logout()
    }
}
```

Cấu trúc đúng: repository điều phối, DataSource lấy dữ liệu, cache nằm ở repository.

### 2.1. Ba vấn đề đang tồn tại

**a) Repository không phải singleton — cache vô tác dụng.**

```kotlin
// LoginViewModelFactory.kt: tạo MỚI mỗi lần
return LoginViewModel(LoginRepository(LoginDataSource())) as T
```

Mỗi ViewModel có một repository riêng với `user = null`. Nên `isLoggedIn` luôn `false` ở màn hình
khác, và `logout()` chỉ xoá cache của instance vừa tạo ra — tức là không xoá gì cả.

Cách sửa gọn nhất, không thêm phụ thuộc:

```kotlin
object LoginRepository {
    private val dataSource = LoginDataSource()
    var user: LoggedInUser? = null
        private set
    // ...
}
```

Hoặc giữ instance trong một lớp `Application` nếu sau này cần `Context`.

**b) Cache chỉ nằm trong RAM.** Kill app là mất trạng thái đăng nhập. Cần persist thì dùng
`EncryptedSharedPreferences` hoặc DataStore — **không** lưu token bằng `SharedPreferences` thường.
Chính comment trong code cũng đã ghi chú điều này:

```kotlin
// If user credentials will be cached in local storage, it is recommended it be encrypted
// @see https://developer.android.com/training/articles/keystore
```

**c) API đồng bộ.** `login()` không phải `suspend`, nên khi thay stub bằng network thật sẽ chặn
main thread → ANR. Phải đổi thành `suspend fun` và gọi trong `viewModelScope`.

## 3. Result — bọc thành công/thất bại

Dự án tự định nghĩa `Result` trong `data/Result.kt`:

```kotlin
sealed class Result<out T : Any> {
    data class Success<out T : Any>(val data: T) : Result<T>()
    data class Error(val exception: Exception) : Result<Nothing>()
}
```

> ⚠️ **Bẫy:** lớp này **che khuất `kotlin.Result`** của thư viện chuẩn. Ở bất cứ file nào dùng nó,
> phải import tường minh:
> ```kotlin
> import com.example.application_ai_assisstant.data.Result
> ```
> Quên import là lấy nhầm `kotlin.Result` và gặp lỗi biên dịch khó hiểu.

Vì là `sealed class`, `when` trên nó là exhaustive — trình biên dịch bắt buộc xử lý đủ nhánh:

```kotlin
when (val result = repository.login(u, p)) {
    is Result.Success -> showUser(result.data)
    is Result.Error   -> showError(result.exception)
}   // không cần else
```

Muốn thêm trạng thái Loading thì bổ sung `data object Loading : Result<Nothing>()`.

## 4. Single Source of Truth

Nguyên tắc: mỗi loại dữ liệu chỉ có **một** nơi được coi là đúng. Thường là database local, còn
network chỉ để làm mới nó.

```kotlin
// Mẫu chuẩn: UI luôn đọc từ DB, network ghi vào DB
fun observeSchedules(): Flow<List<Schedule>> = localDataSource.observeAll()

suspend fun refresh() {
    val fresh = remoteDataSource.fetchSchedules()
    localDataSource.replaceAll(fresh)     // UI tự cập nhật qua Flow
}
```

Lợi ích: app dùng được offline, không có cảnh hai màn hình hiển thị hai phiên bản dữ liệu khác nhau.

Phản mẫu cần tránh — cùng dữ liệu tồn tại ở hai nơi và tự cập nhật riêng:

```kotlin
// ✗ ViewModel A giữ list riêng, ViewModel B giữ list riêng
//   xoá ở A thì B không biết
```

## 5. Mapping model giữa các tầng

| Tầng | Kiểu | Ví dụ trong dự án |
|---|---|---|
| Network | DTO khớp JSON | *(chưa có)* `LoginResponseDto` |
| Data/DB | Entity của Room | *(chưa có)* |
| Domain | Model nghiệp vụ | `data/model/LoggedInUser` |
| UI | Chỉ thứ View cần | `ui/login/LoggedInUserView` |

Dự án đã làm đúng bước cuối: `LoggedInUser(userId, displayName)` → `LoggedInUserView(displayName)`.
View không cần `userId` nên không nhận `userId`.

Có cần cả 4 tầng model không? **Không nhất thiết.** Với app nhỏ, gộp DTO + domain làm một là chấp
nhận được. Nhưng UI model nên tách riêng khi View chỉ dùng một phần dữ liệu, hoặc cần dữ liệu đã
định dạng sẵn (ví dụ `"08:00 AM"` thay vì `Long` timestamp).

## 6. Khuôn mẫu cho repository mới

```kotlin
interface ScheduleRepository {
    fun observeToday(): Flow<List<Schedule>>
    suspend fun refresh(): Result<Unit>
    suspend fun add(schedule: Schedule): Result<Unit>
}

class ScheduleRepositoryImpl(
    private val remote: ScheduleRemoteDataSource,
    private val local: ScheduleLocalDataSource,
) : ScheduleRepository {

    override fun observeToday(): Flow<List<Schedule>> = local.observeToday()

    override suspend fun refresh(): Result<Unit> = try {
        local.replaceAll(remote.fetchAll().map { it.toDomain() })
        Result.Success(Unit)
    } catch (e: IOException) {
        Result.Error(e)
    }
}
```

Ba điểm cần nhớ:
1. Trả về `Flow` cho dữ liệu quan sát được, `suspend` cho hành động một lần.
2. Bắt exception **trong** repository, trả `Result` ra ngoài — đừng để exception rò lên ViewModel.
3. Tách `interface` giúp test ViewModel bằng fake, không cần mock framework.

## Xem thêm

- [MVVM](mvvm.md) — ai gọi Repository
- [Clean Architecture](clean-architecture.md) — Repository nằm ở ranh giới `domain` ↔ `data`
