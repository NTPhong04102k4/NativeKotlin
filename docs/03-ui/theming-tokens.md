# Theming & Design tokens

## 1. Ba tầng, đi theo một chiều

```
colors.xml        giá trị thô          #FF0284C7
     ↓
token ngữ nghĩa   primary_500, text_primary, background_primary
     ↓
themes.xml        thuộc tính theme     colorPrimary, colorSurface
     ↓
styles.xml        style component      Button.Primary, InputField
     ↓
layout XML        style="@style/Button.Primary"
```

**Quy tắc:** layout chỉ được chạm vào tầng dưới cùng. Không hardcode `#FF0284C7`, cũng không dùng
thẳng `@color/sky_500` trong layout.

Vì sao? Đổi màu thương hiệu chỉ cần sửa **một dòng** trong `colors.xml` thay vì đi tìm khắp 12 file
layout. Và chỉ có cách này thì dark mode mới hoạt động.

## 2. Bảng màu của dự án

`res/values/colors.xml` dùng thang màu kiểu Tailwind (50 → 900, số càng lớn càng đậm):

| Nhóm | Thang | Ghi chú |
|---|---|---|
| `slate_*` | 50–900 | Màu trung tính chính, dùng cho chữ và nền |
| `gray_*` | 50–900 | Trung tính phụ |
| `sky_*` | 50–900 | Màu thương hiệu |
| `blue_*`, `cyan_*` | 50–900 | Màu phụ trợ |
| `red_*`, `amber_*`, `emerald_*` | 50–900 | Trạng thái |

Bên trên là lớp **token ngữ nghĩa** — đây mới là thứ nên dùng:

```xml
<!-- Alias: đổi thương hiệu chỉ cần sửa ở đây -->
<color name="primary_500">@color/sky_500</color>

<!-- Token theo vai trò -->
<color name="background_primary">@color/white</color>
<color name="background_secondary">@color/slate_50</color>
<color name="text_primary">@color/slate_900</color>
<color name="text_secondary">@color/slate_600</color>
<color name="text_disabled">@color/slate_400</color>
<color name="divider">@color/slate_200</color>

<color name="error_main">@color/red_500</color>
<color name="warning_main">@color/amber_500</color>
<color name="success_main">@color/emerald_500</color>
<color name="info_main">@color/cyan_500</color>
```

Cách đặt tên này rất tốt: `text_primary` nói **vai trò**, `slate_900` nói **giá trị**. Layout dùng
vai trò thì đổi giá trị không phải sửa layout.

## 3. Theme

```xml
<!-- res/values/themes.xml -->
<style name="Theme.Application_AI_Assisstant" parent="Theme.Material3.DayNight.NoActionBar">
    <item name="colorPrimary">@color/primary_500</item>
    <item name="colorOnPrimary">@color/white</item>
    <item name="colorSecondary">@color/cyan_500</item>
    <item name="colorOnSecondary">@color/white</item>

    <item name="android:windowBackground">@color/background_primary</item>
    <item name="colorSurface">@color/background_primary</item>
    <item name="colorOnSurface">@color/text_primary</item>
    <item name="colorSurfaceVariant">@color/background_secondary</item>
    <item name="colorOnSurfaceVariant">@color/text_secondary</item>

    <item name="android:statusBarColor">@color/background_primary</item>
    <item name="android:windowLightStatusBar">true</item>
</style>
```

Cặp `colorX` / `colorOnX` là ý tưởng cốt lõi của Material: `colorOnPrimary` là màu chữ/icon đặt
**lên trên** nền `colorPrimary`. Đi theo cặp thì độ tương phản luôn đúng.

### Danh sách theme trong dự án

| Theme | Dùng ở đâu |
|---|---|
| `Theme.Application_AI_Assisstant` | Theme gốc, khai báo ở `<application>` |
| `Theme.Application_AI_Assisstant.NoActionBar` | `ScrollingActivity` |
| `Theme.App.Starting` | Splash screen của `LoginActivity` |
| `Theme.Application_AI_Assisstant.AppWidgetContainer` | Nền widget |

Chú ý cách đặt tên có dấu chấm: `Theme.A.B` **tự động kế thừa** `Theme.A` mà không cần khai báo
`parent`. Đó là lý do `Theme.Application_AI_Assisstant.NoActionBar` chỉ cần ghi hai thuộc tính khác
biệt.

## 4. Splash screen

```xml
<style name="Theme.App.Starting" parent="Theme.SplashScreen">
    <item name="windowSplashScreenBackground">@color/background_primary</item>
    <item name="windowSplashScreenAnimatedIcon">@mipmap/ic_launcher</item>
    <item name="postSplashScreenTheme">@style/Theme.Application_AI_Assisstant</item>
</style>
```

Cơ chế: `LoginActivity` khai báo `android:theme="@style/Theme.App.Starting"` trong manifest, rồi
gọi `installSplashScreen()` **trước** `super.onCreate()`. Hàm đó đổi theme sang
`postSplashScreenTheme`. Thiếu `installSplashScreen()` thì app kẹt lại ở theme splash.

## 5. ⚠️ Dark mode hiện đang hỏng

Theme gốc là `DayNight` và có `res/values-night/themes.xml`, nhưng bản override **chỉ đổi màu
thương hiệu, không đổi màu nền và chữ**:

```xml
<!-- values-night/themes.xml — những gì ĐANG có -->
<item name="colorPrimary">@color/primary_300</item>
<item name="colorOnPrimary">@color/gray_900</item>
<item name="colorSecondary">@color/cyan_300</item>
<item name="android:statusBarColor">@color/gray_900</item>
<item name="android:windowLightStatusBar">false</item>
```

Bốn thuộc tính sau **không** được override và cũng **không có `values-night/colors.xml`**:

| Thuộc tính | Trỏ tới | Giá trị ở dark mode |
|---|---|---|
| `android:windowBackground` | `background_primary` | **trắng** |
| `colorSurface` | `background_primary` | **trắng** |
| `colorOnSurface` | `text_primary` | slate_900 (gần đen) |
| `colorSurfaceVariant` | `background_secondary` | slate_50 (gần trắng) |

Kết quả trên thiết bị bật dark mode: **thanh trạng thái tối nhưng toàn bộ nội dung vẫn nền trắng**.

### Cách sửa

Tạo `res/values-night/colors.xml` và lật các token ngữ nghĩa — **không** cần đụng vào
`values-night/themes.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="background_primary">@color/slate_900</color>
    <color name="background_secondary">@color/slate_800</color>
    <color name="text_primary">@color/slate_50</color>
    <color name="text_secondary">@color/slate_400</color>
    <color name="text_disabled">@color/slate_600</color>
    <color name="divider">@color/slate_700</color>
</resources>
```

Đây chính là phần thưởng của việc có lớp token ngữ nghĩa: **6 dòng là xong dark mode**, không phải
sửa layout nào. Nếu layout hardcode `@color/white` thì đã phải sửa từng chỗ.

Chỉ nên override trong `values-night/themes.xml` những gì **không** biểu diễn được bằng token, ví dụ
`android:windowLightStatusBar` (là boolean, không phải màu).

### Kiểm thử dark mode

```powershell
adb shell "cmd uimode night yes"    # bật
adb shell "cmd uimode night no"     # tắt
```

Hoặc trong code, ép theo lựa chọn của người dùng:

```kotlin
AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
```

`res/xml/root_preferences.xml` của `SettingsActivity` là chỗ hợp lý để cắm tuỳ chọn này.

## 6. Style component

`res/values/styles.xml` đã định nghĩa sẵn một bộ style. **Dùng lại chúng thay vì set thuộc tính
từng view.**

| Nhóm | Style |
|---|---|
| Chữ | `Text`, `Text.Heading1`, `Text.Heading2`, `Text.Heading3`, `Text.BodyPrimary`, `Text.BodySecondary`, `Text.Caption` |
| Nút | `Button.Primary`, `Button.Social`, `Button.Outlined` |
| Nhập liệu | `InputField` |
| Container | `Container.Card`, `ListItem` |
| Chip | `MultiSelectChip` |
| Bo góc | `ShapeAppearance.App.Card`, `ShapeAppearance.App.Input` |

```xml
<!-- ✓ Đúng -->
<TextView style="@style/Text.Heading1" ... />

<!-- ✗ Sai: lặp lại giá trị, đổi thiết kế phải sửa khắp nơi -->
<TextView android:textSize="32sp" android:textStyle="bold" android:textColor="#0F172A" ... />
```

## 7. Dimension theo kích thước màn hình

`dimens.xml` cũng có biến thể theo qualifier — cùng một tên, giá trị khác nhau:

| Tên | `values/` | `values-w600dp/` | `values-w1240dp/` | `values-land/` |
|---|---|---|---|---|
| `activity_horizontal_margin` | 16dp | 48dp | 200dp | 48dp |
| `fab_margin` | 16dp | 48dp | 200dp | 48dp |
| `text_margin` | 16dp | 48dp | — | 48dp |

Layout chỉ ghi `@dimen/activity_horizontal_margin`, hệ thống tự chọn giá trị. Xem
[Responsive layout](responsive-layout.md).

## 8. Dynamic color (Material You)

Từ Android 12, app lấy được bảng màu sinh từ hình nền người dùng:

```kotlin
// trong Application.onCreate()
DynamicColors.applyToActivitiesIfAvailable(this)
```

Dự án **chưa** bật (và cũng chưa có lớp `Application` tuỳ biến). Có một dấu vết nhỏ:
`Theme.Application_AI_Assisstant.PopupOverlay` kế thừa
`ThemeOverlay.Material3.DynamicColors.Light`, nhưng theme này không được layout nào dùng.

Cân nhắc: bật dynamic color nghĩa là **từ bỏ màu thương hiệu** — `colorPrimary` sẽ do hình nền
quyết định. Với app có nhận diện thương hiệu rõ ràng như bảng `sky_*` ở đây, không bật là lựa chọn
hợp lý.

## 9. Lint: `HardcodedText` là `error`

```xml
<!-- lint.xml -->
<issue id="HardcodedText" severity="error" />
```

Mọi chuỗi hiển thị phải nằm trong `strings.xml`, nếu không **build fail**. Với dữ liệu mẫu chỉ để
xem trước trong Android Studio thì dùng `tools:text` — nó không ship ra APK và không bị lint bắt:

```xml
<TextView
    android:id="@+id/tv_task_title"
    style="@style/Text.BodyPrimary"
    tools:text="Họp Team Marketing" />   <!-- adapter sẽ set giá trị thật -->
```

`item_discovery_card.xml` và `item_schedule.xml` đã theo quy ước này.

## Xem thêm

- [Material 2 vs Material 3](material2-vs-material3.md)
- [Responsive layout](responsive-layout.md)
