package com.example.bugs.feature.settings.ui

import android.os.Bundle
import android.view.View
import android.widget.Filterable
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.bugs.R
import com.example.bugs.domain.game.GameSettings
import com.example.bugs.feature.game.presentation.GameViewModel
import com.google.android.material.textfield.MaterialAutoCompleteTextView

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)

        // Android restores dropdown text with filtering; keep all selectable options.
        listOf(
            R.id.settings_speed,
            R.id.settings_max_cockroaches,
            R.id.settings_bonus_interval,
            R.id.settings_round_duration
        ).forEach { id ->
            (requireView().findViewById<MaterialAutoCompleteTextView>(id).adapter as Filterable)
                .filter.filter(null)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val model = ViewModelProvider(requireActivity())[GameViewModel::class.java]
        val settings = model.uiState.value.settings

        val speeds = listOf(.5f, 1f, 1.5f, 2f)
        val counts = listOf(5, 10, 15, 20)
        val intervals = listOf(5, 10, 15, 30)
        val durations = listOf(30, 60, 90, 120)

        view.setDropdown(
            R.id.settings_speed,
            R.array.settings_speed_values,
            speeds.indexOf(settings.speed)
        )
        view.setDropdown(
            R.id.settings_max_cockroaches,
            R.array.settings_cockroach_values,
            counts.indexOf(settings.maxBugs)
        )
        view.setDropdown(
            R.id.settings_bonus_interval,
            R.array.settings_bonus_interval_values,
            intervals.indexOf(settings.bonusInterval)
        )
        view.setDropdown(
            R.id.settings_round_duration,
            R.array.settings_round_duration_values,
            durations.indexOf(settings.duration)
        )

        view.findViewById<View>(R.id.settings_apply).setOnClickListener {
            fun index(id: Int, items: Int) = resources.getStringArray(items)
                .indexOf(view.findViewById<MaterialAutoCompleteTextView>(id).text.toString())
                .coerceAtLeast(0)

            model.applySettings(
                GameSettings(
                    speeds[index(R.id.settings_speed, R.array.settings_speed_values)],
                    counts[index(R.id.settings_max_cockroaches, R.array.settings_cockroach_values)],
                    intervals[index(R.id.settings_bonus_interval, R.array.settings_bonus_interval_values)],
                    durations[index(R.id.settings_round_duration, R.array.settings_round_duration_values)]
                )
            )

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
