# App Widget (RemoteViews cổ điển)

Widget là mảnh giao diện của app **chạy trong process của Launcher**, không phải process của bạn.
Đó là nguồn gốc của mọi giới hạn bên dưới.

## 1. Bốn mảnh ghép

```
AndroidManifest.xml
   └─ <receiver> khai báo widget + trỏ tới metadata
         └─ res/xml/a_i__assistant_info.xml     kích thước, chu kỳ cập nhật, layout xem trước
               └─ res/layout/a_i__assistant.xml giao diện (RemoteViews, KHÔNG phải View thường)
                     └─ widget/AI_Assistant.kt  AppWidgetProvider: logic cập nhật
```

Trong dự án:

| Tệp | Vai trò |
|---|---|
| `widget/AI_Assistant.kt` | `AppWidgetProvider` — thực chất là một `BroadcastReceiver` |
| `widget/AI_AssistantConfigureActivity.kt` | Màn hình cấu hình khi người dùng thả widget ra màn hình chính |
| `res/layout/a_i__assistant.xml` | Giao diện widget |
| `res/xml/a_i__assistant_info.xml` | Metadata |

## 2. `AppWidgetProvider`

```kotlin
// widget/AI_Assistant.kt
class AI_Assistant : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        // Người dùng có thể đặt NHIỀU widget cùng loại -> cập nhật tất cả
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) deleteTitlePref(context, appWidgetId)
    }

    override fun onEnabled(context: Context) { /* widget ĐẦU TIÊN được tạo */ }
    override fun onDisabled(context: Context) { /* widget CUỐI CÙNG bị xoá */ }
}
```

| Callback | Khi nào gọi |
|---|---|
| `onUpdate` | Hết chu kỳ `updatePeriodMillis`, hoặc khi widget vừa được thêm, hoặc khi bạn tự gọi |
| `onEnabled` | Instance **đầu tiên** được tạo — nơi khởi tạo tài nguyên dùng chung |
| `onDisabled` | Instance **cuối cùng** bị xoá — nơi dọn dẹp |
| `onDeleted` | Mỗi lần một instance bị xoá — nơi xoá preference của riêng nó |

**`appWidgetId` là chìa khoá.** Mỗi widget trên màn hình chính có một id riêng. Dữ liệu cấu hình
phải lưu theo id, nếu không hai widget cùng loại sẽ đè lên nhau. Dự án làm đúng: `saveTitlePref`,
`loadTitlePref`, `deleteTitlePref` đều nhận `appWidgetId`.

## 3. `RemoteViews` — không phải View bình thường

```kotlin
internal fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
) {
    val widgetText = loadTitlePref(context, appWidgetId)
    val views = RemoteViews(context.packageName, R.layout.a_i__assistant)
    views.setTextViewText(R.id.appwidget_text, widgetText)
    appWidgetManager.updateAppWidget(appWidgetId, views)
}
```

`RemoteViews` là một **mô tả** giao diện được truyền qua IPC sang process của Launcher. Bạn không
cầm được đối tượng `View` nào — chỉ ra lệnh "đặt text của id này thành chuỗi kia".

### View được phép dùng trong widget

Chỉ một tập rất hạn chế:

| Nhóm | Được dùng |
|---|---|
| Layout | `FrameLayout`, `LinearLayout`, `RelativeLayout`, `GridLayout` |
| View | `TextView`, `ImageView`, `Button`, `ImageButton`, `ProgressBar`, `Chronometer`, `AnalogClock` |
| Danh sách | `ListView`, `GridView`, `StackView`, `AdapterViewFlipper` |
| API 31+ | `CheckBox`, `Switch`, `RadioButton`, `RadioGroup` |

**Không dùng được:** `ConstraintLayout`, `RecyclerView`, `CardView`, mọi component Material, và mọi
custom view. Dùng là widget hiện lỗi "Problem loading widget".

### Cách set giá trị

Không có `findViewById`. Mọi thao tác qua các hàm `setXxx(viewId, value)`:

```kotlin
views.setTextViewText(R.id.tv_title, "Xin chào")
views.setImageViewResource(R.id.iv_icon, R.drawable.ic_launcher_foreground)
views.setViewVisibility(R.id.progress, View.GONE)
views.setTextColor(R.id.tv_title, ContextCompat.getColor(context, R.color.text_primary))
views.setInt(R.id.root, "setBackgroundColor", Color.WHITE)
```

## 4. Metadata

```xml
<!-- res/xml/a_i__assistant_info.xml -->
<appwidget-provider
    android:minWidth="40dp"
    android:minHeight="40dp"
    android:updatePeriodMillis="86400000"
    android:initialLayout="@layout/a_i__assistant"
    android:configure="com.ntp...AI_AssistantConfigureActivity"
    android:previewImage="@drawable/example_appwidget_preview"
    android:resizeMode="horizontal|vertical"
    android:widgetCategory="home_screen" />
```

> ⚠️ **`updatePeriodMillis` tối thiểu là 30 phút (1800000).** Đặt nhỏ hơn thì hệ thống **âm thầm
> làm tròn lên** — không báo lỗi, chỉ là widget không cập nhật như bạn mong đợi. Cần cập nhật dày
> hơn thì dùng `WorkManager` hoặc `AlarmManager` rồi tự gọi `updateAppWidget`.

Dự án đang để `86400000` = 24 giờ.

## 5. Màn hình cấu hình

`AI_AssistantConfigureActivity` chạy khi người dùng thả widget ra màn hình chính. Có ba điểm bắt
buộc mà code đã làm đúng:

```kotlin
override fun onCreate(icicle: Bundle?) {
    super.onCreate(icicle)

    // 1. Mặc định CANCELED: người dùng bấm Back thì widget không được tạo
    setResult(RESULT_CANCELED)
    ...
    // 2. Lấy appWidgetId từ intent, không có thì thoát
    appWidgetId = intent.extras?.getInt(
        AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
    ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

    if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
}

private val onClickListener = View.OnClickListener {
    saveTitlePref(context, appWidgetId, widgetText)
    updateAppWidget(context, AppWidgetManager.getInstance(context), appWidgetId)

    // 3. Trả appWidgetId về, KHÔNG có bước này widget sẽ không xuất hiện
    setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
    finish()
}
```

Quên bước 3 là lỗi kinh điển: người dùng bấm "Add widget", màn hình đóng lại, và **không có gì xuất
hiện** trên home screen.

## 6. ⚠️ Thiếu: widget chưa mở được app

Widget hiện tại chỉ hiển thị text, **chạm vào không có gì xảy ra**. Cần gắn `PendingIntent`:

```kotlin
internal fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int,
) {
    val views = RemoteViews(context.packageName, R.layout.a_i__assistant)
    views.setTextViewText(R.id.appwidget_text, loadTitlePref(context, appWidgetId))

    val intent = Intent(context, DiscoveryActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

    val pendingIntent = PendingIntent.getActivity(
        context,
        appWidgetId,                                    // requestCode riêng cho từng widget
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    views.setOnClickPendingIntent(R.id.appwidget_text, pendingIntent)

    appWidgetManager.updateAppWidget(appWidgetId, views)
}
```

Hai chi tiết dễ sai:

- **`FLAG_IMMUTABLE` là bắt buộc từ API 31.** Thiếu nó app crash ngay khi tạo `PendingIntent`.
  (`AlarmHelper` trong dự án đã dùng đúng.)
- **`requestCode` phải khác nhau giữa các widget.** Dùng chung `0` thì mọi widget chia sẻ một
  `PendingIntent` và mở cùng một chỗ. Truyền `appWidgetId` là cách gọn nhất.

Theo quy ước điều hướng của dự án, đích đến nên đặt trong
[`AppRouter`](../02-android-core/navigation.md) thay vì tạo `Intent` thẳng trong widget.

## 7. Cập nhật widget từ bên ngoài

Khi dữ liệu đổi (ví dụ có lịch trình mới), chủ động đẩy cập nhật:

```kotlin
fun refreshAllWidgets(context: Context) {
    val manager = AppWidgetManager.getInstance(context)
    val ids = manager.getAppWidgetIds(ComponentName(context, AI_Assistant::class.java))
    ids.forEach { updateAppWidget(context, manager, it) }
}
```

Hoặc gửi broadcast:

```kotlin
context.sendBroadcast(
    Intent(context, AI_Assistant::class.java).apply {
        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
    }
)
```

Cần cập nhật định kỳ dày hơn 30 phút → `WorkManager` với `PeriodicWorkRequest` (tối thiểu 15 phút),
xem [WorkManager](../05-jetpack/workmanager.md).

## 8. Widget có danh sách

`RecyclerView` không dùng được. Muốn hiện danh sách thì dùng `ListView` + `RemoteViewsService`:

```kotlin
class ScheduleWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        ScheduleRemoteViewsFactory(applicationContext, intent)
}

class ScheduleRemoteViewsFactory(
    private val context: Context,
    intent: Intent,
) : RemoteViewsService.RemoteViewsFactory {

    private var items: List<Schedule> = emptyList()

    override fun onDataSetChanged() {
        items = loadFromDatabase()     // chạy trên background thread — được phép I/O ở đây
    }

    override fun getViewAt(position: Int): RemoteViews =
        RemoteViews(context.packageName, R.layout.item_widget_schedule).apply {
            setTextViewText(R.id.tv_title, items[position].title)
        }

    override fun getCount() = items.size
    // ... các hàm còn lại
}
```

Đăng ký service trong manifest với
`android:permission="android.permission.BIND_REMOTEVIEWS"`, rồi nối vào widget bằng
`views.setRemoteAdapter(R.id.list_view, intent)`.

## 9. Giới hạn cần nhớ

| Giới hạn | Chi tiết |
|---|---|
| Bộ nhớ | RemoteViews có ngân sách giới hạn. Bitmap lớn → `TransactionTooLargeException` |
| Thời gian | `onUpdate` chạy trên main thread và bị giới hạn như `BroadcastReceiver` (~10s). Việc nặng → `WorkManager` |
| Chu kỳ | `updatePeriodMillis` tối thiểu 30 phút, và không đánh thức thiết bị đang ngủ |
| View | Chỉ tập RemoteViews (§3) |
| Theme | Widget dùng theme của Launcher, không phải theme app. Dự án có `Theme.Application_AI_Assisstant.AppWidgetContainer` riêng cho việc này |

## 10. Bo góc theo hệ thống (API 31+)

Android 12 chuẩn hoá bo góc widget. Dự án đã xử lý bằng cách tách theme theo API:

```xml
<!-- values/themes.xml — dự phòng cho API < 31 -->
<style name="Theme.Application_AI_Assisstant.AppWidgetContainerParent" parent="android:Theme.DeviceDefault">
    <item name="appWidgetRadius">16dp</item>
    <item name="appWidgetInnerRadius">8dp</item>
</style>

<!-- values-v31/themes.xml — dùng giá trị của hệ thống -->
<style name="Theme.Application_AI_Assisstant.AppWidgetContainerParent"
       parent="@android:style/Theme.DeviceDefault.DayNight">
    <item name="appWidgetRadius">@android:dimen/system_app_widget_background_radius</item>
    <item name="appWidgetInnerRadius">@android:dimen/system_app_widget_inner_radius</item>
</style>
```

Ba thuộc tính `appWidgetPadding` / `appWidgetRadius` / `appWidgetInnerRadius` được khai báo trong
`res/values/attrs.xml`.

## Xem thêm

- [Jetpack Glance](jetpack-glance.md) — viết widget bằng Compose thay cho RemoteViews
- [WidgetKit (iOS)](widgetkit-ios.md) — đối chiếu bên iOS
- [Navigation](../02-android-core/navigation.md) — nơi đặt đích đến của `PendingIntent`
