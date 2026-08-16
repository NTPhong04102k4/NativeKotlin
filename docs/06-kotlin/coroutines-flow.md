# Coroutines & Flow

`kotlinx-coroutines-android` giờ đã được khai báo trực tiếp trong version catalog. Trang này là
tài liệu tham chiếu cho việc chuyển dự án sang bất đồng bộ — việc **bắt buộc** trước khi nối API
thật, vì `LoginViewModel.login()` hiện đang chặn main thread.

## 1. Coroutine là gì

Một tác vụ **tạm dừng được** mà không chặn thread. Hàm `suspend` có thể dừng ở giữa chừng, nhả
thread cho việc khác, rồi chạy tiếp khi có kết quả.

```kotlin
suspend fun login(username: String, password: String): Result<LoggedInUser> {
    delay(1000)              // dừng 1 giây, KHÔNG chặn thread
    return api.login(...)
}
```

Hàm `suspend` chỉ gọi được từ hàm `suspend` khác hoặc từ trong một coroutine.

## 2. Scope — luôn phải có chủ

| Scope | Sống cùng | Dùng ở |
|---|---|---|
| `viewModelScope` | ViewModel | Logic nghiệp vụ trong ViewModel |
| `lifecycleScope` | Activity/Fragment | Việc gắn với UI |
| `CoroutineScope(...)` tự tạo | Bạn tự quản | Class không có vòng đời (Repository singleton) |
| `GlobalScope` | Toàn process | **Đừng dùng** |

```kotlin
// ✗ Không bao giờ bị huỷ, tiếp tục chạy sau khi màn hình đóng
GlobalScope.launch { ... }

// ✓ Tự huỷ khi ViewModel clear
viewModelScope.launch { ... }
```

Đây gọi là *structured concurrency*: mỗi coroutine thuộc về một scope, scope chết thì con cũng chết.
Không có coroutine mồ côi.

## 3. Dispatcher

| Dispatcher | Chạy trên | Dùng cho |
|---|---|---|
| `Dispatchers.Main` | Main thread | Cập nhật UI |
| `Dispatchers.IO` | Pool cho I/O | Network, đọc/ghi file, database |
| `Dispatchers.Default` | Pool theo số CPU | Tính toán nặng, sắp xếp list lớn |
| `Dispatchers.Unconfined` | Không xác định | Hiếm khi cần |

```kotlin
viewModelScope.launch {                       // mặc định Main
    _uiState.value = _uiState.value.copy(isLoading = true)   // an toàn: đang ở Main

    val data = withContext(Dispatchers.IO) {  // chuyển sang IO
        repository.fetchSchedules()
    }                                          // tự quay lại Main

    _uiState.value = LoginUiState(items = data)
}
```

> **Quy tắc:** việc chuyển dispatcher nên nằm **trong repository/data source**, không phải trong
> ViewModel. ViewModel gọi hàm `suspend` và không cần biết nó chạy ở thread nào.

```kotlin
// Repository tự lo dispatcher — ViewModel gọi sạch sẽ
class LoginRepository(private val dataSource: LoginDataSource) {
    suspend fun login(u: String, p: String): Result<LoggedInUser> =
        withContext(Dispatchers.IO) { dataSource.login(u, p) }
}
```

Retrofit và Room **đã tự chuyển dispatcher** — không cần bọc `withContext(Dispatchers.IO)` quanh
chúng nữa.

## 4. `launch` vs `async`

```kotlin
// launch — bắn đi, không cần kết quả
viewModelScope.launch { repository.refresh() }

// async — cần kết quả, chạy song song
viewModelScope.launch {
    val schedules = async { repository.fetchSchedules() }
    val profile   = async { repository.fetchProfile() }
    render(schedules.await(), profile.await())     // chờ cả hai, tổng thời gian = cái lâu nhất
}
```

Dùng `async` mà không `await()` là code smell — nó nuốt exception.

## 5. Xử lý lỗi

```kotlin
viewModelScope.launch {
    try {
        val result = repository.login(u, p)
        // ...
    } catch (e: CancellationException) {
        throw e                     // BẮT BUỘC ném lại — đây là tín hiệu huỷ, không phải lỗi
    } catch (e: IOException) {
        _uiState.value = LoginUiState(error = R.string.error_network)
    }
}
```

> ⚠️ **`catch (e: Exception)` bắt luôn cả `CancellationException`** và làm hỏng cơ chế huỷ.
> Luôn bắt `CancellationException` riêng rồi ném lại, hoặc bắt kiểu cụ thể.

Cách sạch hơn: bắt exception **trong repository**, trả về `Result` như dự án đã thiết kế:

```kotlin
suspend fun login(u: String, p: String): Result<LoggedInUser> = try {
    Result.Success(api.login(u, p).toDomain())
} catch (e: IOException) {
    Result.Error(e)
}
```

Lúc đó ViewModel chỉ cần `when` trên `Result` — không cần try/catch nào cả.

## 6. Flow

Flow là luồng giá trị bất đồng bộ — "nhiều giá trị theo thời gian", so với `suspend` là "một giá
trị một lần".

| | `suspend fun` | `Flow` |
|---|---|---|
| Số giá trị | Một | Nhiều |
| Ví dụ | `login()`, `refresh()` | `observeSchedules()`, `isConnected` |

### 6.1. Cold vs Hot

```kotlin
// COLD: mỗi collector kích hoạt một lần chạy riêng
val cold = flow {
    emit(api.fetch())    // chạy lại cho từng collector
}

// HOT: luôn tồn tại, mọi collector chia sẻ cùng nguồn
val hot: StateFlow<Boolean> = _isConnected     // NetworkMonitor
```

| | `StateFlow` | `SharedFlow` | `Flow` (cold) |
|---|---|---|---|
| Giá trị khởi tạo | Bắt buộc | Không | Không |
| Giữ giá trị cuối | Có | Tuỳ `replay` | Không |
| Bỏ giá trị trùng lặp | Có | Không | Không |
| Dùng cho | **State** | **Sự kiện một lần** | Nguồn dữ liệu |

`util/NetworkMonitor` dùng `StateFlow<Boolean>` — đúng, vì "có mạng không" là state.

### 6.2. Collect đúng vòng đời

Đây là chỗ `LoginActivity` đang làm chưa chuẩn:

```kotlin
// ✗ Hiện tại: coroutine vẫn chạy khi app xuống background
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

`repeatOnLifecycle` có trong `lifecycle-runtime-ktx` — đã được khai báo trong catalog.

Nhiều Flow cùng lúc:

```kotlin
lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        launch { viewModel.uiState.collect { render(it) } }
        launch { viewModel.events.collect { handle(it) } }
    }
}
```

### 6.3. Toán tử hay dùng

```kotlin
repository.observeSchedules()
    .map { list -> list.filter { !it.isDone } }
    .distinctUntilChanged()
    .onStart { emit(emptyList()) }
    .catch { e -> emit(emptyList()) }              // bắt lỗi upstream
    .flowOn(Dispatchers.IO)                        // đổi dispatcher cho phần TRÊN nó
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
```

`stateIn` biến cold Flow thành `StateFlow` — mẫu chuẩn để expose state từ ViewModel:

| Tham số | Ý nghĩa |
|---|---|
| `WhileSubscribed(5000)` | Dừng upstream 5 giây sau khi collector cuối rời đi (đủ để sống qua xoay màn hình) |
| `Eagerly` | Chạy ngay, không bao giờ dừng |
| `Lazily` | Chạy khi có collector đầu tiên, sau đó không dừng |

### 6.4. Ô tìm kiếm — ví dụ thực tế

Cho `search_hint` trong màn hình Discovery:

```kotlin
private val query = MutableStateFlow("")

val results: StateFlow<List<DiscoveryItem>> = query
    .debounce(300)                    // chờ người dùng ngừng gõ 300ms
    .filter { it.length >= 2 }
    .distinctUntilChanged()
    .flatMapLatest { q ->             // huỷ truy vấn cũ khi có từ khoá mới
        repository.search(q)
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

fun onQueryChanged(value: String) { query.value = value }
```

`flatMapLatest` là mấu chốt: gõ nhanh thì các request cũ bị huỷ, chỉ giữ kết quả mới nhất.

## 7. `callbackFlow` — bọc API callback

`NetworkMonitor` hiện tự quản `MutableStateFlow` và yêu cầu gọi `startMonitoring()`/`stopMonitoring()`
thủ công. `callbackFlow` làm việc đó tự động:

```kotlin
fun Context.networkStatus(): Flow<Boolean> = callbackFlow {
    val manager = getSystemService(ConnectivityManager::class.java)

    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { trySend(true) }
        override fun onLost(network: Network) { trySend(false) }
    }

    val request = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .build()

    manager.registerNetworkCallback(request, callback)

    // Chạy khi collector huỷ — KHÔNG THỂ QUÊN huỷ đăng ký
    awaitClose { manager.unregisterNetworkCallback(callback) }
}
```

Ưu điểm lớn nhất: `awaitClose` gắn việc dọn dẹp vào vòng đời của Flow, nên không có chuyện quên
`stopMonitoring()` như hiện tại.

## 8. Test

```kotlin
@Test
fun `login thanh cong tra ve user`() = runTest {
    val repo = FakeLoginRepository(Result.Success(LoggedInUser("1", "Jane")))
    val viewModel = LoginViewModel(repo)

    viewModel.login("user@example.com", "password")

    assertEquals("Jane", viewModel.uiState.value.user?.displayName)
}
```

`runTest` từ `kotlinx-coroutines-test` chạy coroutine tức thì (bỏ qua `delay`). Cần thêm:

```toml
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutines" }
```

Với `viewModelScope` chạy trên `Dispatchers.Main`, test cần đổi dispatcher:

```kotlin
@Before fun setUp() = Dispatchers.setMain(StandardTestDispatcher())
@After  fun tearDown() = Dispatchers.resetMain()
```

## 9. Việc cần làm cho dự án

Theo thứ tự:

1. `LoginDataSource.login()` → `suspend fun`, thêm `withContext(Dispatchers.IO)` ở repository
2. `LoginViewModel.login()` → bọc trong `viewModelScope.launch`
3. `LoginActivity` → `repeatOnLifecycle(STARTED)` cho `NetworkMonitor`
4. Cân nhắc `callbackFlow` cho `NetworkMonitor`
5. Thêm `kotlinx-coroutines-test` và viết test thật (hiện chỉ có 2 test mẫu do IDE sinh)

Bước 1–2 là **bắt buộc** trước khi Retrofit được nối vào — nếu không, request mạng đầu tiên sẽ
chặn main thread và gây ANR.

## Xem thêm

- [Kotlin cho Android](kotlin-android.md)
- [Lifecycle & ViewModel](../05-jetpack/lifecycle-viewmodel.md)
- [Repository Pattern](../01-kien-truc/repository-pattern.md)
