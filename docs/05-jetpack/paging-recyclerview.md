# RecyclerView & Paging 3

Dự án đã có RecyclerView trong layout (`rv_discovery`, `rv_schedule`) và item layout
(`item_discovery_card.xml`, `item_schedule.xml`) nhưng **chưa có adapter nào**. Trang này là khuôn
mẫu để wire chúng.

## 1. RecyclerView cơ bản với `ListAdapter`

Dùng `ListAdapter` chứ không `RecyclerView.Adapter` thuần — nó lo `DiffUtil` và animation sẵn.

```kotlin
class ScheduleAdapter(
    private val onClick: (Schedule) -> Unit,
) : ListAdapter<Schedule, ScheduleAdapter.VH>(DIFF) {

    inner class VH(private val binding: ItemScheduleBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Schedule) = with(binding) {
            tvTime.text = item.timeText
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

### `areItemsTheSame` vs `areContentsTheSame`

| Hàm | Câu hỏi | So sánh gì |
|---|---|---|
| `areItemsTheSame` | "Có phải cùng một item không?" | **Chỉ id** |
| `areContentsTheSame` | "Nội dung có đổi không?" | Toàn bộ (`data class` → `==` là đủ) |

Làm sai thì hoặc mất animation, hoặc list nhấp nháy toàn bộ mỗi lần cập nhật.

### Nối vào Activity

```kotlin
// ScheduleActivity.onCreate
private val adapter = ScheduleAdapter { schedule -> /* mở chi tiết */ }

binding.rvSchedule.apply {
    layoutManager = LinearLayoutManager(this@ScheduleActivity)
    adapter = this@ScheduleActivity.adapter
    setHasFixedSize(true)          // chỉ khi kích thước RecyclerView không đổi theo nội dung
}

// Observe từ ViewModel
lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.schedules.collect { adapter.submitList(it) }
    }
}
```

> ⚠️ Nhắc lại từ [Navigation](../02-android-core/navigation.md): các tab dùng
> `REORDER_TO_FRONT` nên quay lại tab **không** gọi `onCreate`. Đặt việc tải dữ liệu trong
> `repeatOnLifecycle(STARTED)` như trên là đúng — nó tự chạy lại mỗi lần tab quay về foreground.

## 2. SwipeRefreshLayout

Dự án vừa thêm `androidx.swiperefreshlayout`. Bọc RecyclerView:

```xml
<androidx.swiperefreshlayout.widget.SwipeRefreshLayout
    android:id="@+id/swipe_refresh"
    android:layout_width="match_parent"
    android:layout_height="0dp">

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/rv_schedule"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />
</androidx.swiperefreshlayout.widget.SwipeRefreshLayout>
```

```kotlin
binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }

// Tắt spinner khi xong — đừng quên, nếu không nó quay mãi
viewModel.isRefreshing.collect { binding.swipeRefresh.isRefreshing = it }
```

Đặt màu theo token của dự án:

```kotlin
binding.swipeRefresh.setColorSchemeResources(R.color.primary_500)
```

## 3. Paging 3 — khi danh sách dài

Chỉ dùng Paging khi danh sách **thực sự dài** (hàng trăm+ item, tải theo trang từ API). Với danh
sách lịch trình trong ngày (vài chục item) thì `ListAdapter` là đủ — Paging chỉ thêm phức tạp.

### 3.1. PagingSource

```kotlin
class DiscoveryPagingSource(
    private val api: DiscoveryApi,
) : PagingSource<Int, DiscoveryItem>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, DiscoveryItem> {
        val page = params.key ?: 1
        return try {
            val response = api.getItems(page = page, size = params.loadSize)
            LoadResult.Page(
                data = response.items.map { it.toDomain() },
                prevKey = if (page == 1) null else page - 1,
                nextKey = if (response.items.isEmpty()) null else page + 1,
            )
        } catch (e: IOException) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, DiscoveryItem>): Int? =
        state.anchorPosition?.let { anchor ->
            state.closestPageToPosition(anchor)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchor)?.nextKey?.minus(1)
        }
}
```

### 3.2. Repository → ViewModel

```kotlin
// Repository
fun pagedItems(): Flow<PagingData<DiscoveryItem>> = Pager(
    config = PagingConfig(pageSize = 20, enablePlaceholders = false),
    pagingSourceFactory = { DiscoveryPagingSource(api) },
).flow

// ViewModel — cachedIn BẮT BUỘC, nếu không xoay màn hình là tải lại từ đầu
val items: Flow<PagingData<DiscoveryItem>> =
    repository.pagedItems().cachedIn(viewModelScope)
```

### 3.3. PagingDataAdapter

```kotlin
class DiscoveryAdapter : PagingDataAdapter<DiscoveryItem, DiscoveryAdapter.VH>(DIFF) {
    // giống ListAdapter, nhưng getItem(position) có thể trả về null (placeholder)
    override fun onBindViewHolder(holder: VH, position: Int) {
        getItem(position)?.let { holder.bind(it) }
    }
}

// Activity
lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.items.collectLatest { adapter.submitData(it) }   // collectLatest, không collect
    }
}
```

### 3.4. Loading / error state

```kotlin
adapter.addLoadStateListener { state ->
    binding.progress.isVisible = state.refresh is LoadState.Loading
    binding.tvError.isVisible = state.refresh is LoadState.Error
    binding.rvDiscovery.isVisible = state.refresh is LoadState.NotLoading
}

// Header/footer hiện spinner khi tải trang tiếp
binding.rvDiscovery.adapter = adapter.withLoadStateFooter(
    footer = LoadStateAdapter { adapter.retry() }
)
```

### 3.5. Paging + Room (offline-first)

```kotlin
@Query("SELECT * FROM discovery_items ORDER BY publishedAt DESC")
fun pagingSource(): PagingSource<Int, DiscoveryItemEntity>
```

Kết hợp với `RemoteMediator` để vừa hiển thị cache vừa tải trang mới. Đây là mô hình đầy đủ nhất
nhưng cũng phức tạp nhất — chỉ làm khi thực sự cần offline.

## 4. Tối ưu hiệu năng

| Kỹ thuật | Khi nào |
|---|---|
| `setHasFixedSize(true)` | Kích thước RecyclerView không phụ thuộc nội dung |
| `DiffUtil` qua `ListAdapter` | **Luôn luôn** — đừng gọi `notifyDataSetChanged()` |
| `setHasStableIds(true)` + `getItemId()` | List có id ổn định, cần animation mượt |
| `RecycledViewPool` dùng chung | Nhiều RecyclerView cùng loại item |
| Tránh layout lồng sâu trong item | Lint `TooDeepLayout` đang cảnh báo |

**Đừng dùng `notifyDataSetChanged()`.** Nó bind lại toàn bộ, mất animation và mất vị trí cuộn.

## 5. Lint liên quan

`lint.xml` đặt `TooManyViews` và `TooDeepLayout` ở mức `warning`. Item của RecyclerView được inflate
hàng chục lần nên layout item là chỗ đáng tối ưu nhất — mỗi tầng lồng thêm nhân lên theo số item
hiển thị.

`item_discovery_card.xml` hiện là `MaterialCardView > LinearLayout > LinearLayout > TextView` — ba
tầng, chấp nhận được. Nếu sâu hơn thì cân nhắc `ConstraintLayout` phẳng.

## Xem thêm

- [ViewBinding](../03-ui/viewbinding.md) — binding trong ViewHolder
- [Room & DataStore](room-datastore.md)
- [Repository Pattern](../01-kien-truc/repository-pattern.md)
