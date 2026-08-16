package com.ntp.application_ai_assisstant.ui.personal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ntp.application_ai_assisstant.data.SessionRepository

class PersonalViewModelFactory(
    private val sessionRepository: SessionRepository,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PersonalViewModel::class.java)) {
            return PersonalViewModel(sessionRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
