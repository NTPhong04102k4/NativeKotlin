# WebView

Dự án **chưa dùng WebView**. Trang này là tài liệu tham chiếu cho khi cần nhúng nội dung web —
điều khá chắc sẽ xảy ra với các mục "Bảo mật & Quyền riêng tư", "Về AI Assistant" ở màn hình Cá
nhân, hoặc luồng đăng nhập OAuth.

## 1. Trước tiên: có thực sự cần WebView không?

| Nhu cầu | Nên dùng |
|---|---|
| Mở link ra ngoài (điều khoản, trợ giúp) | **Custom Tabs** |
| Đăng nhập OAuth / SSO | **Custom Tabs** (bắt buộc theo RFC 8252) |
| Nội dung HTML của chính app (nội dung bài viết) | WebView |
| Web app nhúng sâu, cần gọi hàm native | WebView + JS bridge |

### Custom Tabs — mặc định nên chọn

```kotlin
// Cần androidx.browser:browser
CustomTabsIntent.Builder()
    .setDefaultColorSchemeParams(
        CustomTabColorSchemeParams.Builder()
            .setToolbarColor(ContextCompat.getColor(context, R.color.primary_500))
            .build()
    )
    .setShowTitle(true)
    .build()
    .launchUrl(context, Uri.parse(url))
```

Ưu điểm so với WebView: dùng chung cookie/mật khẩu đã lưu của Chrome, tự cập nhật bảo mật, hỗ trợ
đầy đủ web hiện đại, không tốn công bảo trì.

> **Với OAuth/SSO: bắt buộc dùng Custom Tabs, không WebView.** Google, Facebook và hầu hết nhà cung
> cấp danh tính **chặn** đăng nhập từ WebView (lỗi `disallowed_useragent`) vì WebView cho phép app
> chủ đọc trộm mật khẩu. ForgeRock cũng khuyến nghị như vậy — xem [ForgeRock](forgerock.md).

## 2. Cấu hình WebView an toàn

Khi thực sự cần WebView:

```kotlin
private fun WebView.configureSecurely() {
    settings.apply {
        javaScriptEnabled = true             // chỉ bật nếu THỰC SỰ cần
        domStorageEnabled = true

        // Bảo mật — mặc định đã đúng ở API 30+, nhưng khai báo tường minh cho rõ ý
        allowFileAccess = false
        allowContentAccess = false
        @Suppress("DEPRECATION")
        allowFileAccessFromFileURLs = false
        @Suppress("DEPRECATION")
        allowUniversalAccessFromFileURLs = false

        // Không cho tải nội dung HTTP trong trang HTTPS
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

        // Hiển thị
        loadWithOverviewMode = true
        useWideViewPort = true
        setSupportZoom(false)

        // Đừng đổi User-Agent trừ khi backend yêu cầu
    }

    // Chặn điều hướng ra domain lạ
    webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest,
        ): Boolean {
            val host = request.url.host ?: return true
            return if (host in ALLOWED_HOSTS) {
                false                          // cho WebView tự tải
            } else {
                openInCustomTab(request.url)   // domain lạ -> mở ra ngoài
                true
            }
        }
    }
}

private val ALLOWED_HOSTS = setOf("example.com", "www.example.com")
```

### Danh sách kiểm tra bảo mật

| Rủi ro | Cách chặn |
|---|---|
| XSS từ nội dung không tin cậy | Tắt `javaScriptEnabled` nếu không cần |
| Đọc file cục bộ | `allowFileAccess = false` |
| Tải nội dung HTTP trong trang HTTPS | `MIXED_CONTENT_NEVER_ALLOW` |
| Điều hướng sang trang lừa đảo | Kiểm tra host trong `shouldOverrideUrlLoading` |
| Bỏ qua lỗi chứng chỉ | **Không bao giờ** gọi `handler.proceed()` trong `onReceivedSslError` |

```kotlin
// ✗ TUYỆT ĐỐI KHÔNG — mở toang cửa cho tấn công man-in-the-middle
override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
    handler.proceed()
}
```

Google Play **từ chối** app có đoạn code trên.

## 3. JavaScript bridge — cầu nối web ↔ native

```kotlin
class WebAppBridge(private val activity: Activity) {

    @JavascriptInterface        // BẮT BUỘC, thiếu là hàm không gọi được từ JS
    fun getAppVersion(): String = BuildConfig.VERSION_NAME

    @JavascriptInterface
    fun openSchedule() {
        activity.runOnUiThread {              // JS gọi trên thread riêng!
            AppRouter.openTab(activity, R.id.nav_schedule)
        }
    }
}

webView.addJavascriptInterface(WebAppBridge(this), "AndroidBridge")
```

```javascript
// Phía web
const version = AndroidBridge.getAppVersion();
AndroidBridge.openSchedule();
```

> ⚠️ **`addJavascriptInterface` là bề mặt tấn công nghiêm trọng.** Chỉ dùng với nội dung **bạn tự
> kiểm soát hoàn toàn**. Nếu trang web bị chèn script độc, nó gọi được mọi hàm `@JavascriptInterface`
> — kể cả điều hướng, đọc dữ liệu.

Ba luật:
1. Chỉ đăng ký bridge khi tải domain trong allowlist
2. Không bao giờ nhận đường dẫn file hay lệnh tuỳ ý làm tham số
3. Callback của JS **không chạy trên main thread** — phải `runOnUiThread` để đụng UI

Hướng an toàn hơn: `WebMessagePort` (API 23+) hoặc `WebViewCompat.postWebMessage` — có kiểm soát
nguồn gốc (origin).

Gọi ngược từ native sang web:

```kotlin
webView.evaluateJavascript("window.onNativeEvent('${data}')") { result -> /* ... */ }
```

## 4. Xử lý nút Back

WebView có lịch sử riêng. Theo quy ước router của dự án, đăng ký qua `onBackPressedDispatcher`:

```kotlin
onBackPressedDispatcher.addCallback(this) {
    if (binding.webView.canGoBack()) {
        binding.webView.goBack()
    } else {
        isEnabled = false                    // nhả quyền cho callback tiếp theo
        onBackPressedDispatcher.onBackPressed()
    }
}
```

Chú ý `isEnabled = false` — nếu không, Back sẽ không bao giờ thoát được màn hình.

Đừng override `onBackPressed()` (deprecated từ API 33) — xem
[Navigation §4](../02-android-core/navigation.md#4-hành-vi-nút-back).

## 5. Vòng đời — nguồn rò rỉ kinh điển

```kotlin
override fun onPause() {
    binding.webView.onPause()
    binding.webView.pauseTimers()      // dừng cả JS timer
    super.onPause()
}

override fun onResume() {
    super.onResume()
    binding.webView.onResume()
    binding.webView.resumeTimers()
}

override fun onDestroy() {
    with(binding.webView) {
        loadUrl("about:blank")
        stopLoading()
        removeJavascriptInterface("AndroidBridge")
        (parent as? ViewGroup)?.removeView(this)
        destroy()                       // BẮT BUỘC
    }
    super.onDestroy()
}
```

WebView giữ tham chiếu tới Activity context. Không `destroy()` là rò rỉ **cả cây view lẫn
Activity** — lint `StaticFieldLeak` (mức `error`) có thể bắt một số trường hợp, nhưng không phải
tất cả.

> Với router hiện tại, các tab dùng `REORDER_TO_FRONT` nên `onDestroy` **không** được gọi khi đổi
> tab. WebView trong một tab sẽ sống mãi cùng tab đó — điều này tốt cho state nhưng nhớ
> `pauseTimers()` trong `onPause` để không đốt pin. Xem
> [Vòng đời §5](../02-android-core/vong-doi.md).

## 6. Chế độ tối

WebView không tự theo dark mode của app:

```kotlin
if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
    WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, true)
}
```

Cần `androidx.webkit:webkit`. Cách tốt hơn: để trang web tự xử lý bằng
`@media (prefers-color-scheme: dark)`.

Liên quan: dark mode của app hiện đang hỏng — xem
[Theming §5](../03-ui/theming-tokens.md#5-️-dark-mode-hiện-đang-hỏng).

## 7. Debug

```kotlin
if (BuildConfig.DEBUG) {
    WebView.setWebContentsDebuggingEnabled(true)
}
```

Rồi mở `chrome://inspect` trên máy tính. **Chỉ bật ở bản debug** — bản release bật cái này là để lộ
toàn bộ nội dung WebView cho bất kỳ ai cắm USB.

`buildConfig = true` đã được bật trong `app/build.gradle.kts` nên `BuildConfig.DEBUG` dùng được.

## 8. Nếu thêm màn hình WebView vào dự án

Theo quy ước hiện có:

1. Tạo `ui/web/WebViewActivity.kt` với ViewBinding
2. Khai báo `<activity>` trong manifest (**không** cần `launchMode="singleTop"` — đây không phải tab)
3. Thêm route vào `AppRouter`, **không** tạo `Intent` thẳng ở chỗ gọi:
   ```kotlin
   fun openWeb(from: Activity, url: String, @StringRes titleRes: Int) {
       from.startActivity(
           Intent(from, WebViewActivity::class.java)
               .putExtra(WebViewActivity.EXTRA_URL, url)
               .putExtra(WebViewActivity.EXTRA_TITLE, titleRes)
       )
   }
   ```
4. Nối `tv_privacy` / `action_about` ở `PersonalActivity` vào route mới

## Xem thêm

- [Navigation](../02-android-core/navigation.md)
- [ForgeRock](forgerock.md) — vì sao SSO phải dùng Custom Tabs
- [Native Bridge](native-bridge.md) — JS bridge ở quy mô lớn hơn
