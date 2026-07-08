package com.example.application_ai_assisstant.ui.personal

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.application_ai_assisstant.R
import com.example.application_ai_assisstant.databinding.ActivityPersonalBinding
import com.example.application_ai_assisstant.util.setupBottomNavigation

class PersonalActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPersonalBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPersonalBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupBottomNavigation(binding.bottomNavigation, R.id.nav_personal)
        
        // TODO: Cấu hình sự kiện cho các nút bấm
    }
}
