# Responsive layout

## 1. Qualifier: hệ thống tự chọn tài nguyên

Android chọn thư mục tài nguyên **khớp nhất** với thiết bị lúc chạy. Code không cần biết gì.

```
res/
├── layout/activity_login.xml            mặc định (điện thoại dọc)
├── layout-w936dp/activity_login.xml     màn hình rộng ≥ 936dp
└── layout-w1240dp/activity_login.xml    màn hình rộng ≥ 1240dp
```

Điện thoại rộng 411dp → dùng `layout/`. Tablet 1000dp → dùng `layout-w936dp/`. Màn hình gập mở ra
1280dp → dùng `layout-w1240dp/`.

## 2. `w` (available width) chứ không phải `sw` (smallest width)

Khác biệt này quan trọng:

| Qualifier | Ý nghĩa | Khi xoay màn hình |
|---|---|---|
| `sw600dp` | Cạnh **ngắn nhất** của màn hình ≥ 600dp | **Không đổi** — thuộc tính của thiết bị |
| `w600dp` | Chiều rộng **hiện tại** ≥ 600dp | **Đổi** — xoay ngang là đổi tài nguyên |

Dự án dùng `w`, nghĩa là bố cục **phản ứng theo xoay màn hình và theo chế độ chia đôi màn hình**.
Đây là lựa chọn hiện đại và đúng cho thiết bị gập.

## 3. Tài nguyên có biến thể trong dự án

### Layout

| Layout | `layout/` | `-w936dp/` | `-w1240dp/` |
|---|---|---|---|
| `activity_login.xml` | ✓ | ✓ | ✓ |
| `content_scrolling.xml` | ✓ | ✓ | ✓ |

Khác biệt: bản mặc định dùng `NestedScrollView` cuộn toàn màn hình; hai bản rộng dùng một
`LinearLayout` rộng cố định 480dp căn giữa — vì form đăng nhập kéo dài hết chiều ngang tablet thì
rất xấu.

### Dimension

| Tên | `values/` | `-w600dp/` | `-w1240dp/` | `-land/` |
|---|---|---|---|---|
| `activity_horizontal_margin` | 16dp | 48dp | 200dp | 48dp |
| `fab_margin` | 16dp | 48dp | 200dp | 48dp |
| `text_margin` | 16dp | 48dp | — | 48dp |

Đây là cách responsive **rẻ nhất**: chỉ đổi lề, không nhân bản cả file layout. Ưu tiên cách này
trước khi nghĩ tới việc tạo layout riêng.

> `values-w1240dp/` không định nghĩa `text_margin`, nên ở màn hình ≥1240dp giá trị này lấy từ
> `values-w600dp/` (48dp). Đó là hành vi đúng của hệ thống resource — không phải lỗi, nhưng cần biết
> để không bất ngờ.

## 4. Quy tắc bắt buộc giữa các biến thể

### 4.1. Root id phải giống nhau — nếu không **build fail**

```
> Task :app:dataBindingGenBaseClassesDebug FAILED
  Configurations for activity_login.xml must agree on the root element's ID.
```

Đây là lỗi có thật đã xảy ra trong repo: `layout/` và `layout-w936dp/` có
`android:id="@+id/main"` ở thẻ gốc, còn `layout-w1240dp/` thì thiếu. Toàn bộ build đứng lại ở bước
xử lý tài nguyên — không phải cảnh báo, mà là lỗi chặn.

**Root element của mọi biến thể phải: hoặc đều có id giống nhau, hoặc đều không có id.**

### 4.2. Id con nên có mặt ở mọi biến thể

Nếu một id chỉ có ở vài biến thể, ViewBinding sinh field **nullable** và mọi chỗ dùng phải thêm
`?.`. Xem [ViewBinding §4](viewbinding.md#4-field-nullable--bẫy-lớn-nhất-của-dự-án-này).

`activity_login.xml` đang vướng đúng chuyện này:

| Id | `layout/` | `-w936dp/` | `-w1240dp/` | Kiểu field |
|---|---|---|---|---|
| `username`, `password`, `login` | ✓ | ✓ | ✓ | không null |
| `btn_google`, `btn_biometric` | ✓ | ✓ | ✓ | không null¹ |
| `loading` | ✓ | ✗ | ✗ | **nullable** |

¹ Trong code vẫn viết `binding.btnGoogle?.` — dấu `?` này có thể đã thừa sau khi các biến thể được
đồng bộ. Kiểm tra lại trước khi bỏ đi.

### 4.3. Sửa một biến thể thì sửa cả bộ

Đây là chi phí thật của việc nhân bản layout: **mỗi thay đổi nhân lên 3 lần**. Vì vậy chỉ tạo biến
thể khi bố cục thực sự khác về cấu trúc. Nếu chỉ khác kích thước/lề → dùng `dimens.xml` có
qualifier.

## 5. Breakpoint chuẩn của Google

| Window size class | Chiều rộng | Thiết bị điển hình | Bố cục gợi ý |
|---|---|---|---|
| **Compact** | < 600dp | Điện thoại dọc | Một cột, bottom navigation |
| **Medium** | 600–839dp | Tablet nhỏ, điện thoại ngang, máy gập mở | Hai cột, navigation rail |
| **Expanded** | ≥ 840dp | Tablet lớn, desktop, ChromeOS | Ba cột, navigation drawer cố định |

Breakpoint của dự án (`w936dp`, `w1240dp`) **không khớp** với bộ chuẩn (600/840). Không sai — chọn
theo nội dung là hợp lý — nhưng nếu sau này dùng `WindowSizeClass` của Jetpack thì hai hệ sẽ lệch
nhau. Cân nhắc chuyển sang `w600dp` / `w840dp` để đồng nhất.

Với `values-w600dp/dimens.xml` đã dùng 600dp, hiện tại hệ thống đang có **hai bộ breakpoint khác
nhau**: dimens theo 600/1240, layout theo 936/1240.

## 6. Bottom navigation trên màn hình rộng

Theo Material guideline, bottom navigation chỉ dành cho **compact**. Từ medium trở lên nên dùng
`NavigationRailView` (thanh dọc bên trái).

Cả ba tab của dự án hiện đều dùng `BottomNavigationView` ở mọi kích thước. Trên tablet, thanh điều
hướng kéo hết chiều ngang trông rất trống trải.

Muốn cải thiện thì tạo biến thể layout cho tab, thay `BottomNavigationView` bằng
`NavigationRailView`. Cả hai đều kế thừa `NavigationBarView` nên `AppRouter` **không cần sửa** —
chỉ cần đổi kiểu tham số:

```kotlin
fun bind(
    activity: AppCompatActivity,
    navigationView: NavigationBarView,     // thay vì BottomNavigationView
    currentItemId: Int,
)
```

Đây là một lý do nữa để gom điều hướng về một chỗ: đổi loại thanh điều hướng chỉ đụng một file.

## 7. Các qualifier khác

| Qualifier | Nghĩa | Ví dụ |
|---|---|---|
| `-land` / `-port` | Hướng màn hình | `values-land/dimens.xml` (đã dùng) |
| `-night` | Dark mode | `values-night/themes.xml` (đã dùng) |
| `-v31` | API ≥ 31 | `values-v31/themes.xml` (đã dùng) |
| `-vi` / `-en` | Ngôn ngữ | *(chưa có — app chỉ có tiếng Việt hardcode trong `values/`)* |
| `-hdpi`…`-xxxhdpi` | Mật độ điểm ảnh | `mipmap-*` cho icon launcher |

Qualifier **kết hợp được** và có thứ tự ưu tiên cố định:
`values-night-v31/` = dark mode **và** API ≥ 31.

## 8. Kiểm thử

```powershell
# Đổi kích thước màn hình ảo
adb shell wm size 1280x800
adb shell wm density 240
adb shell wm size reset

# Bật chia đôi màn hình để kiểm tra qualifier "w"
# (Developer options -> Force activities to be resizable)
```

Trong Android Studio, dùng nút chọn thiết bị ở thanh Preview để xem nhiều kích thước cùng lúc mà
không cần chạy app.

## Xem thêm

- [ViewBinding](viewbinding.md) — hệ quả của việc có nhiều biến thể
- [Theming & tokens](theming-tokens.md) — `dimens.xml` theo qualifier
- [Navigation](../02-android-core/navigation.md)
