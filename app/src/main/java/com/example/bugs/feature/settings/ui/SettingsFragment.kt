package com.example.bugs.feature.settings.ui

import android.os.Bundle
import android.view.View
import android.widget.Filterable
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.bugs.R
import com.example.bugs.core.ui.gameModeLabel
import com.example.bugs.data.DeviceStateEntity
import com.example.bugs.domain.game.GameDifficulty
import com.example.bugs.domain.game.GameSettings
import com.example.bugs.domain.game.GameSettingsOptions
import com.example.bugs.feature.game.presentation.GameViewModel
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import kotlinx.coroutines.launch

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    private val fields = listOf(
        R.id.settings_speed,
        R.id.settings_max_cockroaches,
        R.id.settings_bonus_interval,
        R.id.settings_round_duration
    )
    private val arrays = listOf(
        R.array.settings_speed_values,
        R.array.settings_cockroach_values,
        R.array.settings_bonus_interval_values,
        R.array.settings_round_duration_values
    )

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)

        (fields + R.id.settings_mode).forEach { id ->
            val input = requireView().findViewById<MaterialAutoCompleteTextView>(id)
            (input.adapter as Filterable).filter.filter(null)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val model = ViewModelProvider(requireActivity())[GameViewModel::class.java]
        val mode = view.findViewById<MaterialAutoCompleteTextView>(R.id.settings_mode)
        val status = view.findViewById<TextView>(R.id.settings_status)
        val apply = view.findViewById<View>(R.id.settings_apply)
        val retry = view.findViewById<View>(R.id.settings_retry)
        val inputs = fields.map { view.findViewById<MaterialAutoCompleteTextView>(it) }
        val difficulties = GameDifficulty.entries.toList() + null

        inputs.forEachIndexed { index, input -> input.setSimpleItems(arrays[index]) }
        mode.setSimpleItems(difficulties.map { requireContext().gameModeLabel(it?.name) }.toTypedArray())

        var selectedDifficulty: GameDifficulty? = GameDifficulty.MEDIUM
        var custom = model.uiState.value.device.selection.customSettings
        var renderedSelection: DeviceStateEntity? = null

        fun showValues(settings: GameSettings) {
            val indices = listOf(
                GameSettingsOptions.SPEEDS.indexOf(settings.speed),
                GameSettingsOptions.BUG_COUNTS.indexOf(settings.maxBugs),
                GameSettingsOptions.BONUS_INTERVALS.indexOf(settings.bonusInterval),
                GameSettingsOptions.DURATIONS.indexOf(settings.duration)
            )

            inputs.forEachIndexed { index, input ->
                input.setText(resources.getStringArray(arrays[index])[indices[index]], false)
            }
        }

        fun readCustom(): GameSettings {
            val indices = inputs.mapIndexed { index, input ->
                resources.getStringArray(arrays[index]).indexOf(input.text.toString()).coerceAtLeast(0)
            }

            return GameSettings(
                speed = GameSettingsOptions.SPEEDS[indices[0]],
                maxBugs = GameSettingsOptions.BUG_COUNTS[indices[1]],
                bonusInterval = GameSettingsOptions.BONUS_INTERVALS[indices[2]],
                duration = GameSettingsOptions.DURATIONS[indices[3]]
            )
        }

        fun renderMode() {
            mode.setText(requireContext().gameModeLabel(selectedDifficulty?.name), false)
            showValues(selectedDifficulty?.settings() ?: custom)
            inputs.forEach { it.isEnabled = selectedDifficulty == null }
        }

        mode.setOnItemClickListener { _, _, position, _ ->
            if (selectedDifficulty == null) {
                custom = readCustom()
            }

            selectedDifficulty = difficulties[position]
            renderMode()
        }

        apply.setOnClickListener {
            model.applySettings(selectedDifficulty?.settings() ?: readCustom(), selectedDifficulty)
        }
        retry.setOnClickListener { model.reload() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.uiState.collect { state ->
                    if (renderedSelection != state.device.selection) {
                        renderedSelection = state.device.selection
                        selectedDifficulty = state.device.selection.difficulty?.let(GameDifficulty::valueOf)
                        custom = state.device.selection.customSettings
                        renderMode()
                    }

                    val available = !state.device.loading && !state.device.error && !state.settingsSaving

                    mode.isEnabled = available
                    inputs.forEach { it.isEnabled = available && selectedDifficulty == null }
                    apply.isEnabled = available
                    retry.isVisible = state.device.error
                    status.setText(
                        when {
                            state.device.loading -> R.string.data_loading
                            state.device.error -> R.string.data_error
                            state.settingsSaving -> R.string.data_saving
                            state.settingsError -> R.string.settings_save_error
                            else -> R.string.settings_next_round
                        }
                    )
                }
            }
        }
    }
}
