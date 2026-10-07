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
import com.example.bugs.R
import com.example.bugs.feature.game.presentation.GamePhase
import com.example.bugs.feature.game.presentation.GameViewModel
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import kotlin.math.ceil

class GameFragment : Fragment(R.layout.fragment_game), Choreographer.FrameCallback {

    private lateinit var model: GameViewModel

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        model = ViewModelProvider(requireActivity())[GameViewModel::class.java]

        val field = view.findViewById<GameFieldView>(R.id.game_field)
        val status = view.findViewById<TextView>(R.id.game_status)
        val overlay = view.findViewById<View>(R.id.game_overlay)
        val title = view.findViewById<TextView>(R.id.game_overlay_title)
        val body = view.findViewById<TextView>(R.id.game_overlay_body)
        val action = view.findViewById<MaterialButton>(R.id.game_action)

        field.onTap = { x, y -> model.tap(x, y, System.nanoTime()) }

        action.setOnClickListener {
            if (model.uiState.value.phase == GamePhase.PAUSED) {
                model.resume()
            } else {
                field.clearEffects()
                model.start()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.uiState.collect { state ->
                    field.round = state.round
                    field.active = state.phase == GamePhase.RUNNING
                    field.invalidate()

                    status.text = getString(
                        R.string.game_status,
                        state.round?.score ?: 0,
                        ceil(state.round?.remaining ?: state.settings.duration.toDouble()).toInt()
                    )

                    overlay.isVisible = state.phase != GamePhase.RUNNING

                    when (state.phase) {
                        GamePhase.READY -> {
                            title.setText(R.string.game_ready)

                            val settings = state.settings
                            body.text = getString(
                                R.string.game_ready_body,
                                settings.speed.toString().replace('.', ','),
                                settings.maxBugs,
                                settings.bonusInterval,
                                settings.duration
                            )
                            action.setText(R.string.game_start)
                        }

                        GamePhase.PAUSED -> {
                            title.setText(R.string.game_pause)
                            body.setText(R.string.game_pause_body)
                            action.setText(R.string.game_resume)
                        }

                        GamePhase.FINISHED -> {
                            title.setText(R.string.game_finished)

                            val round = requireNotNull(state.round)
                            body.text = getString(
                                R.string.game_results,
                                round.score,
                                round.hits,
                                round.misses,
                                String.format("%.1f", round.accuracy)
                            )
                            action.setText(R.string.game_replay)
                        }

                        GamePhase.RUNNING -> Unit
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        model.frame(frameTimeNanos)
        if (isResumed) Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onPause() {
        model.pause()
        Choreographer.getInstance().removeFrameCallback(this)
        super.onPause()
    }
}
