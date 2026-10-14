package com.example.bugs.feature.game.ui

import android.os.Bundle
import android.view.Choreographer
import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.bugs.MainActivity
import com.example.bugs.R
import com.example.bugs.core.ui.gameModeLabel
import com.example.bugs.feature.game.presentation.GamePhase
import com.example.bugs.feature.game.presentation.GameUiState
import com.example.bugs.feature.game.presentation.GameViewModel
import com.example.bugs.feature.game.presentation.SaveStatus
import com.google.android.material.button.MaterialButton
import java.util.Locale
import kotlin.math.ceil
import kotlinx.coroutines.launch

class GameFragment : Fragment(R.layout.fragment_game), Choreographer.FrameCallback {

    private lateinit var model: GameViewModel

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        model = ViewModelProvider(requireActivity())[GameViewModel::class.java]

        val field = view.findViewById<GameFieldView>(R.id.game_field)
        val status = view.findViewById<TextView>(R.id.game_status)
        val overlay = view.findViewById<View>(R.id.game_overlay)
        val title = view.findViewById<TextView>(R.id.game_overlay_title)
        val body = view.findViewById<TextView>(R.id.game_overlay_body)
        val action = view.findViewById<MaterialButton>(R.id.game_action)

        field.onTap = { x, y -> model.tap(x, y, System.nanoTime()) }
        action.setOnClickListener { performAction(field) }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.uiState.collect { state ->
                    field.round = state.round
                    field.active = state.phase == GamePhase.RUNNING
                    field.invalidate()

                    val remaining = if (state.phase == GamePhase.FINISHED) 0 else {
                        ceil(state.round?.remaining ?: state.settings.duration.toDouble()).toInt()
                    }

                    status.text = getString(R.string.game_status, state.result?.score ?: state.round?.score ?: 0, remaining)
                    overlay.isVisible = state.phase != GamePhase.RUNNING
                    action.isEnabled = when (state.phase) {
                        GamePhase.READY -> !state.device.loading && !state.settingsSaving
                        GamePhase.FINISHED -> state.saveStatus == SaveStatus.ERROR ||
                            (state.saveStatus == SaveStatus.SAVED && !state.device.loading && !state.settingsSaving)
                        else -> true
                    }

                    when (state.phase) {
                        GamePhase.READY -> {
                            title.setText(R.string.game_ready)
                            body.text = readyBody(state)
                            action.setText(
                                when {
                                    state.device.error -> R.string.data_retry
                                    state.device.activePlayer == null -> R.string.game_choose_profile
                                    else -> R.string.game_start
                                }
                            )
                        }

                        GamePhase.PAUSED -> {
                            title.setText(R.string.game_pause)
                            body.text = getString(R.string.game_pause_player, state.roundPlayerName, getString(R.string.game_pause_body))
                            action.setText(R.string.game_resume)
                        }

                        GamePhase.FINISHED -> {
                            title.setText(R.string.game_finished)
                            body.text = finishedBody(state)
                            action.setText(
                                if (state.saveStatus == SaveStatus.ERROR || state.device.error) {
                                    R.string.data_retry
                                } else {
                                    R.string.game_replay
                                }
                            )
                        }

                        GamePhase.RUNNING -> Unit
                    }
                }
            }
        }
    }

    private fun performAction(field: GameFieldView) {
        val state = model.uiState.value

        when {
            state.phase == GamePhase.FINISHED && state.saveStatus == SaveStatus.ERROR -> model.retrySave()
            state.device.error -> model.reload()
            state.device.activePlayer == null && state.phase != GamePhase.PAUSED -> {
                (requireActivity() as MainActivity).openProfile()
            }
            state.phase == GamePhase.PAUSED -> model.resume()
            else -> {
                field.clearEffects()
                model.start()
            }
        }
    }

    private fun readyBody(state: GameUiState): String {
        if (state.device.loading) return getString(R.string.data_loading)
        if (state.device.error) return getString(R.string.data_error)

        val player = state.device.activePlayer ?: return getString(R.string.profile_empty)
        val settings = state.settings
        val parameters = getString(
            R.string.game_ready_body,
            String.format(Locale.getDefault(), "%.1f", settings.speed),
            settings.maxBugs,
            settings.bonusInterval,
            settings.duration
        )
        val playerMode = getString(
            R.string.game_player_mode,
            player.fullName,
            requireContext().gameModeLabel(state.device.selection.difficulty)
        )

        return getString(R.string.game_ready_player, playerMode, parameters)
    }

    private fun finishedBody(state: GameUiState): String {
        val result = requireNotNull(state.result)
        val summary = getString(
            R.string.game_results,
            result.score,
            result.hits,
            result.misses,
            String.format(Locale.getDefault(), "%.1f", result.accuracy)
        )
        val saveMessage = getString(
            when (state.saveStatus) {
                SaveStatus.NONE, SaveStatus.SAVING -> R.string.data_saving
                SaveStatus.SAVED -> R.string.game_saved
                SaveStatus.ERROR -> R.string.game_save_error
            }
        )
        val playerMode = getString(
            R.string.game_player_mode,
            state.roundPlayerName,
            requireContext().gameModeLabel(result.difficulty)
        )
        val body = getString(R.string.game_finished_player, playerMode, summary, saveMessage)

        return if (state.device.error) getString(R.string.game_ready_player, body, getString(R.string.data_error)) else body
    }

    override fun onResume() {
        super.onResume()
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        model.frame(frameTimeNanos)

        if (isResumed) {
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onPause() {
        model.pause()
        Choreographer.getInstance().removeFrameCallback(this)
        super.onPause()
    }
}
