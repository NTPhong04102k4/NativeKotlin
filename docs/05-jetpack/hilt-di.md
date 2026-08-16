# Dependency Injection — từ factory thủ công tới Hilt

## 1. Dự án đang DI thủ công

```kotlin
// LoginViewModelFactory.kt
override fun <T : ViewModel> create(modelClass: Class<T>): T {
    if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
        return LoginViewModel(
            loginRepository = LoginRepository(dataSource = LoginDataSource())
        ) as T
    }
    throw IllegalArgumentException("Unknown ViewModel class")
}
```

Đây **là** dependency injection — chỉ là làm bằng tay. Với một ViewModel thì hoàn toàn ổn và không
tốn thêm thư viện nào.

Nhưng nó có một lỗi thật: `LoginRepository(LoginDataSource())` được tạo **mới mỗi lần**, nên cache
trong repository vô tác dụng. Xem
[Repository Pattern §2.1](../01-kien-truc/repository-pattern.md).

## 2. Bước trung gian: Service Locator

Trước khi nhảy sang Hilt, có một bước rẻ hơn nhiều giải quyết được vấn đề singleton:

```kotlin
// di/ServiceLocator.kt
object ServiceLocator {

    @Volatile
    private var loginRepository: LoginRepository? = null

    fun provideLoginRepository(): LoginRepository =
        loginRepository ?: synchronized(this) {
            loginRepository ?: LoginRepository(LoginDataSource()).also { loginRepository = it }
        }

    @VisibleForTesting
    fun resetForTest() {
        synchronized(this) { loginRepository = null }
    }
}
```

```kotlin
// LoginViewModelFactory.kt
return LoginViewModel(ServiceLocator.provideLoginRepository()) as T
```

**Được gì:** repository thành singleton thật, cache hoạt động, `logout()` có tác dụng, test thay
được bằng fake qua `resetForTest()`.

**Chi phí:** không thêm thư viện, không thêm thời gian build, khoảng 15 dòng.

> ⚠️ Nếu `ServiceLocator` cần giữ `Context`, **chỉ dùng `applicationContext`**. Giữ Activity
> context trong `object` là rò rỉ và sẽ bị lint `StaticFieldLeak` (mức `error`) bắt.

**Khuyến nghị:** với quy mô hiện tại của dự án, dừng ở đây là hợp lý. Chỉ chuyển sang Hilt khi số
ViewModel/Repository tăng lên đáng kể.

## 3. Hilt

### 3.1. Cài đặt — và cảnh báo cho dự án này

```toml
[versions]
hilt = "2.51.1"
ksp = "..."     # phải khớp phiên bản Kotlin

[libraries]
hilt-android  = { group = "com.google.dagger", name = "hilt-android",          version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-android-compiler", version.ref = "hilt" }

[plugins]
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
ksp  = { id = "com.google.devtools.ksp",        version.ref = "ksp" }
```

> ⚠️ **Hai rào cản riêng của repo này:**
>
> 1. Dự án **không apply plugin Kotlin tường minh** — AGP 9 cung cấp Kotlin ngầm. Hilt cần KSP,
>    mà KSP cần biết phiên bản Kotlin. Đây là chỗ nhiều khả năng phải chỉnh lại phần plugin setup
>    (`CLAUDE.md` cảnh báo đừng thêm `org.jetbrains.kotlin.android` nếu chưa rework).
> 2. **Configuration cache đang bật.** Plugin Hilt và KSP đều tạo task mới; cần kiểm tra chúng an
>    toàn với configuration cache.
>
> Hãy thử trên một nhánh riêng trước.

### 3.2. Application class

Dự án **chưa có** lớp `Application` tuỳ biến. Hilt bắt buộc phải có:

```kotlin
@HiltAndroidApp
class AiAssistantApplication : Application()
```

```xml
<application
    android:name=".AiAssistantApplication"
    ... >
```

### 3.3. Module

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideLoginDataSource(): LoginDataSource = LoginDataSource()

    @Provides
    @Singleton
    fun provideLoginRepository(dataSource: LoginDataSource): LoginRepository =
        LoginRepository(dataSource)
}
```

Với interface, dùng `@Binds` (nhẹ hơn `@Provides`):

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: AuthRepositoryImpl): AuthRepository
}
```

### 3.4. ViewModel — factory biến mất

```kotlin
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginRepository: LoginRepository,
    private val savedStateHandle: SavedStateHandle,   // Hilt tự cung cấp
) : ViewModel()
```

```kotlin
@AndroidEntryPoint
class LoginActivity : AppCompatActivity() {
    private val viewModel: LoginViewModel by viewModels()   // không cần factory
}
```

`LoginViewModelFactory.kt` **xoá được hoàn toàn**. Đây là lợi ích rõ nhất của Hilt: mỗi ViewModel
mới không còn kéo theo một file factory.

### 3.5. Scope

| Annotation | Sống cùng | Dùng cho |
|---|---|---|
| `@Singleton` | Toàn app | Repository, DataSource, Retrofit, Room |
| `@ActivityRetainedScoped` | Sống qua đổi cấu hình | State chia sẻ giữa các ViewModel |
| `@ActivityScoped` | Một Activity | Thứ cần Activity context |
| `@ViewModelScoped` | Một ViewModel | UseCase có state |
| *(không annotation)* | Tạo mới mỗi lần inject | Mapper, object không state |

## 4. So sánh ba cách

| | Factory thủ công | Service Locator | Hilt |
|---|---|---|---|
| Boilerplate mỗi ViewModel mới | 1 file factory | 1 dòng | 0 |
| Singleton | Tự lo | Tự lo (dễ) | Tự động |
| Kiểm tra lúc biên dịch | Không | Không | **Có** |
| Thời gian build | 0 | 0 | **+đáng kể** |
| Test | Sửa factory | `resetForTest()` | `@TestInstallIn` |
| Phát hiện phụ thuộc vòng | Lúc chạy | Lúc chạy | **Lúc biên dịch** |
| Đường cong học | Không | Không | Dốc |

## 5. Koin — lựa chọn thay thế

Nhẹ hơn Hilt, không cần annotation processor nên **không vướng vấn đề KSP/plugin Kotlin** của dự án:

```kotlin
val appModule = module {
    single { LoginDataSource() }
    single { LoginRepository(get()) }
    viewModel { LoginViewModel(get()) }
}

// Application
startKoin {
    androidContext(this@AiAssistantApplication)
    modules(appModule)
}

// Activity
private val viewModel: LoginViewModel by viewModel()
```

| | Hilt | Koin |
|---|---|---|
| Kiểm tra lúc biên dịch | Có | **Không** — thiếu binding thì crash lúc chạy |
| Ảnh hưởng thời gian build | Đáng kể | Gần như không |
| Cần KSP/kapt | **Có** | Không |
| Google hậu thuẫn | Có | Không |

Với ràng buộc plugin của repo này, **Koin có thể là lựa chọn thực tế hơn Hilt** nếu quyết định dùng
DI framework.

## 6. Khuyến nghị

```
Bây giờ (1 ViewModel)
   └─► Giữ factory thủ công, nhưng chuyển LoginRepository thành singleton
       qua ServiceLocator (§2). Đây là việc nên làm ngay — nó sửa một lỗi thật.

3–5 ViewModel
   └─► ServiceLocator vẫn ổn. Bắt đầu thấy lặp.

Trên 5 ViewModel, nhiều repository, có nhiều người cùng làm
   └─► Chuyển sang Koin (ít rủi ro build) hoặc Hilt (an toàn hơn lúc biên dịch)
```

Đừng thêm DI framework "cho chuẩn" khi chưa có vấn đề nó giải quyết — với dự án này, thứ đáng sửa
ngay là **singleton của repository**, không phải thiếu Hilt.

## Xem thêm

- [Repository Pattern](../01-kien-truc/repository-pattern.md) — lỗi singleton cụ thể
- [MVVM](../01-kien-truc/mvvm.md) — vai trò của factory
- [Clean Architecture](../01-kien-truc/clean-architecture.md)
