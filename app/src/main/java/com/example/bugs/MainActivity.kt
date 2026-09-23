package com.example.bugs

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.bugs.feature.registration.ui.RegistrationFragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        findViewById<BottomNavigationView>(R.id.main_bottom_navigation)
            .setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.navigation_registration -> {
                        if (supportFragmentManager
                                .findFragmentById(R.id.main_fragment_container) !is RegistrationFragment
                        ) {
                            supportFragmentManager
                                .beginTransaction()
                                .replace(
                                    R.id.main_fragment_container,
                                    RegistrationFragment()
                                )
                                .commit()
                        }
                        true
                    }

                    else -> false
                }
            }

        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(
                    R.id.main_fragment_container,
                    RegistrationFragment()
                )
                .commit()
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }
    }
}
