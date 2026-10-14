package com.example.bugs

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.bugs.feature.game.ui.GameFragment
import com.example.bugs.feature.profile.ui.ProfileFragment
import com.example.bugs.feature.records.ui.RecordsFragment
import com.example.bugs.feature.rules.ui.RulesFragment
import com.example.bugs.feature.settings.ui.SettingsFragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    fun openProfile() {
        findViewById<ViewPager2>(R.id.main_view_pager).setCurrentItem(1, false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val navigation = findViewById<BottomNavigationView>(R.id.main_bottom_navigation)
        val viewPager = findViewById<ViewPager2>(R.id.main_view_pager)

        val navigationItems = intArrayOf(
            R.id.navigation_game,
            R.id.navigation_profile,
            R.id.navigation_records,
            R.id.navigation_rules,
            R.id.navigation_settings
        )

        viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = navigationItems.size

            override fun createFragment(position: Int): Fragment = when (position) {
                0 -> GameFragment()
                1 -> ProfileFragment()
                2 -> RecordsFragment()
                3 -> RulesFragment()
                else -> SettingsFragment()
            }
        }

        navigation.setOnItemSelectedListener { item ->
            val position = navigationItems.indexOf(item.itemId)
            if (position >= 0 && position != viewPager.currentItem) {
                viewPager.setCurrentItem(position, false)
            }

            position >= 0
        }

        viewPager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    navigation.selectedItemId = navigationItems[position]
                }
            }
        )

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)

            insets
        }
    }
}
