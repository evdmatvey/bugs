package com.example.bugs

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.bugs.feature.authors.ui.AuthorsFragment
import com.example.bugs.feature.registration.ui.RegistrationFragment
import com.example.bugs.feature.rules.ui.RulesFragment
import com.example.bugs.feature.settings.ui.SettingsFragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val navigation = findViewById<BottomNavigationView>(R.id.main_bottom_navigation)
        val viewPager = findViewById<ViewPager2>(R.id.main_view_pager)
        val navigationItems = intArrayOf(
            R.id.navigation_registration,
            R.id.navigation_rules,
            R.id.navigation_authors,
            R.id.navigation_settings
        )

        viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = navigationItems.size

            override fun createFragment(position: Int): Fragment = when (position) {
                0 -> RegistrationFragment()
                1 -> RulesFragment()
                2 -> AuthorsFragment()
                else -> SettingsFragment()
            }
        }

        navigation.setOnItemSelectedListener { item ->
            val position = navigationItems.indexOf(item.itemId)
            if (position >= 0) viewPager.currentItem = position
            position >= 0
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                navigation.selectedItemId = navigationItems[position]
            }
        })

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }
    }
}
