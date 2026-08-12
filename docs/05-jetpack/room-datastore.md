# Room & DataStore — lưu trữ dữ liệu

Dự án hiện **không có bất kỳ persistence nào**. `LoginRepository` chỉ cache trong RAM; kill app là
mất sạch. Trang này mô tả hai lựa chọn chuẩn và khi nào dùng cái nào.

## 1. Chọn cái gì

| Nhu cầu | Giải pháp |
|---|---|
| Dữ liệu có cấu trúc, nhiều bản ghi, cần truy vấn | **Room** |
| Vài giá trị cài đặt (theme, đã onboarding chưa) | **Preferences DataStore** |
| Object có schema chặt | **Proto DataStore** |
| Token, mật khẩu, dữ liệu nhạy cảm | **EncryptedSharedPreferences** hoặc DataStore + mã hoá |
| File lớn (ảnh, video) | Hệ thống file + chỉ lưu đường dẫn vào DB |

Với dự án: lịch trình và bài viết Discovery → **Room**. Trạng thái đăng nhập và tuỳ chọn theme →
**DataStore**.

## 2. Room

### 2.1. Cài đặt

Room cần annotation processor. Dự án **không apply plugin Kotlin tường minh** (AGP 9 tự lo), nên
thêm KSP phải kiểm tra tương thích:

```toml
# gradle/libs.versions.toml
[versions]
room = "2.6.1"
ksp = "..."   # phải khớp với phiên bản Kotlin mà AGP 9 dùng

[libraries]
androidx-room-runtime  = { group = "androidx.room", name = "room-runtime",  version.ref = "room" }
androidx-room-ktx      = { group = "androidx.room", name = "room-ktx",      version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }

[plugins]
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

> ⚠️ Đây là chỗ dễ vướng nhất. KSP phải khớp phiên bản Kotlin; AGP 9 quản lý Kotlin ngầm nên không
> thấy ngay số phiên bản. Nếu build lỗi, kiểm tra `./gradlew :app:dependencies` để biết Kotlin nào
> đang được dùng.

### 2.2. Ba thành phần

```kotlin
// a) Entity — một bảng
@Entity(tableName = "schedules")
data class ScheduleEntity(
    @PrimaryKey val id: String,
    val title: String,
    val location: String,
    val startTime: Long,          // lưu epoch millis, KHÔNG lưu Date
    val isDone: Boolean = false,
)

// b) DAO — các truy vấn
@Dao
interface ScheduleDao {

    @Query("SELECT * FROM schedules WHERE startTime BETWEEN :from AND :to ORDER BY startTime")
    fun observeBetween(from: Long, to: Long): Flow<List<ScheduleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ScheduleEntity>)

    @Query("DELETE FROM schedules")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(items: List<ScheduleEntity>) {
        clear()
        upsertAll(items)
    }
}

// c) Database
@Database(entities = [ScheduleEntity::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scheduleDao(): ScheduleDao
}
```

### 2.3. Hai quy tắc quan trọng

**a) Trả về `Flow` cho truy vấn đọc.** Room tự phát lại khi bảng thay đổi — UI cập nhật mà không
cần gọi lại gì:

```kotlin
fun observeBetween(from: Long, to: Long): Flow<List<ScheduleEntity>>   // ✓ tự cập nhật
suspend fun getBetween(from: Long, to: Long): List<ScheduleEntity>     // ✓ đọc một lần
fun getBetweenSync(...): List<ScheduleEntity>                          // ✗ chặn main thread
```

**b) Hàm ghi phải `suspend`.** Room chặn mọi truy cập DB trên main thread (ném
`IllegalStateException`) — đó là tính năng, không phải lỗi.

### 2.4. Migration

Đổi schema mà không tăng `version` → crash `IllegalStateException: Room cannot verify the data
integrity`.

```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE schedules ADD COLUMN note TEXT NOT NULL DEFAULT ''")
    }
}

Room.databaseBuilder(context, AppDatabase::class.java, "app.db")
    .addMigrations(MIGRATION_1_2)
    .build()
```

`fallbackToDestructiveMigration()` xoá sạch dữ liệu — chỉ dùng khi đang phát triển, **không bao giờ**
để trong bản phát hành.

Bật `exportSchema = true` và commit thư mục `schemas/` vào git để review được thay đổi schema.

### 2.5. Nối vào Repository

Room là *chi tiết cài đặt của tầng data*, không được rò lên UI:

```kotlin
class ScheduleRepositoryImpl(
    private val dao: ScheduleDao,
    private val api: ScheduleApi,
) : ScheduleRepository {

    // Single source of truth: UI luôn đọc từ DB
    override fun observeToday(): Flow<List<Schedule>> =
        dao.observeBetween(startOfDay(), endOfDay())
            .map { entities -> entities.map { it.toDomain() } }   // Entity -> domain model

    override suspend fun refresh(): Result<Unit> = try {
        dao.replaceAll(api.fetchSchedules().map { it.toEntity() })
        Result.Success(Unit)
    } catch (e: IOException) {
        Result.Error(e)                 // dữ liệu cũ trong DB vẫn hiển thị được
    }
}
```

Chú ý `ScheduleEntity` (Room) khác `Schedule` (domain) — xem
[Repository Pattern §5](../01-kien-truc/repository-pattern.md#5-mapping-model-giữa-các-tầng).

## 3. DataStore

Thay thế `SharedPreferences`. Khác biệt cốt lõi:

| | SharedPreferences | DataStore |
|---|---|---|
| API | Đồng bộ (`getString`) | **Bất đồng bộ** (`Flow` / `suspend`) |
| Main thread | `apply()` vẫn ghi đĩa ngầm → có thể gây jank | Không bao giờ chặn |
| Báo lỗi | Nuốt lỗi | Phát qua `Flow` |
| Nhất quán giao dịch | Không đảm bảo | Có |
| Type safety | Không | Có (Proto DataStore) |

### 3.1. Preferences DataStore

```kotlin
private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val NIGHT_MODE = intPreferencesKey("night_mode")
        val ONBOARDED = booleanPreferencesKey("onboarded")
    }

    val nightMode: Flow<Int> = context.settingsDataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { it[Keys.NIGHT_MODE] ?: AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM }

    suspend fun setNightMode(mode: Int) {
        context.settingsDataStore.edit { it[Keys.NIGHT_MODE] = mode }
    }
}
```

`.catch { }` là bắt buộc — DataStore phát `IOException` qua Flow khi đọc file lỗi, không bắt là
crash.

### 3.2. Nối với SettingsActivity

`SettingsActivity` đang dùng `PreferenceFragmentCompat`, mà cái này ghi thẳng vào
`SharedPreferences` mặc định — **không** đi qua DataStore. Muốn hợp nhất thì hoặc:

- Đọc `PreferenceManager.getDefaultSharedPreferences(context)` ở tầng repository, hoặc
- Bỏ `PreferenceFragmentCompat`, tự dựng màn hình cài đặt đọc/ghi DataStore

Cách đầu ít việc hơn nhiều và đủ dùng.

## 4. Lưu token đăng nhập — không dùng cách thường

`LoginRepository` hiện giữ `user` trong RAM. Muốn lưu bền thì token **phải được mã hoá**:

```kotlin
val masterKey = MasterKey.Builder(context)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
    .build()

val securePrefs = EncryptedSharedPreferences.create(
    context,
    "auth_prefs",
    masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
)
```

Cần `androidx.security:security-crypto`.

Chính comment trong `LoginRepository` đã ghi chú đúng điều này:

```kotlin
// If user credentials will be cached in local storage, it is recommended it be encrypted
// @see https://developer.android.com/training/articles/keystore
```

Với xác thực doanh nghiệp, ForgeRock SDK tự quản lý lưu trữ token an toàn — xem
[ForgeRock](../07-tich-hop/forgerock.md).

## 5. Lộ trình đề xuất cho dự án

| Bước | Việc | Vì sao trước |
|---|---|---|
| 1 | DataStore cho tuỳ chọn theme (dark mode) | Nhỏ, không rủi ro, sửa luôn được lỗi dark mode |
| 2 | `EncryptedSharedPreferences` cho token | Cần trước khi có API thật |
| 3 | Room cho `schedules` | Màn hình Schedule cần dữ liệu offline |
| 4 | Room cho `discovery_items` + Paging | Xem [Paging](paging-recyclerview.md) |

## Xem thêm

- [Repository Pattern](../01-kien-truc/repository-pattern.md)
- [WorkManager](workmanager.md) — đồng bộ nền vào Room
- [Coroutines & Flow](../06-kotlin/coroutines-flow.md)
