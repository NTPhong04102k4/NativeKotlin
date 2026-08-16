package com.ntp.application_ai_assisstant.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ntp.application_ai_assisstant.data.ScheduleRepository
import com.ntp.application_ai_assisstant.data.SessionRepository

class ScheduleViewModelFactory(
    private val repository: ScheduleRepository,
    private val sessionRepository: SessionRepository,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ScheduleViewModel::class.java)) {
            return ScheduleViewModel(repository, sessionRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
