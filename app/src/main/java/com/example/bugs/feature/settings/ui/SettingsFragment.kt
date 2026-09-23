package com.example.bugs.feature.settings.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.bugs.R
import com.google.android.material.textfield.MaterialAutoCompleteTextView

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.setDropdown(R.id.settings_speed, R.array.settings_speed_values, 1)
        view.setDropdown(R.id.settings_max_cockroaches, R.array.settings_cockroach_values, 1)
        view.setDropdown(R.id.settings_bonus_interval, R.array.settings_bonus_interval_values, 2)
        view.setDropdown(R.id.settings_round_duration, R.array.settings_round_duration_values, 1)

        view.findViewById<View>(R.id.settings_apply).setOnClickListener {
            Toast.makeText(requireContext(), R.string.settings_applied, Toast.LENGTH_SHORT).show()
        }
    }
}

private fun View.setDropdown(viewId: Int, itemsId: Int, defaultIndex: Int) {
    findViewById<MaterialAutoCompleteTextView>(viewId).apply {
        setSimpleItems(itemsId)
        setText(resources.getStringArray(itemsId)[defaultIndex], false)
    }
}
