# WorkManager

WorkManager chạy việc nền **đảm bảo hoàn thành**, kể cả khi app bị đóng hoặc thiết bị khởi động lại.

## 1. Khi nào dùng cái gì

| Nhu cầu | Công cụ |
|---|---|
| Việc phải xong dù app đóng (đồng bộ, upload) | **WorkManager** |
| Việc ngắn, chỉ khi app đang mở | `viewModelScope` / `lifecycleScope` |
| Đúng một thời điểm chính xác (báo thức, nhắc lịch) | `AlarmManager` → `AlarmHelper` |
| Việc người dùng thấy được, đang chạy (phát nhạc, ghi âm) | Foreground Service |
| Phản ứng tức thì với sự kiện hệ thống | `BroadcastReceiver` |

**WorkManager không thay thế `AlarmManager`.** WorkManager tối thiểu 15 phút cho việc định kỳ và
không đảm bảo đúng giây — báo thức vẫn phải dùng `AlarmHelper`.

## 2. Worker

```kotlin
class SyncScheduleWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val repository = ServiceLocator.scheduleRepository(applicationContext)
            when (repository.refresh()) {
                is com.ntp.application_ai_assisstant.data.Result.Success -> Result.success()
                is com.ntp.application_ai_assisstant.data.Result.Error   -> Result.retry()
            }
        } catch (e: IOException) {
            Result.retry()        // lỗi tạm thời -> thử lại
        } catch (e: Exception) {
            Result.failure()      // lỗi vĩnh viễn -> bỏ
        }
    }
}
```

> ⚠️ `androidx.work.ListenableWorker.Result` **trùng tên** với `data/Result.kt` của dự án — vốn đã
> che khuất `kotlin.Result`. Trong Worker có tới ba `Result` khác nhau. Import tường minh hoặc đặt
> alias:
> ```kotlin
> import com.ntp.application_ai_assisstant.data.Result as DataResult
> ```

Ba giá trị trả về:

| | Nghĩa |
|---|---|
| `Result.success()` | Xong, không chạy lại |
| `Result.retry()` | Lỗi tạm thời — WorkManager tự lên lịch lại theo backoff |
| `Result.failure()` | Lỗi vĩnh viễn — bỏ luôn, không thử lại |

Dùng `CoroutineWorker` chứ không `Worker`: `doWork()` là `suspend`, chạy trên `Dispatchers.Default`
và huỷ được.

## 3. Ràng buộc

Đây là điểm mạnh nhất của WorkManager — mô tả *điều kiện* thay vì tự kiểm tra:

```kotlin
val constraints = Constraints.Builder()
    .setRequiredNetworkType(NetworkType.CONNECTED)   // chỉ chạy khi có mạng
    .setRequiresBatteryNotLow(true)
    .setRequiresCharging(false)
    .setRequiresStorageNotLow(true)
    .build()
```

Hệ thống chờ đủ điều kiện mới chạy — không cần `NetworkMonitor` để tự kiểm tra.

## 4. Lên lịch

```kotlin
// Chạy một lần
val request = OneTimeWorkRequestBuilder<SyncScheduleWorker>()
    .setConstraints(constraints)
    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
    .setInitialDelay(10, TimeUnit.SECONDS)
    .addTag("sync")
    .build()

WorkManager.getInstance(context).enqueue(request)

// Định kỳ — tối thiểu 15 phút
val periodic = PeriodicWorkRequestBuilder<SyncScheduleWorker>(1, TimeUnit.HOURS)
    .setConstraints(constraints)
    .build()

WorkManager.getInstance(context).enqueueUniquePeriodicWork(
    "sync_schedules",
    ExistingPeriodicWorkPolicy.KEEP,     // đã có thì giữ, đừng tạo trùng
    periodic,
)
```

`enqueueUniquePeriodicWork` **rất quan trọng**: gọi `enqueue` thẳng trong `onCreate` sẽ tạo một
job mới mỗi lần mở app, dẫn tới hàng chục job trùng nhau.

| Policy | Hành vi khi đã tồn tại |
|---|---|
| `KEEP` | Giữ job cũ, bỏ qua job mới |
| `REPLACE` / `UPDATE` | Thay bằng job mới |
| `CANCEL_AND_REENQUEUE` | Huỷ job đang chạy rồi xếp lại |

## 5. Truyền dữ liệu

```kotlin
val request = OneTimeWorkRequestBuilder<UploadWorker>()
    .setInputData(workDataOf("schedule_id" to id))
    .build()

// Trong Worker
val id = inputData.getString("schedule_id") ?: return Result.failure()

// Trả kết quả
return Result.success(workDataOf("uploaded_count" to 12))
```

`Data` giới hạn **10KB**. Dữ liệu lớn thì ghi vào Room/file rồi truyền id.

## 6. Theo dõi tiến trình

```kotlin
WorkManager.getInstance(context)
    .getWorkInfosForUniqueWorkLiveData("sync_schedules")
    .observe(this) { infos ->
        val state = infos.firstOrNull()?.state
        binding.progress.isVisible = state == WorkInfo.State.RUNNING
    }
```

Có cả bản `Flow`: `getWorkInfosForUniqueWorkFlow(...)`.

## 7. Chuỗi công việc

```kotlin
WorkManager.getInstance(context)
    .beginWith(listOf(compressWorker, validateWorker))   // chạy song song
    .then(uploadWorker)                                   // xong cả hai mới chạy
    .then(cleanupWorker)
    .enqueue()
```

Output của bước trước tự thành input của bước sau.

## 8. Foreground Worker — việc dài

Việc chạy quá lâu sẽ bị hệ thống dừng. Nếu người dùng cần thấy tiến trình:

```kotlin
override suspend fun doWork(): Result {
    setForeground(createForegroundInfo())
    // ... việc dài
}

private fun createForegroundInfo(): ForegroundInfo {
    val notification = NotificationCompat.Builder(applicationContext, "ai_assistant_channel")
        .setContentTitle("Đang đồng bộ")
        .setSmallIcon(R.mipmap.ic_launcher)
        .setOngoing(true)
        .build()

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ForegroundInfo(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    } else {
        ForegroundInfo(1, notification)
    }
}
```

Dùng được channel `ai_assistant_channel` mà `NotificationHelper` đã tạo sẵn. Nhưng nhớ: trên
Android 13+ thông báo cần quyền `POST_NOTIFICATIONS` mà app **chưa xin** — xem
[Permissions](../02-android-core/permissions.md).

Từ Android 14, foreground service **bắt buộc** khai báo `foregroundServiceType` trong manifest.

## 9. Ứng dụng cho dự án này

### a) Làm mới widget dày hơn 30 phút

`updatePeriodMillis` tối thiểu 30 phút. Muốn 15 phút:

```kotlin
class WidgetRefreshWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        val manager = AppWidgetManager.getInstance(applicationContext)
        val ids = manager.getAppWidgetIds(
            ComponentName(applicationContext, AI_Assistant::class.java)
        )
        ids.forEach { updateAppWidget(applicationContext, manager, it) }
        return Result.success()
    }
}
```

Lên lịch trong `AppWidgetProvider.onEnabled()`, huỷ trong `onDisabled()`.

### b) Đồng bộ lịch trình nền

Khi có Room, chạy `refresh()` định kỳ để app mở ra là có dữ liệu sẵn.

### c) Không dùng cho báo thức

`AlarmHelper` vẫn là công cụ đúng cho việc "đúng 8:00 sáng thì báo". WorkManager không đảm bảo
độ chính xác đó.

## 10. Kiểm thử

```powershell
# Xem tất cả job đang xếp hàng
adb shell dumpsys jobscheduler | Select-String "application_ai_assisstant"

# Ép chạy job ngay (cần job id từ lệnh trên)
adb shell cmd jobscheduler run -f com.ntp.application_ai_assisstant <jobId>
```

Trong unit test dùng `androidx.work:work-testing` với `TestListenableWorkerBuilder`.

## Xem thêm

- [Room & DataStore](room-datastore.md) — nơi Worker ghi dữ liệu
- [App Widget](../04-app-widget/app-widget.md)
- [BroadcastReceiver](../02-android-core/broadcast-receiver.md) — bàn giao việc nặng cho WorkManager
