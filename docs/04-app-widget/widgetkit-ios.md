# WidgetKit (iOS) — tài liệu định hướng

> **Bối cảnh:** repo này là app Android thuần. Trang này viết cho **bản iOS tương ứng** của sản
> phẩm, để hai nền tảng có cùng mô hình widget và cùng ngôn ngữ khi trao đổi. Không có code nào ở
> đây chạy trong repo hiện tại.

## 1. Đối chiếu Android ↔ iOS

| Khái niệm | Android | iOS (WidgetKit) |
|---|---|---|
| Khai báo widget | `<receiver>` + `AppWidgetProvider` | `@main struct ... : Widget` trong Widget Extension |
| Mô tả giao diện | `RemoteViews` / Glance | **SwiftUI** (bắt buộc) |
| Cung cấp dữ liệu | `onUpdate` | `TimelineProvider` |
| Chu kỳ cập nhật | `updatePeriodMillis` (≥ 30 phút) | Timeline entries + ngân sách hệ thống |
| Nhiều kích thước | Nhiều layout / `SizeMode` | `WidgetFamily` (`.systemSmall/.systemMedium/.systemLarge`) |
| Cấu hình | `configure` Activity | `AppIntentConfiguration` (iOS 17+) |
| Chạm vào widget | `PendingIntent` | `widgetURL` / `Link` / `AppIntent` |
| Chia sẻ dữ liệu với app | Cùng process/DB | **App Group** (bắt buộc) |

Khác biệt lớn nhất về tư duy:

> **Android: app đẩy cập nhật khi muốn. iOS: app nộp trước một "dòng thời gian", hệ thống quyết
> định khi nào hiển thị.**

Trên iOS bạn **không** ép widget cập nhật ngay được. Bạn mô tả trước các mốc thời gian và nội dung
tương ứng; WidgetKit tự chọn thời điểm render dựa trên ngân sách năng lượng.

## 2. Cấu trúc một Widget Extension

Trong Xcode: **File → New → Target → Widget Extension**. Sinh ra một target riêng, tách khỏi app
chính.

```
AIAssistantWidget/
├── AIAssistantWidget.swift        Widget + TimelineProvider + View
├── Info.plist
└── Assets.xcassets
```

## 3. Ba mảnh của WidgetKit

### 3.1. Entry — một "khung hình" tại một thời điểm

```swift
struct ScheduleEntry: TimelineEntry {
    let date: Date                  // BẮT BUỘC: thời điểm hiển thị khung này
    let title: String
    let location: String
}
```

### 3.2. TimelineProvider — nộp trước các khung hình

```swift
struct ScheduleProvider: TimelineProvider {

    // Ảnh giữ chỗ khi widget chưa có dữ liệu (hiện trong widget gallery)
    func placeholder(in context: Context) -> ScheduleEntry {
        ScheduleEntry(date: Date(), title: "Họp Team Marketing", location: "Phòng họp số 2")
    }

    // Bản xem trước nhanh
    func getSnapshot(in context: Context, completion: @escaping (ScheduleEntry) -> Void) {
        completion(placeholder(in: context))
    }

    // Dòng thời gian thật
    func getTimeline(in context: Context, completion: @escaping (Timeline<ScheduleEntry>) -> Void) {
        Task {
            let schedules = await SharedStore.loadTodaySchedules()

            // Mỗi lịch trình là một khung, hiển thị đúng vào giờ của nó
            let entries = schedules.map {
                ScheduleEntry(date: $0.startTime, title: $0.title, location: $0.location)
            }

            // .after: xin hệ thống nạp lại timeline sau 1 giờ
            completion(Timeline(entries: entries, policy: .after(Date().addingTimeInterval(3600))))
        }
    }
}
```

Ba `TimelineReloadPolicy`:

| Policy | Nghĩa |
|---|---|
| `.atEnd` | Nạp lại khi dùng hết entry cuối cùng |
| `.after(date)` | Nạp lại sau mốc thời gian cụ thể |
| `.never` | Chỉ nạp lại khi app chủ động yêu cầu |

### 3.3. View — SwiftUI

```swift
struct ScheduleWidgetView: View {
    var entry: ScheduleEntry
    @Environment(\.widgetFamily) var family

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(entry.date, style: .time)
                .font(.headline)
                .foregroundStyle(.tint)

            Text(entry.title)
                .font(.body.bold())
                .lineLimit(1)

            if family != .systemSmall {
                Text(entry.location)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .containerBackground(.fill.tertiary, for: .widget)   // BẮT BUỘC từ iOS 17
        .widgetURL(URL(string: "aiassistant://schedule"))
    }
}
```

### 3.4. Ghép lại

```swift
@main
struct AIAssistantWidget: Widget {
    let kind = "AIAssistantScheduleWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: ScheduleProvider()) { entry in
            ScheduleWidgetView(entry: entry)
        }
        .configurationDisplayName("Lịch trình")
        .description("Xem sự kiện sắp tới của bạn.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
    }
}
```

## 4. App Group — chia sẻ dữ liệu

Widget Extension là **process riêng, sandbox riêng**. Không đọc được `UserDefaults.standard` hay
thư mục Documents của app chính. Phải bật **App Group** ở cả hai target:

```
Signing & Capabilities → + Capability → App Groups
→ group.com.ntp.aiassistant
```

```swift
enum SharedStore {
    static let suiteName = "group.com.ntp.aiassistant"

    static var defaults: UserDefaults {
        UserDefaults(suiteName: suiteName)!
    }

    // Dùng chung container cho Core Data / SQLite
    static var containerURL: URL {
        FileManager.default.containerURL(
            forSecurityApplicationGroupIdentifier: suiteName
        )!
    }
}
```

Đây là khác biệt lớn nhất so với Android — bên Android widget và app cùng process nên
`SharedPreferences` dùng chung được ngay (đúng như `saveTitlePref` đang làm).

## 5. Ép widget nạp lại từ app chính

```swift
import WidgetKit

// Sau khi đồng bộ dữ liệu xong
WidgetCenter.shared.reloadTimelines(ofKind: "AIAssistantScheduleWidget")

// Hoặc tất cả widget của app
WidgetCenter.shared.reloadAllTimelines()
```

Đây là *đề nghị*, không phải *lệnh* — hệ thống vẫn có quyền hoãn nếu app đang tiêu tốn ngân sách.

## 6. Widget tương tác (iOS 17+)

Trước iOS 17, chạm vào widget chỉ mở được app. Từ iOS 17 có nút và toggle chạy ngay trong widget:

```swift
struct ToggleDoneIntent: AppIntent {
    static var title: LocalizedStringResource = "Đánh dấu hoàn thành"

    @Parameter(title: "ID") var scheduleId: String

    func perform() async throws -> some IntentResult {
        await SharedStore.markDone(scheduleId)
        return .result()
    }
}

// Trong View
Button(intent: ToggleDoneIntent(scheduleId: entry.id)) {
    Image(systemName: "checkmark.circle")
}
```

Tương đương bên Android là `actionRunCallback` của Glance
([Jetpack Glance §6](jetpack-glance.md#6-xử-lý-sự-kiện)).

## 7. Deep link vào app

```swift
.widgetURL(URL(string: "aiassistant://schedule"))
```

Bắt ở app chính:

```swift
.onOpenURL { url in
    if url.host == "schedule" { navigateToSchedule() }
}
```

Bên Android, việc này làm bằng `PendingIntent` trỏ tới Activity — và theo quy ước của repo, đích
đến phải khai báo trong [`AppRouter`](../02-android-core/navigation.md).

## 8. Giới hạn cần biết

| Giới hạn | Chi tiết |
|---|---|
| Ngân sách nạp lại | ~40–70 lần/ngày cho widget hay dùng. Hết ngân sách là widget đứng im |
| Bộ nhớ | Extension bị giới hạn khoảng 30MB — vượt là bị hệ thống giết |
| Không animation | Widget là ảnh tĩnh. Chỉ `Text(date, style: .timer)` là tự chạy |
| Không cuộn, không gesture | Chỉ nút/link |
| Không network trong View | Mọi việc lấy dữ liệu phải nằm trong `TimelineProvider` |
| SwiftUI bắt buộc | Không dùng được UIKit |

## 9. Nếu làm cả hai nền tảng

Giữ cho hai bên khớp nhau:

1. **Thống nhất "widget hiển thị cái gì"** trước khi viết code — Android và iOS nên cùng một tập
   dữ liệu, cùng một tập kích thước.
2. **Chuẩn hoá deep link.** Cùng một scheme (`aiassistant://schedule`) cho cả hai, để backend và
   thông báo đẩy dùng chung một định dạng.
3. **Chấp nhận khác biệt về tần suất cập nhật.** Android chủ động được, iOS thì không. Đừng thiết
   kế tính năng phụ thuộc vào widget cập nhật tức thời.
4. **Chia sẻ dữ liệu khác nhau:** Android dùng chung process; iOS bắt buộc App Group. Lên kế hoạch
   tầng lưu trữ từ đầu.

## Xem thêm

- [App Widget (Android)](app-widget.md)
- [Jetpack Glance](jetpack-glance.md)
- [Native Bridge](../07-tich-hop/native-bridge.md) — nếu app dùng lớp cross-platform
