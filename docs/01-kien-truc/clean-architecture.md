# Clean Architecture

MVVM trả lời "tầng UI tổ chức thế nào". Clean Architecture trả lời "**toàn bộ hệ thống** chia tầng
thế nào". Hai thứ trực giao nhau — dùng Clean vẫn dùng MVVM ở tầng presentation.

## 1. Quy tắc phụ thuộc

Đây là quy tắc duy nhất thực sự quan trọng:

> **Phụ thuộc chỉ được hướng vào trong.** Tầng trong không biết gì về tầng ngoài.

```
        ┌─────────────────────────────────────────┐
        │           PRESENTATION                  │   Activity, ViewModel
        │   ┌─────────────────────────────────┐   │   biết Android
        │   │            DOMAIN               │   │   UseCase, Entity, interface Repository
        │   │   ┌─────────────────────────┐   │   │   Kotlin thuần, KHÔNG import android.*
        │   │   │        ENTITY           │   │   │
        │   │   └─────────────────────────┘   │   │
        │   └─────────────────────────────────┘   │
        │                 DATA                    │   RepositoryImpl, Retrofit, Room
        └─────────────────────────────────────────┘   biết Android
                        ▲       ▲
                        └───────┘
              cả hai đều phụ thuộc vào DOMAIN
```

Điểm mấu chốt: **`data` phụ thuộc `domain`, không phải ngược lại**. Làm được nhờ *dependency
inversion* — interface `Repository` nằm ở `domain`, lớp cài đặt nằm ở `data`.

## 2. Ba tầng cụ thể

### 2.1. Domain — trái tim, không biết Android

```kotlin
// domain/model/User.kt — entity thuần
data class User(val id: String, val displayName: String)

// domain/repository/AuthRepository.kt — chỉ là INTERFACE
interface AuthRepository {
    suspend fun login(username: String, password: String): Result<User>
    suspend fun logout()
}

// domain/usecase/LoginUseCase.kt — một nghiệp vụ = một class
class LoginUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(username: String, password: String): Result<User> {
        if (password.length <= 5) return Result.Error(IllegalArgumentException("Mật khẩu quá ngắn"))
        return repository.login(username.trim(), password)
    }
}
```

Kiểm tra nhanh xem tầng domain có "sạch" không: **nếu file nào import `android.*` thì nó không
thuộc domain.** Domain phải chạy được trong unit test JVM thuần, không cần emulator.

### 2.2. Data — cài đặt interface của domain

```kotlin
// data/repository/AuthRepositoryImpl.kt
class AuthRepositoryImpl(
    private val remote: AuthRemoteDataSource,
    private val local: AuthLocalDataSource,
) : AuthRepository {

    override suspend fun login(username: String, password: String): Result<User> {
        val response = remote.login(username, password)      // DTO của tầng data
        local.saveToken(response.token)
        return Result.Success(response.toDomain())           // map DTO -> entity domain
    }

    override suspend fun logout() = local.clear()
}
```

Chú ý `response.toDomain()`: DTO của API **không** được rò lên tầng domain. Đổi API thì chỉ sửa
mapper, domain và UI không đụng tới.

### 2.3. Presentation — MVVM nằm ở đây

```kotlin
class LoginViewModel(private val loginUseCase: LoginUseCase) : ViewModel() {
    fun login(username: String, password: String) = viewModelScope.launch {
        _state.value = _state.value.copy(isLoading = true)
        when (val result = loginUseCase(username, password)) {   // gọi UseCase
            is Result.Success -> _state.value = LoginState(loggedIn = true)
            is Result.Error   -> _state.value = LoginState(error = R.string.login_failed)
        }
    }
}
```

## 3. UseCase — cần hay không?

UseCase là một class **một việc duy nhất**, thường chỉ có `operator fun invoke()`.

**Đáng dùng khi:**
- Cùng một nghiệp vụ được nhiều ViewModel gọi (ví dụ `LogoutUseCase` dùng ở cả Personal và Settings)
- Nghiệp vụ phải phối hợp nhiều repository (`GetDashboardUseCase` gộp lịch + gợi ý + hồ sơ)
- Logic đủ phức tạp để đáng test riêng

**Không đáng dùng khi:** UseCase chỉ gọi thẳng một hàm repository rồi trả về. Lúc đó nó chỉ là một
lớp trung gian rỗng làm code khó đọc thêm.

```kotlin
// UseCase vô nghĩa — bỏ đi, cho ViewModel gọi thẳng repository
class GetUserUseCase(private val repo: UserRepository) {
    suspend operator fun invoke() = repo.getUser()
}
```

## 4. Áp vào dự án này thì thế nào?

Hiện tại dự án **chưa** theo Clean Architecture. Cấu trúc thực tế:

```
data/
├── LoginDataSource.kt       ← không có interface, ViewModel gọi qua Repository cụ thể
├── LoginRepository.kt       ← class cụ thể, không phải interface
├── Result.kt
└── model/LoggedInUser.kt
ui/login/                    ← ViewModel gọi thẳng Repository, không có UseCase
```

Nếu muốn chuyển sang Clean, đây là lộ trình theo thứ tự rủi ro tăng dần:

| Bước | Việc | Được gì |
|---|---|---|
| 1 | Tách `interface AuthRepository`, cho `LoginRepository` cài đặt nó | Test ViewModel bằng fake repository, không cần sửa gì khác |
| 2 | Chuyển các hàm sang `suspend`, ViewModel dùng `viewModelScope` | Hết nguy cơ ANR khi thay stub bằng API thật |
| 3 | Đưa validate mật khẩu/email từ ViewModel xuống `LoginUseCase` | Logic nghiệp vụ test được bằng JVM thuần |
| 4 | Tách package `domain/` không import `android.*` | Ranh giới cưỡng chế được bằng lint/module |
| 5 | Tách thành Gradle module `:domain`, `:data`, `:app` | Trình biên dịch chặn vi phạm tầng, build song song nhanh hơn |

**Khuyến nghị thực tế:** với quy mô hiện tại (1 màn hình có logic, 5 màn hình khung), làm **bước 1
và 2 là đủ**. Bước 4–5 chỉ đáng khi codebase lớn lên nhiều hoặc có nhiều người cùng làm.

## 5. Khi nào KHÔNG nên dùng Clean Architecture

Clean không miễn phí — nó đánh đổi số lượng file lấy khả năng thay thế từng tầng.

Với dự án này, một luồng login theo Clean đầy đủ cần: `User`, `AuthRepository`, `LoginUseCase`,
`AuthRepositoryImpl`, `AuthRemoteDataSource`, `LoginRequestDto`, `LoginResponseDto`, mapper,
`LoginViewModel`, `LoginState`, `LoginActivity` — **11 file** cho một màn hình đăng nhập.

Đừng dùng Clean khi:
- App nhỏ, ít màn hình, một người làm
- Prototype / MVP cần ra nhanh
- Không có ý định thay đổi nguồn dữ liệu

Dùng Clean khi:
- Nhiều người/nhiều team cùng chạm vào codebase
- Có nhiều nguồn dữ liệu (remote + cache + local) cần hoán đổi
- Logic nghiệp vụ phức tạp và cần test kỹ độc lập với Android

## Xem thêm

- [MVVM](mvvm.md) — tầng presentation
- [Repository Pattern](repository-pattern.md) — ranh giới `domain` ↔ `data`
