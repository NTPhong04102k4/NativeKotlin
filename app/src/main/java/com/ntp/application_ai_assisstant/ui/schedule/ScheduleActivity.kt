package com.ntp.application_ai_assisstant.ui.schedule

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.ntp.application_ai_assisstant.R
import com.ntp.application_ai_assisstant.appContainer
import com.ntp.application_ai_assisstant.databinding.ActivityScheduleBinding
import com.ntp.application_ai_assisstant.ui.common.toMessageRes
import com.ntp.application_ai_assisstant.util.AppRouter
import com.ntp.application_ai_assisstant.util.setupBottomNavigation
import java.util.Calendar
import kotlinx.coroutines.launch

/**
 * Màn Lịch trình — cùng khuôn với DiscoveryActivity:
 * dựng view -> [render] state -> [handleEvent] sự kiện một lần.
 */
class ScheduleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScheduleBinding

    private val viewModel: ScheduleViewModel by viewModels {
        ScheduleViewModelFactory(appContainer.scheduleRepository, appContainer.sessionRepository)
    }

    private val adapter = ScheduleAdapter { /* TODO: mở màn chi tiết sự kiện khi có */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScheduleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupBottomNavigation(binding.bottomNavigation, R.id.nav_schedule)
        setupViews()
        observeViewModel()
    }

    private fun setupViews() = with(binding) {
        rvSchedule.layoutManager = LinearLayoutManager(this@ScheduleActivity)
        rvSchedule.adapter = adapter

        swipeRefresh.setOnRefreshListener { viewModel.refresh() }
        btnRetry.setOnClickListener { viewModel.retry() }

        // CalendarView trả về year/month/day rời rạc -> quy về epoch millis đầu ngày
        // để state chỉ có một kiểu dữ liệu thời gian duy nhất.
        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val millis = Calendar.getInstance().apply {
                set(year, month, dayOfMonth, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            viewModel.onDateSelected(millis)
        }

        // TODO: mở màn thêm lịch trình khi có
        fabAddSchedule.isVisible = false
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch { viewModel.events.collect(::handleEvent) }
            }
        }
    }

    private fun render(state: ScheduleUiState) = with(binding) {
        progressBar.isVisible = state.isLoading
        swipeRefresh.isRefreshing = state.isRefreshing
        layoutError.isVisible = state.errorKind != null
        tvEmpty.isVisible = state.isEmpty
        swipeRefresh.isVisible = !state.isLoading && state.errorKind == null

        state.errorKind?.let { tvError.setText(it.toMessageRes()) }
        adapter.submitList(state.visibleItems)
    }

    private fun handleEvent(event: ScheduleEvent) {
        when (event) {
            is ScheduleEvent.ShowError ->
                Toast.makeText(this, event.kind.toMessageRes(), Toast.LENGTH_SHORT).show()

            ScheduleEvent.SessionExpired -> {
                Toast.makeText(this, R.string.error_unauthorized, Toast.LENGTH_LONG).show()
                AppRouter.logout(this)
            }
        }
    }
}
