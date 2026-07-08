package com.example.application_ai_assisstant.ui.discovery

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.application_ai_assisstant.R
import com.example.application_ai_assisstant.databinding.ActivityDiscoveryBinding
import com.example.application_ai_assisstant.util.setupBottomNavigation

class DiscoveryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityDiscoveryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDiscoveryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupBottomNavigation(binding.bottomNavigation, R.id.nav_discovery)
        
        // TODO: Cấu hình RecyclerView và Search
    }
}
