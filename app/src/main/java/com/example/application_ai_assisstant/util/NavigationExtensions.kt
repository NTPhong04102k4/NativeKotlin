package com.example.application_ai_assisstant.util

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import com.example.application_ai_assisstant.R
import com.example.application_ai_assisstant.ui.discovery.DiscoveryActivity
import com.example.application_ai_assisstant.ui.personal.PersonalActivity
import com.example.application_ai_assisstant.ui.schedule.ScheduleActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

fun AppCompatActivity.setupBottomNavigation(bottomNavigationView: BottomNavigationView, currentItemId: Int) {
    bottomNavigationView.selectedItemId = currentItemId
    bottomNavigationView.setOnItemSelectedListener { item ->
        when (item.itemId) {
            R.id.nav_discovery -> {
                if (currentItemId != R.id.nav_discovery) {
                    startActivity(Intent(this, DiscoveryActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                }
                true
            }
            R.id.nav_schedule -> {
                if (currentItemId != R.id.nav_schedule) {
                    startActivity(Intent(this, ScheduleActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                }
                true
            }
            R.id.nav_personal -> {
                if (currentItemId != R.id.nav_personal) {
                    startActivity(Intent(this, PersonalActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                }
                true
            }
            else -> false
        }
    }
}
