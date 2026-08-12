package com.example.application_ai_assisstant.ui.login

import android.app.Activity
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.widget.EditText
import android.widget.Toast
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.application_ai_assisstant.databinding.ActivityLoginBinding

import com.example.application_ai_assisstant.R

import androidx.lifecycle.lifecycleScope
import com.example.application_ai_assisstant.util.AppRouter
import com.example.application_ai_assisstant.util.NetworkMonitor
import com.example.application_ai_assisstant.util.BiometricHelper
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var loginViewModel: LoginViewModel
    private lateinit var binding: ActivityLoginBinding
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var biometricHelper: BiometricHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Theo dõi mạng
        networkMonitor = NetworkMonitor(this)
        networkMonitor.startMonitoring()
        lifecycleScope.launch {
            networkMonitor.isConnected.collect { isConnected ->
                if (!isConnected) {
                    Toast.makeText(this@LoginActivity, getString(R.string.network_lost), Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Biometric Login (ví dụ khi nhấn vào logo hoặc nút riêng)
        biometricHelper = BiometricHelper(this)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val username = binding.username
        val password = binding.password
        val login = binding.login
        val loading = binding.loading

        loginViewModel = ViewModelProvider(this, LoginViewModelFactory())[LoginViewModel::class.java]

        loginViewModel.loginFormState.observe(this@LoginActivity, Observer {
            val loginState = it ?: return@Observer

            // disable login button unless both username / password is valid
            login.isEnabled = loginState.isDataValid

            if (loginState.usernameError != null) {
                username.error = getString(loginState.usernameError)
            }
            if (loginState.passwordError != null) {
                password.error = getString(loginState.passwordError)
            }
        })

        loginViewModel.loginResult.observe(this@LoginActivity, Observer {
            val loginResult = it ?: return@Observer

            loading?.visibility = View.GONE

            // Chỉ đóng màn hình khi đăng nhập THÀNH CÔNG. Trước đây setResult/finish nằm ngoài
            // nhánh if nên đăng nhập sai cũng thoát luôn LoginActivity.
            loginResult.error?.let { error ->
                showLoginFailed(error)
                return@Observer
            }
            loginResult.success?.let { user ->
                setResult(RESULT_OK)
                goToHome(user)
            }
        })

        username.afterTextChanged {
            loginViewModel.loginDataChanged(
                username.text.toString(),
                password.text.toString()
            )
        }

        password.apply {
            afterTextChanged {
                loginViewModel.loginDataChanged(
                    username.text.toString(),
                    password.text.toString()
                )
            }

            setOnEditorActionListener { _, actionId, _ ->
                when (actionId) {
                    EditorInfo.IME_ACTION_DONE ->
                        loginViewModel.login(
                            username.text.toString(),
                            password.text.toString()
                        )
                }
                false
            }

            login.setOnClickListener {
                loading?.visibility = View.VISIBLE
                loginViewModel.login(username.text.toString(), password.text.toString())
            }

            binding.btnGoogle?.setOnClickListener {
                // Triển khai Google Login
                Toast.makeText(this@LoginActivity, getString(R.string.google_connecting), Toast.LENGTH_SHORT).show()
            }

            binding.btnBiometric?.setOnClickListener {
                biometricHelper.showBiometricPrompt(
                    getString(R.string.biometric_title),
                    getString(R.string.biometric_subtitle),
                    onSuccess = {
                        goToHome(LoggedInUserView(getString(R.string.biometric_user)))
                    },
                    onError = { message ->
                        Toast.makeText(this@LoginActivity, message, Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        // NetworkMonitor đăng ký callback vào ConnectivityManager nên phải tự huỷ đăng ký,
        // nếu không callback sống lâu hơn Activity.
        networkMonitor.stopMonitoring()
        super.onDestroy()
    }

    private fun goToHome(model: LoggedInUserView) {
        val welcome = getString(R.string.welcome)
        Toast.makeText(applicationContext, "$welcome ${model.displayName}", Toast.LENGTH_LONG).show()
        AppRouter.openHomeAfterLogin(this)
    }

    private fun showLoginFailed(@StringRes errorString: Int) {
        Toast.makeText(applicationContext, errorString, Toast.LENGTH_SHORT).show()
    }
}

/**
 * Extension function to simplify setting an afterTextChanged action to EditText components.
 */
fun EditText.afterTextChanged(afterTextChanged: (String) -> Unit) {
    this.addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(editable: Editable?) {
            afterTextChanged.invoke(editable.toString())
        }

        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
    })
}
