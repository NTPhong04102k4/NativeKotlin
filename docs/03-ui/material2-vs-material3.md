# Material 2 vs Material 3

Dự án dùng **Material 3** (`Theme.Material3.DayNight.NoActionBar`) qua thư viện
`com.google.android.material:material`. Tài liệu này giải thích khác biệt và những chỗ dễ trộn lẫn
hai bộ.

## 1. Khác biệt cốt lõi

| | Material 2 (2018) | Material 3 (2021, "Material You") |
|---|---|---|
| Theme gốc | `Theme.MaterialComponents.*` | `Theme.Material3.*` |
| Hệ màu | 12 vai trò (primary, primaryVariant, surface…) | ~26 vai trò, luôn theo cặp `colorX`/`colorOnX` |
| Màu động | Không | Có — sinh từ hình nền (API 31+) |
| Độ nổi khối | Bóng đổ (elevation shadow) | **Phủ màu** (surface tint) thay cho bóng |
| Bo góc | Nhỏ, đồng đều | To hơn, khác nhau theo loại component |
| Nút | Chữ nhật bo nhẹ | Bo tròn hẳn (pill), cao 40dp |
| Bảng chữ | `TextAppearance.MaterialComponents.*` | `TextAppearance.Material3.*` |
| Thanh dưới | `BottomNavigationView` | `NavigationBarView` (BottomNavigationView vẫn dùng được) |

## 2. Ánh xạ thuộc tính màu

Bảng tra khi chuyển từ M2 sang M3 hoặc khi đọc code/StackOverflow cũ:

| Material 2 | Material 3 | Ghi chú |
|---|---|---|
| `colorPrimary` | `colorPrimary` | Giữ nguyên tên |
| `colorPrimaryVariant` | `colorPrimaryContainer` | **Đổi ý nghĩa**, không chỉ đổi tên |
| `colorSecondaryVariant` | `colorSecondaryContainer` | |
| `colorOnPrimary` | `colorOnPrimary` | |
| `colorSurface` | `colorSurface` | M3 thêm `colorSurfaceVariant`, `colorSurfaceContainer*` |
| `colorError` | `colorError` + `colorErrorContainer` | |
| `android:textColorPrimary` | `colorOnSurface` | Nên dùng bản M3 |
| — | `colorSurfaceTint` | Mới ở M3: màu phủ tạo cảm giác nổi khối |
| — | `colorOutline`, `colorOutlineVariant` | Mới: viền và đường kẻ |

Theme của dự án dùng: `colorPrimary`, `colorOnPrimary`, `colorSecondary`, `colorOnSecondary`,
`colorSurface`, `colorOnSurface`, `colorSurfaceVariant`, `colorOnSurfaceVariant`, `colorContainer`,
`colorOnContainer` — xem [Theming & tokens](theming-tokens.md).

## 3. Ánh xạ style component

| Material 2 | Material 3 |
|---|---|
| `Widget.MaterialComponents.Button` | `Widget.Material3.Button` |
| `Widget.MaterialComponents.Button.OutlinedButton` | `Widget.Material3.Button.OutlinedButton` |
| `Widget.MaterialComponents.Button.TextButton` | `Widget.Material3.Button.TextButton` |
| `Widget.MaterialComponents.CardView` | `Widget.Material3.CardView.Elevated` / `.Filled` / `.Outlined` |
| `Widget.MaterialComponents.TextInputLayout.OutlinedBox` | `Widget.Material3.TextInputLayout.OutlinedBox` |
| `Widget.MaterialComponents.Chip.Filter` | `Widget.Material3.Chip.Filter` |
| `ShapeAppearance.MaterialComponents.MediumComponent` | `ShapeAppearance.Material3.MediumComponent` |

`res/values/styles.xml` của dự án đã dùng đúng bộ M3:

```xml
<style name="Button.Primary" parent="Widget.Material3.Button">
<style name="Button.Social" parent="Widget.Material3.Button.OutlinedButton">
<style name="InputField" parent="Widget.Material3.TextInputLayout.OutlinedBox">
<style name="Container.Card" parent="Widget.Material3.CardView.Elevated">
<style name="MultiSelectChip" parent="Widget.Material3.Chip.Filter">
<style name="ShapeAppearance.App.Card" parent="ShapeAppearance.Material3.MediumComponent">
```

## 4. Bẫy khi trộn hai bộ

### 4.1. Style M2 dưới theme M3 → crash

Đây là lỗi hay gặp nhất:

```
java.lang.IllegalArgumentException: The style on this component requires your app theme
to be Theme.MaterialComponents (or a descendant).
```

Nguyên nhân: component dùng style `Widget.MaterialComponents.*` nhưng theme là `Theme.Material3.*`
(hoặc ngược lại). Component M2 đòi các thuộc tính mà theme M3 không cung cấp.

**Quy tắc: chọn một bộ và dùng nhất quán.** Không trộn.

### 4.2. `AppCompat` không đủ cho component Material

```xml
<!-- Theme.AppCompat không có colorPrimaryVariant, colorSurface... -->
<style name="AppTheme" parent="Theme.AppCompat.Light" />   <!-- ✗ với MaterialButton -->
```

Theme phải kế thừa `Theme.MaterialComponents.*` hoặc `Theme.Material3.*`.

### 4.3. Elevation ở M3 hoạt động khác

M3 thay bóng đổ bằng **surface tint** — độ nổi càng cao thì nền càng ngả sang `colorPrimary`.
Nếu đặt `cardElevation="0dp"` như dự án đang làm:

```xml
<style name="Container.Card" parent="Widget.Material3.CardView.Elevated">
    <item name="cardElevation">0dp</item>
    <item name="strokeColor">@color/divider</item>
    <item name="strokeWidth">1dp</item>
</style>
```

thì không có tint, phân tách hoàn toàn dựa vào viền. Hợp lý và nhất quán — nhưng lúc đó
`Widget.Material3.CardView.Outlined` mới là parent đúng về mặt ngữ nghĩa (elevated card mà elevation
bằng 0 thì không còn là elevated).

### 4.4. `BottomNavigationView` vs `NavigationBarView`

M3 giới thiệu `NavigationBarView` làm lớp cơ sở, nhưng `BottomNavigationView` vẫn kế thừa từ nó và
vẫn được hỗ trợ đầy đủ. Dự án dùng `BottomNavigationView` — không cần đổi.

## 5. Phiên bản thư viện

```toml
# gradle/libs.versions.toml
material = "1.5.0"
```

**1.5.0 là bản khá cũ** (đầu 2022) — đây là bản đầu tiên có Material 3 ổn định. Những thứ ra sau
1.5.0 sẽ **không có**, ví dụ:

- Nhóm màu `colorSurfaceContainer*` (1.9+)
- `MaterialSwitch` kiểu M3 (1.7+)
- `SearchBar` / `SearchView` kiểu M3 (1.8+)
- `Widget.Material3.CardView.Outlined` với đầy đủ token (bổ sung dần qua các bản)
- Nhiều sửa lỗi cho `predictive back` (1.10+)

Nếu gặp thuộc tính/component xuất hiện trong tài liệu Google mà không biên dịch được, khả năng cao
là do phiên bản. Nâng cấp chỉ cần sửa một dòng trong version catalog:

```toml
material = "1.13.0"
```

rồi build lại và kiểm tra hồi quy giao diện. Nâng bản này thường an toàn nhưng có thể đổi nhẹ về
kích thước/bo góc mặc định của component.

## 6. Chuyển M2 → M3, checklist

1. Đổi `parent` của theme gốc: `Theme.MaterialComponents.*` → `Theme.Material3.*`
2. Đổi mọi `Widget.MaterialComponents.*` → `Widget.Material3.*`
3. Ánh xạ lại thuộc tính màu theo bảng ở §2 (chú ý `colorPrimaryVariant` → `colorPrimaryContainer`
   đổi cả ý nghĩa, không chỉ đổi tên)
4. Rà lại elevation — bóng đổ đã thành surface tint
5. Kiểm tra chiều cao/bo góc: nút M3 cao 40dp và bo tròn hơn, layout có thể xô lệch
6. Xem lại `TextAppearance.*` sang bộ `Material3`

## Xem thêm

- [Theming & Design tokens](theming-tokens.md)
- [Responsive layout](responsive-layout.md)
