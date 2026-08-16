package com.ntp.application_ai_assisstant.data

import com.ntp.application_ai_assisstant.data.local.SessionManager
import com.ntp.application_ai_assisstant.data.model.LoggedInUser

/**
 * Class that requests authentication and user information from the remote data source and
 * maintains an in-memory cache of login status and user credentials information.
 */

class LoginRepository(val dataSource: LoginDataSource) {

    // in-memory cache of the loggedInUser object
    var user: LoggedInUser? = null
        private set

    val isLoggedIn: Boolean
        get() = user != null

    init {
        // If user credentials will be cached in local storage, it is recommended it be encrypted
        // @see https://developer.android.com/training/articles/keystore
        user = null
    }

    fun logout() {
        user = null
        SessionManager.clear()
        dataSource.logout()
    }

    fun login(username: String, password: String): Result<LoggedInUser> {
        // handle login
        val result = dataSource.login(username, password)

        if (result is Result.Success) {
            setLoggedInUser(result.data)
        }

        return result
    }

    private fun setLoggedInUser(loggedInUser: LoggedInUser) {
        this.user = loggedInUser
        // Đẩy lên state toàn cục để mọi màn hình đọc được và để phiên sống sót qua process death.
        // Khi backend thật trả access token, gọi thêm SessionManager.saveToken(token) ở đây —
        // ApiClient sẽ tự đính nó vào mọi request sau đó.
        SessionManager.saveUser(loggedInUser)
    }
}