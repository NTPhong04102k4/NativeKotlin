# Jetpack Glance

Glance là cách viết App Widget bằng **cú pháp Compose** thay cho `RemoteViews`. Nó không phải
Compose thật — Glance biên dịch composable của bạn **xuống** `RemoteViews`.

```
@Composable của bạn  ──Glance runtime──►  RemoteViews  ──IPC──►  Launcher
```

Vì đích đến vẫn là `RemoteViews`, **mọi giới hạn của widget vẫn còn nguyên** (xem
[App Widget §9](app-widget.md#9-giới-hạn-cần-nhớ)). Glance chỉ làm cho việc viết dễ chịu hơn.

## 1. ⚠️ Điều kiện tiên quyết với dự án này

> **Glance cần Jetpack Compose. Dự án hiện đang cấm Compose** (`CLAUDE.md`: *"no Jetpack Compose"*).

Dùng Glance nghĩa là phải mở cửa cho Compose vào build:

```kotlin
// app/build.gradle.kts — những thứ PHẢI thêm
android {
    buildFeatures {
        compose = true          // bật cùng viewBinding hiện có
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "..."
    }
}

dependencies {
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")
}
```

Chi phí thật cần cân nhắc trước khi quyết định:

| Ảnh hưởng | Mức độ |
|---|---|
| Thời gian build | Tăng đáng kể (thêm Compose compiler plugin) |
| Kích thước APK | +1–2 MB cho runtime Compose |
| Quy ước dự án | Phá vỡ "no Compose" — cần thống nhất lại với cả team |
| minSdk | Glance yêu cầu API 23+; dự án đang 24 → **không vướng** |
| Plugin Kotlin | Dự án **không apply** `org.jetbrains.kotlin.android` (AGP 9 tự lo). Bật Compose có thể phải sửa lại phần plugin — xem `CLAUDE.md` |

**Khuyến nghị:** widget hiện tại chỉ hiển thị một dòng text. Với nhu cầu đó, `RemoteViews` là đủ và
Glance không đáng. Chỉ cân nhắc Glance nếu widget về sau phức tạp lên nhiều (danh sách, nhiều
trạng thái, nhiều kích thước).

## 2. So sánh

| | RemoteViews | Glance |
|---|---|---|
| Cách viết UI | XML + `views.setTextViewText(id, ...)` | `@Composable` |
| Layout linh hoạt | Rất hạn chế | `Row`/`Column`/`Box` tự do |
| Nhiều kích thước widget | Tự xử lý thủ công | `SizeMode.Responsive` |
| State | Tự lưu bằng SharedPreferences | `GlanceStateDefinition` (DataStore) |
| Xử lý sự kiện | `PendingIntent` từng view | `actionRunCallback`, `actionStartActivity` |
| Cần Compose | Không | **Có** |
| Kiểm tra lúc biên dịch | Không (sai id → lỗi lúc chạy) | Có |

## 3. Widget hiện tại viết lại bằng Glance

Đối chiếu trực tiếp với `widget/AI_Assistant.kt`.

```kotlin
class AiAssistantGlanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Đọc dữ liệu Ở ĐÂY — hàm suspend, được phép I/O
        val title = loadTitle(context, id)

        provideContent {
            GlanceTheme {
                WidgetContent(title)
            }
        }
    }

    @Composable
    private fun WidgetContent(title: String) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .padding(16.dp)
                .clickable(actionStartActivity<DiscoveryActivity>()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = GlanceTheme.colors.onSurface,
                ),
            )
        }
    }
}

// Receiver: vẫn cần, vẫn khai báo trong manifest y như cũ
class AiAssistantWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AiAssistantGlanceWidget()
}
```

Điểm dễ chịu nhất: `provideGlance` là **`suspend`**, nên đọc database/network ngay trong đó được —
không cần `goAsync()` như với `BroadcastReceiver` thuần.

Manifest gần như không đổi:

```xml
<receiver
    android:name=".widget.AiAssistantWidgetReceiver"
    android:exported="false">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <meta-data
        android:name="android.appwidget.provider"
        android:resource="@xml/a_i__assistant_info" />
</receiver>
```

## 4. Component của Glance

Tên quen thuộc nhưng **không phải** component Compose thường — chúng nằm trong
`androidx.glance.*`, không phải `androidx.compose.*`:

| Glance | Tương đương Compose |
|---|---|
| `Column`, `Row`, `Box` | Giống tên |
| `Text` | `Text` |
| `Image` | `Image` |
| `Button` | `Button` |
| `LazyColumn` | `LazyColumn` (nhưng ít tính năng hơn nhiều) |
| `GlanceModifier` | `Modifier` |
| `Spacer` | `Spacer` |

**Không có:** `ConstraintLayout`, animation, `Canvas`, gesture tuỳ biến, `LazyRow`. Vì đích đến vẫn
là `RemoteViews`.

## 5. State và cập nhật

```kotlin
// Định nghĩa key
private val titleKey = stringPreferencesKey("widget_title")

// Đọc trong composable
@Composable
fun Content() {
    val prefs = currentState<Preferences>()
    val title = prefs[titleKey] ?: "AI Assistant"
    Text(title)
}

// Ghi từ bên ngoài (ví dụ sau khi đồng bộ dữ liệu)
suspend fun updateTitle(context: Context, newTitle: String) {
    GlanceAppWidgetManager(context)
        .getGlanceIds(AiAssistantGlanceWidget::class.java)
        .forEach { id ->
            updateAppWidgetState(context, id) { prefs -> prefs[titleKey] = newTitle }
            AiAssistantGlanceWidget().update(context, id)
        }
}
```

## 6. Xử lý sự kiện

```kotlin
// Mở Activity
Text("Mở app", modifier = GlanceModifier.clickable(actionStartActivity<DiscoveryActivity>()))

// Chạy logic nền rồi cập nhật lại widget
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        syncData()
        AiAssistantGlanceWidget().update(context, glanceId)
    }
}

Button("Làm mới", onClick = actionRunCallback<RefreshAction>())
```

`ActionCallback.onAction` cũng là `suspend` — dễ hơn hẳn so với `PendingIntent` + `BroadcastReceiver`
của cách cũ.

## 7. Nhiều kích thước widget

Đây là chỗ Glance thắng rõ nhất so với RemoteViews:

```kotlin
override val sizeMode = SizeMode.Responsive(
    setOf(
        DpSize(100.dp, 50.dp),      // nhỏ
        DpSize(250.dp, 100.dp),     // vừa
        DpSize(250.dp, 250.dp),     // lớn
    )
)

@Composable
fun Content() {
    val size = LocalSize.current
    if (size.width < 150.dp) CompactView() else ExpandedView()
}
```

Làm việc này bằng RemoteViews phải tạo nhiều layout XML và tự chọn theo
`OPTION_APPWIDGET_MIN_WIDTH` trong `onAppWidgetOptionsChanged`.

## 8. Lộ trình nếu quyết định dùng

1. Thống nhất với team về việc mở Compose (đây là quyết định kiến trúc, không phải kỹ thuật)
2. Bật `compose = true`, thêm dependency Glance vào **version catalog** (không hardcode chuỗi —
   xem quy ước trong `CLAUDE.md`)
3. Viết `GlanceAppWidget` mới **song song** với widget cũ, chưa đụng vào `AI_Assistant.kt`
4. Đổi `<receiver>` trong manifest sang receiver mới, giữ nguyên `a_i__assistant_info.xml`
5. Kiểm thử trên launcher thật (Pixel Launcher, One UI, MIUI hành xử khác nhau)
6. Xoá `AI_Assistant.kt` và `updateAppWidget` cũ

Bước 3–4 tách rời nghĩa là có thể quay đầu bất cứ lúc nào.

## Xem thêm

- [App Widget](app-widget.md) — cách hiện tại
- [WidgetKit (iOS)](widgetkit-ios.md)
- [Room & DataStore](../05-jetpack/room-datastore.md) — nơi Glance lưu state
