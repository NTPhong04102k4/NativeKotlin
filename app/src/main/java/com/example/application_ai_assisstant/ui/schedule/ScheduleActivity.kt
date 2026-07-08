package com.example.application_ai_assisstant.ui.schedule

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.application_ai_assisstant.R
import com.example.application_ai_assisstant.databinding.ActivityScheduleBinding
import com.example.application_ai_assisstant.util.setupBottomNavigation

class ScheduleActivity : AppCompatActivity() {
    private lateinit var binding: ActivityScheduleBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScheduleBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupBottomNavigation(binding.bottomNavigation, R.id.nav_schedule)
        
        // TODO: Cấu hình Calendar và List lịch trình
    }
}
