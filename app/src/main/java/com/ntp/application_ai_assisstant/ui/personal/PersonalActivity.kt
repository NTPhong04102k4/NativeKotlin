package com.ntp.application_ai_assisstant.ui.personal

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ntp.application_ai_assisstant.R
import com.ntp.application_ai_assisstant.appContainer
import com.ntp.application_ai_assisstant.databinding.ActivityPersonalBinding
import com.ntp.application_ai_assisstant.util.AppRouter
import com.ntp.application_ai_assisstant.util.setupBottomNavigation
import kotlinx.coroutines.launch

class PersonalActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPersonalBinding

    private val viewModel: PersonalViewModel by viewModels {
        PersonalViewModelFactory(appContainer.sessionRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPersonalBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBottomNavigation(binding.bottomNavigation, R.id.nav_personal)

        binding.tvSettings.setOnClickListener { AppRouter.openSettings(this) }
        // Nút không tự điều hướng nữa: nó báo ý định cho ViewModel, ViewModel dọn phiên xong
        // mới phát PersonalEvent.NavigateToLogin. Nhờ vậy không bao giờ chuyển màn khi token còn.
        binding.btnLogout.setOnClickListener { viewModel.onLogoutClicked() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch { viewModel.events.collect(::handleEvent) }
            }
        }
    }

    private fun render(state: PersonalUiState) = with(binding) {
        tvName.text = if (state.isLoggedIn) {
            state.displayName
        } else {
            getString(R.string.personal_guest)
        }
        tvEmail.text = if (state.isLoggedIn) {
            getString(R.string.personal_user_id, state.userId)
        } else {
            ""
        }
        btnLogout.isEnabled = state.isLoggedIn
    }

    private fun handleEvent(event: PersonalEvent) {
        when (event) {
            PersonalEvent.NavigateToLogin -> AppRouter.logout(this)
        }
    }
}
