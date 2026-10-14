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
import com.example.bugs.BugsApplication
import com.example.bugs.R
import com.example.bugs.data.DeviceData
import com.example.bugs.feature.registration.ui.RegistrationFragment
import com.google.android.material.button.MaterialButton
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val repository = (requireActivity().application as BugsApplication).repository
        val status = view.findViewById<TextView>(R.id.profile_status)
        val create = view.findViewById<MaterialButton>(R.id.profile_create)
        var selecting = false

        create.setText(
            if (childFragmentManager.findFragmentById(R.id.profile_form) == null) {
                R.string.profile_create
            } else {
                R.string.profile_close_form
            }
        )
        create.setOnClickListener { toggleForm(create) }
        view.findViewById<View>(R.id.profile_retry).setOnClickListener { repository.reload() }

        val selectPlayer: (Long) -> Unit = { id ->
            if (!selecting) {
                selecting = true

                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        repository.selectPlayer(id)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        status.setText(R.string.profile_select_error)
                    } finally {
                        selecting = false
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.device.collect { render(view, it, selectPlayer) }
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

    private fun render(view: View, data: DeviceData, selectPlayer: (Long) -> Unit) {
        val players = view.findViewById<LinearLayout>(R.id.profile_players)
        val status = view.findViewById<TextView>(R.id.profile_status)
        val activePlayer = data.activePlayer
        val available = !data.loading && !data.error

        view.findViewById<View>(R.id.profile_create).isEnabled = available
        view.findViewById<View>(R.id.profile_retry).isVisible = data.error
        status.text = when {
            data.loading -> getString(R.string.data_loading)
            data.error -> getString(R.string.data_error)
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
                    isEnabled = available
                    setOnClickListener { selectPlayer(player.id) }
                }
            )
        }
    }
}
