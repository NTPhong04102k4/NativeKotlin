# Biometric — xác thực sinh trắc học

Dự án đã có `util/BiometricHelper.kt` và nút `btn_biometric` trong màn hình đăng nhập. Trang này
giải thích cơ chế và những gì còn thiếu.

## 1. Hiện trạng

```kotlin
// util/BiometricHelper.kt
class BiometricHelper(private val activity: AppCompatActivity) {

    fun showBiometricPrompt(
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onError(errString.toString())
                }
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }
                override fun onAuthenticationFailed() {
                    onError("Xác thực thất bại")
                }
            })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Hủy")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}
```

Nối vào `LoginActivity`:

```kotlin
binding.btnBiometric?.setOnClickListener {
    biometricHelper.showBiometricPrompt(
        getString(R.string.biometric_title),
        getString(R.string.biometric_subtitle),
        onSuccess = { goToHome(LoggedInUserView(getString(R.string.biometric_user))) },
        onError = { message -> Toast.makeText(this@LoginActivity, message, LENGTH_SHORT).show() },
    )
}
```

## 2. ⚠️ Bốn vấn đề cần sửa

### 2.1. Không kiểm tra thiết bị có hỗ trợ không

Gọi `authenticate()` trên máy không có cảm biến, hoặc người dùng chưa đăng ký vân tay → prompt
không hiện, chỉ có callback lỗi. Nút vẫn hiển thị và người dùng không hiểu vì sao bấm không được.

```kotlin
fun canAuthenticate(): Int =
    BiometricManager.from(activity).canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
    )

fun isAvailable(): Boolean = canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
```

| Mã trả về | Nghĩa | Nên làm |
|---|---|---|
| `BIOMETRIC_SUCCESS` | Sẵn sàng | Hiện nút |
| `BIOMETRIC_ERROR_NO_HARDWARE` | Máy không có cảm biến | **Ẩn nút** |
| `BIOMETRIC_ERROR_HW_UNAVAILABLE` | Tạm thời không dùng được | Ẩn hoặc vô hiệu hoá |
| `BIOMETRIC_ERROR_NONE_ENROLLED` | Chưa đăng ký vân tay/khuôn mặt | Mời sang Cài đặt để đăng ký |
| `BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED` | Cần cập nhật bảo mật | Thông báo |

```kotlin
// LoginActivity
binding.btnBiometric?.isVisible = biometricHelper.isAvailable()
```

### 2.2. `onAuthenticationFailed` bị coi là lỗi

```kotlin
override fun onAuthenticationFailed() {
    onError("Xác thực thất bại")     // ✗ sai ngữ nghĩa
}
```

Ba callback có ý nghĩa khác nhau:

| Callback | Nghĩa | Prompt còn mở? |
|---|---|---|
| `onAuthenticationSucceeded` | Khớp | Đóng |
| `onAuthenticationFailed` | **Một lần quét không khớp** — người dùng thử lại được | **Vẫn mở** |
| `onAuthenticationError` | Lỗi thật (huỷ, hết lần thử, khoá) | Đóng |

Hiện tại mỗi lần đặt sai ngón tay là hiện một Toast, dù prompt vẫn đang mở và người dùng vẫn thử
lại được. Rối mắt và sai thông điệp. Nên tách riêng:

```kotlin
fun showBiometricPrompt(
    title: String,
    subtitle: String,
    onSuccess: (BiometricPrompt.AuthenticationResult) -> Unit,
    onFailed: () -> Unit = {},                    // quét không khớp — thường bỏ qua
    onError: (Int, String) -> Unit,               // kèm errorCode
)
```

### 2.3. Không phân biệt "người dùng huỷ" với lỗi thật

```kotlin
override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
    when (errorCode) {
        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
        BiometricPrompt.ERROR_USER_CANCELED,
        BiometricPrompt.ERROR_CANCELED -> { /* im lặng — người dùng chủ động huỷ */ }

        BiometricPrompt.ERROR_LOCKOUT ->
            onError(errorCode, "Quá nhiều lần thử. Vui lòng dùng mật khẩu.")

        BiometricPrompt.ERROR_LOCKOUT_PERMANENT ->
            onError(errorCode, "Đã khoá sinh trắc học. Cần mở khoá máy bằng mã PIN.")

        else -> onError(errorCode, errString.toString())
    }
}
```

Hiện tại bấm "Hủy" cũng hiện Toast lỗi — không đúng.

### 2.4. ⚠️ Nghiêm trọng nhất: xác thực xong không có phiên đăng nhập thật

```kotlin
onSuccess = { goToHome(LoggedInUserView(getString(R.string.biometric_user))) }
```

Vân tay khớp → vào thẳng màn hình chính với tên hiển thị là chuỗi cứng `"Người dùng Sinh trắc học"`.
**Không có token, không có user id, không gọi backend.**

Đây không phải xác thực — nó chỉ là kiểm tra chủ máy. Sinh trắc học trên Android **không** cung cấp
danh tính cho server; nó chỉ mở khoá cục bộ.

Mô hình đúng:

```
Lần đăng nhập ĐẦU TIÊN:
   username/password  ──►  backend  ──►  refresh token
                                              │
                                              ▼
                              lưu vào Keystore, khoá bằng sinh trắc học

Các lần sau:
   vân tay khớp  ──►  mở khoá Keystore  ──►  lấy refresh token  ──►  đổi access token
```

Nghĩa là sinh trắc học chỉ dùng để **mở khoá thứ đã lưu**, không phải để tạo phiên mới. Nếu chưa
từng đăng nhập bằng mật khẩu thì nút sinh trắc học phải bị ẩn.

## 3. CryptoObject — buộc chặt vào Keystore

Không có `CryptoObject`, kết quả `onAuthenticationSucceeded` chỉ là một cái boolean có thể bị bỏ
qua/giả mạo trên máy đã root. Buộc vào Keystore thì dữ liệu **không giải mã được** nếu không xác
thực thành công — bảo đảm bằng mã hoá, không phải bằng logic app.

```kotlin
private fun getOrCreateSecretKey(): SecretKey {
    val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    (keyStore.getKey(KEY_NAME, null) as? SecretKey)?.let { return it }

    val spec = KeyGenParameterSpec.Builder(
        KEY_NAME,
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
    )
        .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
        .setUserAuthenticationRequired(true)              // chỉ dùng được sau khi xác thực
        .setInvalidatedByBiometricEnrollment(true)        // đăng ký vân tay mới -> khoá cũ vô hiệu
        .build()

    return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        .apply { init(spec) }
        .generateKey()
}

// Truyền cipher vào prompt
val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), IvParameterSpec(savedIv))

biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))

// Trong onAuthenticationSucceeded
val plainToken = result.cryptoObject?.cipher?.doFinal(encryptedToken)
```

`setInvalidatedByBiometricEnrollment(true)` rất quan trọng: nếu ai đó thêm vân tay mới vào máy,
khoá cũ tự vô hiệu và token không giải mã được nữa.

## 4. Cấp độ xác thực

```kotlin
.setAllowedAuthenticators(
    BiometricManager.Authenticators.BIOMETRIC_STRONG or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL
)
```

| Cấp | Nghĩa | Dùng với CryptoObject |
|---|---|---|
| `BIOMETRIC_STRONG` (Class 3) | Vân tay, khuôn mặt 3D | **Có** |
| `BIOMETRIC_WEAK` (Class 2) | Nhận diện khuôn mặt 2D | Không |
| `DEVICE_CREDENTIAL` | PIN / hình vẽ / mật khẩu máy | Có (API 30+) |

> ⚠️ Không dùng được đồng thời `setNegativeButtonText()` và `DEVICE_CREDENTIAL` — hệ thống tự hiện
> nút "Dùng mã PIN". Đặt cả hai sẽ ném `IllegalArgumentException`. Code hiện tại đang dùng
> `setNegativeButtonText("Hủy")` nên chưa vướng, nhưng cần biết khi thêm `DEVICE_CREDENTIAL`.

Chuỗi `"Hủy"` cũng đang hardcode trong `BiometricHelper` — nên chuyển sang `strings.xml` cho nhất
quán với phần còn lại đã được dọn.

## 5. Phiên bản thư viện

```kotlin
implementation("androidx.biometric:biometric:1.1.0")
```

**1.1.0 là bản cũ** (2021), lại còn hardcode chuỗi toạ độ thay vì qua version catalog. Bản
`1.2.0-alpha` có `BiometricPrompt` hỗ trợ tốt hơn cho `DEVICE_CREDENTIAL` và sửa nhiều lỗi trên
Android 12+. Nếu chỉ dùng cấp cơ bản thì 1.1.0 vẫn chạy.

## 6. Quyền

```xml
<uses-permission android:name="android.permission.USE_BIOMETRIC" />
```

Đây là **normal permission** — được cấp tự động, không cần xin lúc chạy. Đây cũng là quyền duy nhất
trong dự án thuộc nhóm này mà đang hoạt động đúng.

## 7. Checklist khi hoàn thiện

- [ ] `isAvailable()` để ẩn/hiện nút
- [ ] Tách `onAuthenticationFailed` khỏi `onError`
- [ ] Xử lý riêng `ERROR_NEGATIVE_BUTTON` / `ERROR_USER_CANCELED` (im lặng)
- [ ] Nối với phiên đăng nhập thật qua `CryptoObject` + Keystore
- [ ] Ẩn nút nếu chưa từng đăng nhập bằng mật khẩu
- [ ] Chuyển `"Hủy"` sang `strings.xml`
- [ ] Cân nhắc đưa `androidx.biometric` vào version catalog

## Xem thêm

- [Permissions](../02-android-core/permissions.md)
- [ForgeRock](forgerock.md) — SDK doanh nghiệp đã tích hợp sẵn biometric + token
- [Room & DataStore](../05-jetpack/room-datastore.md) — lưu token an toàn
