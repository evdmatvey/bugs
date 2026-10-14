package com.example.bugs.feature.records.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Filterable
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.bugs.R
import com.example.bugs.core.ui.labelRes
import com.example.bugs.domain.game.GameDifficulty
import com.example.bugs.feature.records.presentation.RecordRow
import com.example.bugs.feature.records.presentation.RecordsSection
import com.example.bugs.feature.records.presentation.RecordsUiState
import com.example.bugs.feature.records.presentation.RecordsViewModel
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

class RecordsFragment : Fragment(R.layout.fragment_records) {

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)

        val mode = requireView().findViewById<MaterialAutoCompleteTextView>(R.id.records_mode)
        (mode.adapter as Filterable).filter.filter(null)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val model = ViewModelProvider(this)[RecordsViewModel::class.java]
        val tabs = view.findViewById<MaterialButtonToggleGroup>(R.id.records_tabs)
        val mode = view.findViewById<MaterialAutoCompleteTextView>(R.id.records_mode)
        val list = view.findViewById<RecyclerView>(R.id.records_list)
        val adapter = RecordAdapter()

        list.layoutManager = LinearLayoutManager(requireContext())
        list.adapter = adapter
        mode.setSimpleItems(GameDifficulty.entries.map { getString(it.labelRes()) }.toTypedArray())
        render(view, model.uiState.value, adapter)

        tabs.addOnButtonCheckedListener { _, id, checked ->
            if (checked) {
                model.setSection(if (id == R.id.records_personal) RecordsSection.RECORDS else RecordsSection.RATING)
            }
        }

        mode.setOnItemClickListener { _, _, position, _ ->
            GameDifficulty.entries.getOrNull(position)?.let(model::setDifficulty)
        }

        view.findViewById<View>(R.id.records_retry).setOnClickListener { model.reload() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.uiState.collect { render(view, it, adapter) }
            }
        }
    }

    private fun render(view: View, state: RecordsUiState, adapter: RecordAdapter) {
        val tabs = view.findViewById<MaterialButtonToggleGroup>(R.id.records_tabs)
        val mode = view.findViewById<MaterialAutoCompleteTextView>(R.id.records_mode)
        val status = view.findViewById<TextView>(R.id.records_status)
        val sectionId = if (state.section == RecordsSection.RECORDS) R.id.records_personal else R.id.records_rating

        if (tabs.checkedButtonId != sectionId) {
            tabs.check(sectionId)
        }

        mode.setText(getString(state.difficulty.labelRes()), false)
        mode.isEnabled = !state.loading && !state.error
        view.findViewById<View>(R.id.records_mode_layout).isVisible = state.section == RecordsSection.RECORDS
        view.findViewById<View>(R.id.records_retry).isVisible = state.error

        status.setText(
            when {
                state.error -> R.string.data_error
                state.loading -> R.string.data_loading
                state.rows.isEmpty() -> R.string.records_empty
                state.section == RecordsSection.RECORDS -> R.string.records_personal_hint
                else -> R.string.records_rating_hint
            }
        )

        adapter.submitList(if (state.error || state.loading) emptyList() else state.rows)
    }
}

private class RecordAdapter : ListAdapter<RecordRow, RecordAdapter.Holder>(object : DiffUtil.ItemCallback<RecordRow>() {
    override fun areItemsTheSame(old: RecordRow, new: RecordRow): Boolean =
        old.difficulty == new.difficulty && old.result?.result?.roundId == new.result?.result?.roundId

    override fun areContentsTheSame(old: RecordRow, new: RecordRow): Boolean = old == new
}) {

    private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.record_title)
        val details: TextView = view.findViewById(R.id.record_details)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_record, parent, false)
    )

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = getItem(position)
        val context = holder.itemView.context
        val level = context.getString(item.difficulty.labelRes())
        val row = item.result

        if (row == null) {
            holder.title.text = level
            holder.details.setText(R.string.records_no_record)
            return
        }

        val result = row.result
        val date = Instant.ofEpochMilli(result.finishedAt)
            .atZone(ZoneId.systemDefault())
            .format(dateFormatter)

        holder.title.text = if (item.place == null) {
            context.getString(R.string.record_title, row.fullName, result.score)
        } else {
            context.getString(R.string.record_ranked_title, item.place, row.fullName, result.score)
        }

        holder.details.text = context.getString(
            R.string.record_details,
            level,
            result.accuracy,
            result.hits,
            result.misses,
            date
        )
    }
}
