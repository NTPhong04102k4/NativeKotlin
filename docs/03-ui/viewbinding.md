# ViewBinding

## 1. Bật ở đâu

```kotlin
// app/build.gradle.kts
buildFeatures {
    viewBinding = true
}
```

Chỉ một dòng đó. AGP sẽ sinh **một class binding cho mỗi file layout**.

## 2. Quy tắc sinh tên class

`snake_case.xml` → `PascalCaseBinding`:

| File layout | Class binding | Dùng ở |
|---|---|---|
| `activity_login.xml` | `ActivityLoginBinding` | `LoginActivity` |
| `activity_discovery.xml` | `ActivityDiscoveryBinding` | `DiscoveryActivity` |
| `item_schedule.xml` | `ItemScheduleBinding` | *(chưa có adapter)* |
| `a_i__assistant_configure.xml` | `AIAssistantConfigureBinding` | `AI_AssistantConfigureActivity` |

Tên id cũng đổi theo: `android:id="@+id/btn_google"` → thuộc tính `binding.btnGoogle`.

> File `a_i__assistant_configure.xml` có tên xấu là do Android Studio sinh ra từ class
> `AI_Assistant`. Không nên đổi tên vì phải sửa cả `RemoteViews` và `xml/a_i__assistant_info.xml`.

Không muốn sinh binding cho một layout nào đó thì thêm vào thẻ gốc:

```xml
<LinearLayout ... tools:viewBindingIgnore="true">
```

## 3. Dùng trong Activity

```kotlin
class DiscoveryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDiscoveryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDiscoveryBinding.inflate(layoutInflater)
        setContentView(binding.root)          // truyền binding.root, KHÔNG phải R.layout.xxx

        setupBottomNavigation(binding.bottomNavigation, R.id.nav_discovery)
    }
}
```

Ba điểm bắt buộc:
1. `inflate(layoutInflater)` **trước** `setContentView`
2. `setContentView(binding.root)` — truyền `R.layout.activity_discovery` là view không được bind
3. Khai báo `lateinit var`, không phải `val` (chưa có giá trị lúc khởi tạo)

## 4. Field nullable — bẫy lớn nhất của dự án này

Đây là chỗ khiến `LoginActivity` trông lạ:

```kotlin
binding.btnGoogle?.setOnClickListener { ... }    // vì sao có dấu ?
loading?.visibility = View.GONE                   // và ở đây nữa
```

Nguyên nhân: `activity_login.xml` có **ba biến thể**:

```
res/layout/activity_login.xml            ← có view "loading"
res/layout-w936dp/activity_login.xml     ← KHÔNG có
res/layout-w1240dp/activity_login.xml    ← KHÔNG có
```

Khi một id **không xuất hiện ở mọi biến thể**, ViewBinding sinh field kiểu **nullable**. Hợp lý:
lúc chạy, hệ thống có thể chọn biến thể không chứa view đó.

Quy tắc rút ra:

> **Thêm một view có id vào một biến thể thì phải thêm vào tất cả các biến thể** — nếu không muốn
> field trở thành nullable và phải rắc `?.` khắp nơi.

Kiểm tra nhanh id nào có ở đâu:

```powershell
Select-String -Path app/src/main/res/layout*/activity_login.xml -Pattern 'android:id='
```

## 5. So sánh các cách truy cập view

| | `findViewById` | ViewBinding | DataBinding | Compose |
|---|---|---|---|---|
| Null-safe | Không | **Có** | Có | Không có view |
| Type-safe | Không (phải ép kiểu) | **Có** | Có | Có |
| Tốc độ biên dịch | Nhanh nhất | **Nhanh** | Chậm (annotation processor) | Trung bình |
| Logic trong XML | Không | **Không** | Có (`@{}`) | Không có XML |
| Cấu hình | Không | 1 dòng | 1 dòng + `<layout>` bọc ngoài | Nhiều |

**ViewBinding là lựa chọn đúng cho dự án này.** DataBinding cho phép nhét biểu thức vào XML — nghe
tiện nhưng làm logic rơi vào layout, khó debug (lỗi hiện trong code sinh tự động) và làm chậm build.

`findViewById` vẫn còn một chỗ trong dự án:

```kotlin
// ScrollingActivity.kt
setSupportActionBar(findViewById(R.id.toolbar))
```

Viết lại bằng binding được: `setSupportActionBar(binding.toolbar)`.

## 6. ViewBinding trong RecyclerView Adapter

Dự án đã có `item_discovery_card.xml` và `item_schedule.xml` nhưng **chưa có adapter**. Khi viết,
theo khuôn mẫu này:

```kotlin
class ScheduleAdapter(
    private val onClick: (Schedule) -> Unit,
) : ListAdapter<Schedule, ScheduleAdapter.VH>(DIFF) {

    inner class VH(private val binding: ItemScheduleBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Schedule) = with(binding) {
            tvTime.text = item.time
            tvPeriod.text = item.period
            tvTaskTitle.text = item.title
            tvTaskLocation.text = item.location
            root.setOnClickListener { onClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemScheduleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Schedule>() {
            override fun areItemsTheSame(a: Schedule, b: Schedule) = a.id == b.id
            override fun areContentsTheSame(a: Schedule, b: Schedule) = a == b
        }
    }
}
```

Chú ý `inflate(inflater, parent, false)` — tham số thứ ba **phải** là `false`, để `false` thì
RecyclerView tự gắn view vào; truyền `true` sẽ crash.

## 7. ViewBinding trong Fragment (nếu cần)

Dự án hầu như không dùng Fragment, ngoại lệ duy nhất là
`SettingsActivity.SettingsFragment` (`PreferenceFragmentCompat`, không dùng binding). Nếu về sau có
Fragment thật, phải nhớ:

```kotlin
private var _binding: FragmentXBinding? = null
private val binding get() = _binding!!

override fun onDestroyView() {
    super.onDestroyView()
    _binding = null       // BẮT BUỘC: view của Fragment chết trước Fragment
}
```

Fragment sống lâu hơn view của nó — giữ binding sau `onDestroyView` là rò rỉ toàn bộ cây view.
Activity không có vấn đề này nên `lateinit var` là đủ.

## 8. Lỗi thường gặp

| Triệu chứng | Nguyên nhân |
|---|---|
| Không tìm thấy class binding | Layout có lỗi cú pháp XML → chưa sinh được. Build lại và đọc lỗi XML trước |
| `binding` chưa khởi tạo | Truy cập trước `inflate()`, hoặc quên `lateinit` |
| Field bỗng thành nullable | Id không có mặt ở mọi biến thể layout — xem §4 |
| Sửa layout mà code không thấy | Build → *Clean Project*, hoặc *File → Invalidate Caches* |
| `NullPointerException` ở `binding.root` | Gọi `setContentView(R.layout.xxx)` thay vì `binding.root` |

## Xem thêm

- [Responsive layout](responsive-layout.md) — vì sao có biến thể layout
- [Vòng đời](../02-android-core/vong-doi.md)
