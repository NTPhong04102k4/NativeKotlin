# CI status checks cho main / staging / dev

Ngày: 2026-08-16

## Vấn đề

GitHub báo khi lưu ruleset:

> Required status checks cannot be empty. Please add at least one status check or disable the rule.

Rule *"Require status checks to pass"* bắt buộc chọn check **theo tên**, mà GitHub chỉ gợi ý tên
những check **đã từng chạy** trên repo gần đây. Repo chưa có `.github/workflows/` nào → không check
nào tồn tại → danh sách rỗng → không lưu được rule.

Hệ quả: **thứ tự triển khai không thể đảo ngược.** Phải có workflow chạy xong ít nhất một lần rồi
mới cấu hình được ruleset.

## Phạm vi

Chỉ thêm CI status check. **Không** đụng vào `app/build.gradle.kts`, không thêm product flavor —
việc chia môi trường theo flavor đã có hướng xử lý riêng ở nhánh `dev`.

## Thiết kế

Một file `.github/workflows/ci.yml`, ba job chạy song song. Tên job chính là tên status check.

| Job | Lệnh | Artifact |
|---|---|---|
| `build` | `./gradlew assembleDebug` | — |
| `test` | `./gradlew testDebugUnitTest` | `test-results` khi fail |
| `lint` | `./gradlew lintDebug` | `lint-report` luôn luôn |

Ba job tách hẳn (thay vì gộp `build` + `test` chung một lần compile) để tín hiệu fail rõ ràng nhất:
nhìn tên check là biết hỏng khâu nào, không phải mở log. Đánh đổi là mỗi job compile lại từ đầu,
tốn thêm phút CI.

Mỗi job: `ubuntu-latest`, `timeout-minutes: 30`, `actions/checkout@v4` →
`actions/setup-java@v4` (temurin **21**) → `gradle/actions/setup-gradle@v4` để cache Gradle.

Thêm `permissions: contents: read` (least privilege) và `concurrency` huỷ run cũ khi push liên tiếp.

### Những quyết định dễ sai

**Không dùng bộ lọc `paths:`.** Đây là bẫy phổ biến nhất khi kết hợp với required status check: PR
chỉ sửa `docs/` sẽ không kích hoạt workflow → check không bao giờ chạy → ruleset treo vĩnh viễn ở
*"Expected — waiting for status"*, PR không merge được. Chạy thừa vẫn hơn kẹt.

**`java-version: 21` phải khớp `toolchainVersion=21`** trong `gradle/gradle-daemon-jvm.properties`.
Lệch thì Gradle tự tải JDK khác qua foojay ở mỗi lần chạy, làm build chậm hẳn.

### Cố ý loại ra

- **`connectedAndroidTest`** — cần emulator, chậm và hay flaky, mà repo mới chỉ có hai test template
  của IDE (`ExampleUnitTest`, `ExampleInstrumentedTest`). Chưa đáng.
- **Check format / ktlint** — project chưa áp plugin ktlint nào. Task `ktlintFormat` mà `make format`
  gọi hiện **không tồn tại**; đưa vào CI là fail ngay. Muốn có thì phải thêm plugin vào
  `app/build.gradle.kts` + version catalog trước.

## Sửa kèm: `lint.xml` vốn không có tác dụng

Phát hiện khi kiểm tra trước lúc biến `lint` thành required check. File `lint.xml` ở thư mục gốc
dùng sai thẻ gốc — `<resources>` thay vì `<lint>`. Lint báo thẳng trong report:

```
lint.xml:2: Warning: Unsupported tag <resources>, expected one of lint, issue, ignore or option
```

Toàn bộ bốn rule `severity="error"` (`HardcodedText`, `MissingPermission`, `StaticFieldLeak`,
`HandlerLeak`) bị bỏ qua. Nếu bật `lint` làm required check khi chưa sửa, gate sẽ luôn xanh kể cả
lúc code vi phạm — tức là vô dụng.

Đã sửa thẻ gốc thành `<lint>`. Sau khi sửa: `0 errors, 144 warnings`, và bốn cảnh báo
`Unsupported tag` biến mất — config đã có hiệu lực thật, code hiện vẫn pass cả bốn rule.

## Cấu hình ruleset (làm sau khi workflow chạy xong lần đầu)

1. Commit `ci.yml` → push lên `dev`
2. Đợi workflow chạy xong **một lần** → ba check `build` / `test` / `lint` mới tồn tại
3. Settings → Rules → ruleset → search tên check → chọn → Save
4. Lặp cho `staging` và `main`

Mức chặt khác nhau theo branch:

| Branch | Required checks | Review |
|---|---|---|
| `main` | `build`, `test`, `lint` | Bắt buộc PR + approve |
| `staging` | `build`, `test`, `lint` | Bắt buộc PR + approve |
| `dev` | chỉ `lint` | Review theo rules |

`dev` nhẹ hơn vì bản build từng môi trường đã được tạo theo flavor ở nhánh này, không cần CI gác
thêm; chỉ cần lint giữ chất lượng code và review giữ chất lượng thiết kế.

## Rủi ro đã biết

`app/build.gradle.kts` đang khai báo `com.facebook.android:facebook-android-sdk:latest.release`.
Đây là dynamic version — CI resolve lại phiên bản mới nhất ở **mỗi** lần chạy, nên build có thể đỏ
đột ngột dù không ai sửa dòng code nào. Nên pin số phiên bản cụ thể trước khi bật required check.
Nằm ngoài phạm vi thay đổi này.

## Kiểm chứng

- `./gradlew lintDebug` → BUILD SUCCESSFUL, `0 errors, 144 warnings` (sau khi sửa `lint.xml`)
- `./gradlew assembleDebug` → BUILD SUCCESSFUL, sinh được APK
- `./gradlew test` → BUILD SUCCESSFUL

Cả ba lệnh trong workflow đều đã chạy pass tại máy trước khi đưa lên CI.
