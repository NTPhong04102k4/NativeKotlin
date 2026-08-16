package com.ntp.application_ai_assisstant.data.local

import android.content.Context
import android.content.SharedPreferences
import com.ntp.application_ai_assisstant.data.model.LoggedInUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * State toàn cục của phiên đăng nhập.
 *
 * Đây là nơi DUY NHẤT giữ thông tin user/token cho cả app:
 *  - [currentUser] là StateFlow nên mọi màn hình `collect` nó sẽ tự cập nhật khi đăng nhập/đăng xuất.
 *  - [token] được đọc đồng bộ bởi Interceptor của ApiClient để đính vào mỗi request.
 *
 * Lưu bằng SharedPreferences nên sống sót qua process death — khác với cache in-memory
 * của LoginRepository (mất sạch khi hệ thống kill app).
 *
 * LƯU Ý BẢO MẬT: với token thật nên đổi sang EncryptedSharedPreferences (androidx.security:security-crypto).
 */
object SessionManager {

    private const val PREF_NAME = "ai_assistant_session"
    private const val KEY_TOKEN = "token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_DISPLAY_NAME = "display_name"

    private var prefs: SharedPreferences? = null

    private val _currentUser = MutableStateFlow<LoggedInUser?>(null)
    val currentUser: StateFlow<LoggedInUser?> = _currentUser.asStateFlow()

    /** Gọi một lần duy nhất trong Application.onCreate(). */
    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        _currentUser.value = readUser()
    }

    val token: String?
        get() = prefs?.getString(KEY_TOKEN, null)

    val isLoggedIn: Boolean
        get() = _currentUser.value != null

    /** Lưu thông tin user sau khi đăng nhập thành công. */
    fun saveUser(user: LoggedInUser) {
        prefs?.edit()
            ?.putString(KEY_USER_ID, user.userId)
            ?.putString(KEY_DISPLAY_NAME, user.displayName)
            ?.apply()
        _currentUser.value = user
    }

    /** Lưu access token trả về từ backend. */
    fun saveToken(token: String) {
        prefs?.edit()?.putString(KEY_TOKEN, token)?.apply()
    }

    /** Xoá toàn bộ phiên — dùng khi đăng xuất hoặc khi token hết hạn (HTTP 401). */
    fun clear() {
        prefs?.edit()?.clear()?.apply()
        _currentUser.value = null
    }

    private fun readUser(): LoggedInUser? {
        val store = prefs ?: return null
        val userId = store.getString(KEY_USER_ID, null) ?: return null
        val displayName = store.getString(KEY_DISPLAY_NAME, null) ?: return null
        return LoggedInUser(userId, displayName)
    }
}
