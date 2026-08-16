# Navigation — `AppRouter`

Dự án **không dùng Fragment và không dùng Navigation Component**. Mỗi màn hình là một Activity
riêng, và toàn bộ việc điều hướng đi qua một file duy nhất:
[`util/AppRouter.kt`](../../app/src/main/java/com/example/application_ai_assisstant/util/AppRouter.kt).

> **Quy tắc:** `AppRouter` là nơi **duy nhất** được phép gọi `startActivity`. Không rải
> `Intent(this, XxxActivity::class.java)` khắp nơi trong Activity.

## 1. Bản đồ điều hướng

```
                      ┌──────────────────┐
                      │  LoginActivity   │  launcher, splash screen
                      └────────┬─────────┘
                    openHomeAfterLogin()   NEW_TASK | CLEAR_TASK
                               ▼
   ┌───────────────────────────────────────────────────────┐
   │              BOTTOM NAVIGATION (3 tab)                │
   │                                                       │
   │   Discovery ◄──────► Schedule ◄──────► Personal       │
   │   (tab gốc)      REORDER_TO_FRONT                     │
   └───────────────────────────┬───────────────────────────┘
                               │ openSettings()   push thường
                               ▼
                      ┌──────────────────┐
                      │ SettingsActivity │
                      └──────────────────┘

   Personal ──logout()──► LoginActivity     NEW_TASK | CLEAR_TASK
```

`ScrollingActivity` tồn tại trong manifest nhưng **chưa có route nào trỏ tới** — nó là màn hình mẫu
do Android Studio sinh ra.

## 2. Bảng route

Toàn bộ tab được khai báo ở một chỗ:

```kotlin
private val TABS: Map<Int, Class<out Activity>> = linkedMapOf(
    R.id.nav_discovery to DiscoveryActivity::class.java,
    R.id.nav_schedule  to ScheduleActivity::class.java,
    R.id.nav_personal  to PersonalActivity::class.java,
)
```

**Thêm một tab mới cần đúng 3 việc:**

1. Thêm một dòng vào `TABS`
2. Thêm `<item>` cùng id vào `res/menu/bottom_nav_menu.xml`
3. Khai báo `<activity>` trong `AndroidManifest.xml` kèm `android:launchMode="singleTop"`

Không phải sửa gì trong các Activity còn lại. (Trước đây phải sửa ba nhánh `when` gần như giống hệt
nhau — đó là lý do gom về bảng route.)

## 3. Vì sao đổi tab giữ được state

Cách cũ dùng `startActivity` + `finish()`: Activity hiện tại bị huỷ, nên mỗi lần quay lại tab là
màn hình được **tạo lại từ đầu** — mất vị trí cuộn, mất dữ liệu đang nhập, RecyclerView phải bind lại.

Cách hiện tại dùng `FLAG_ACTIVITY_REORDER_TO_FRONT`:

```kotlin
from.startActivity(
    Intent(from, target).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
)
```

Nếu Activity đích **đã có** trong task, nó được kéo lên trên cùng mà không bị tạo lại → state còn
nguyên. Chỉ lần mở đầu tiên mới thực sự tạo instance mới.

Diễn biến back stack khi người dùng bấm Discovery → Schedule → Personal → Discovery:

```
[Discovery]
[Discovery, Schedule]
[Discovery, Schedule, Personal]
[Schedule, Personal, Discovery]     ← Discovery được kéo lên, KHÔNG tạo lại
```

`android:launchMode="singleTop"` trong manifest là lớp bảo hiểm: đảm bảo không bao giờ có hai
instance của cùng một tab.

## 4. Hành vi nút Back

Vì `REORDER_TO_FRONT` làm thứ tự stack bị xáo, để hệ thống tự xử lý Back sẽ cho kết quả khó đoán
(Back từ Discovery lại nhảy về Personal). Nên hành vi được cài đặt tường minh:

```kotlin
private fun setupTabBackBehavior(activity: AppCompatActivity, currentItemId: Int) {
    if (currentItemId == START_TAB_ID) return          // tab gốc: để hệ thống thoát app
    activity.onBackPressedDispatcher.addCallback(activity) {
        openTab(activity, START_TAB_ID)                 // tab phụ: quay về tab gốc
    }
}
```

Đây đúng là hợp đồng Material guideline đặt ra cho bottom navigation:

| Đang ở | Bấm Back | Kết quả |
|---|---|---|
| Discovery (tab gốc) | → | Thoát app |
| Schedule | → | Về Discovery |
| Personal | → | Về Discovery |
| Settings | → | Về Personal (back stack thường) |

Trước đây mọi tab đều `finish()` nên Back ở **bất kỳ** tab nào cũng thoát thẳng app.

Dùng `onBackPressedDispatcher` chứ không override `onBackPressed()` — hàm đó đã deprecated từ API
33 và không tương thích với predictive back gesture của Android 14+.

## 5. Xoá back stack khi đổi phiên đăng nhập

Hai chỗ cần xoá sạch task, nếu không người dùng bấm Back là quay ngược về màn hình lẽ ra không còn
truy cập được:

```kotlin
fun openHomeAfterLogin(from: Activity) {          // login xong: Back không được về Login
    from.startActivity(
        Intent(from, DiscoveryActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    )
    from.finish()
}

fun logout(from: Activity) {                       // logout: Back không được về màn đã đăng nhập
    from.startActivity(
        Intent(from, LoginActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    )
    from.finish()
}
```

`CLEAR_TASK` bắt buộc đi kèm `NEW_TASK` — một mình `CLEAR_TASK` không có tác dụng.

## 6. Tắt animation chuyển tab

Đây là chỗ dễ sai vì API đã đổi ở Android 14 (API 34).

| API | Hàm | Gọi ở đâu |
|---|---|---|
| < 34 | `overridePendingTransition(0, 0)` | Ngay **sau** `startActivity()`, trên Activity đang rời đi |
| ≥ 34 | `overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)` | Trong `onCreate()` của Activity **được mở** |

Không chỉ là đổi tên hàm — **chỗ gọi cũng khác**. Nên `AppRouter` tách làm hai:

```kotlin
// nhánh cũ: gọi trong openTab(), sau startActivity
private fun Activity.disableTransitionForLegacyApi() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        overridePendingTransition(0, 0)
    }
}

// nhánh mới: gọi trong bind(), tức là trong onCreate của tab
private fun Activity.applyTabTransition() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN, 0, 0)
        overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
    }
}
```

`UPSIDE_DOWN_CAKE` là tên hằng của API 34. Dự án có `minSdk 24` nên phải giữ cả hai nhánh.

## 7. Một bẫy nhỏ nhưng hay gặp

```kotlin
bottomNavigationView.selectedItemId = currentItemId    // PHẢI đặt TRƯỚC
bottomNavigationView.setOnItemSelectedListener { ... } // rồi mới set listener
```

Nếu gán `selectedItemId` **sau** khi set listener, việc gán đó sẽ kích hoạt luôn listener → app tự
điều hướng sang chính màn hình đang mở. Thứ tự này quan trọng.

## 8. So với Navigation Component

Nếu sau này cân nhắc đổi:

| | AppRouter (hiện tại) | Navigation Component |
|---|---|---|
| Đơn vị màn hình | Activity | Fragment trong 1 Activity |
| Back stack | Tự cài đặt | Tự động, có multiple back stack cho từng tab |
| Giữ state tab | Có (`REORDER_TO_FRONT`) | Có (`setupWithNavController`) |
| Truyền tham số | `Intent` extras, không type-safe | Safe Args, có kiểm tra kiểu lúc biên dịch |
| Deep link | Tự khai báo `intent-filter` | Khai báo trong nav graph |
| Xem được sơ đồ | Đọc `TABS` trong code | Có editor đồ hoạ |
| Chuyển màn nhanh | Chậm hơn (tạo Activity) | Nhanh hơn (chỉ đổi Fragment) |
| Chi phí đổi sang | — | Phải viết lại cả 3 màn hình thành Fragment |

`AppRouter` đủ dùng cho quy mô hiện tại. Chỉ nên cân nhắc Navigation Component nếu về sau cần
**multiple back stack thật** (mỗi tab có lịch sử điều hướng riêng, nhiều tầng sâu) hoặc cần deep
link phức tạp.

## 9. Chưa có

- **Truyền tham số giữa các màn hình.** Chưa route nào cần, nhưng khi cần thì thêm tham số vào hàm
  của `AppRouter` và đóng gói `Intent` extras **bên trong** router, đừng để chỗ gọi tự làm.
- **`ScrollingActivity`** chưa có route.
- **Widget chưa mở được app.** `widget/AI_Assistant` mới chỉ set text, chưa gắn `PendingIntent` —
  xem [App Widget](../04-app-widget/app-widget.md).
- **Scroll-to-top khi chạm lại tab đang mở.** `setOnItemReselectedListener` đã có sẵn chỗ cắm nhưng
  còn rỗng, chờ RecyclerView được wire.

## Xem thêm

- [Vòng đời](vong-doi.md) — `REORDER_TO_FRONT` gọi những callback nào
- [MVVM](../01-kien-truc/mvvm.md)
