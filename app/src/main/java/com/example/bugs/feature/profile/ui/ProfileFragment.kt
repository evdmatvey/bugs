package com.example.bugs.feature.profile.ui

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.bugs.R
import com.example.bugs.feature.profile.presentation.ProfileUiState
import com.example.bugs.feature.profile.presentation.ProfileViewModel
import com.example.bugs.feature.registration.ui.RegistrationFragment
import com.google.android.material.button.MaterialButton
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private val model: ProfileViewModel by viewModel()
    private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val create = view.findViewById<MaterialButton>(R.id.profile_create)

        create.setText(
            if (childFragmentManager.findFragmentById(R.id.profile_form) == null) {
                R.string.profile_create
            } else {
                R.string.profile_close_form
            }
        )
        create.setOnClickListener { toggleForm(create) }
        view.findViewById<View>(R.id.profile_retry).setOnClickListener { model.reload() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.uiState.collect { render(view, it) }
            }
        }
    }

    private fun toggleForm(create: MaterialButton) {
        val existing = childFragmentManager.findFragmentById(R.id.profile_form)

        if (existing == null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.profile_form, RegistrationFragment())
                .commitNow()
            create.setText(R.string.profile_close_form)
        } else {
            childFragmentManager.beginTransaction().remove(existing).commitNow()
            create.setText(R.string.profile_create)
        }
    }

    private fun render(view: View, state: ProfileUiState) {
        val data = state.device
        val players = view.findViewById<LinearLayout>(R.id.profile_players)
        val status = view.findViewById<TextView>(R.id.profile_status)
        val activePlayer = data.activePlayer
        val available = !data.loading && !data.error

        view.findViewById<View>(R.id.profile_create).isEnabled = available
        view.findViewById<View>(R.id.profile_retry).isVisible = data.error
        status.text = when {
            data.loading -> getString(R.string.data_loading)
            data.error -> getString(R.string.data_error)
            state.selectionError -> getString(R.string.profile_select_error)
            activePlayer != null -> getString(R.string.profile_active, activePlayer.fullName)
            else -> getString(R.string.profile_empty)
        }

        players.removeAllViews()

        data.players.forEach { player ->
            val birthDate = LocalDate.ofEpochDay(player.birthDate).format(dateFormatter)
            val selected = if (activePlayer?.id == player.id) getString(R.string.profile_selected) else ""

            players.addView(
                MaterialButton(requireContext()).apply {
                    text = getString(R.string.profile_choice, player.fullName, player.course, birthDate, selected)
                    isEnabled = available && !state.selecting
                    setOnClickListener { model.selectPlayer(player.id) }
                }
            )
        }
    }
}
