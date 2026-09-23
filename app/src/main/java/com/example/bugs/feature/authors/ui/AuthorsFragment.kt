package com.example.bugs.feature.authors.ui

import android.os.Bundle
import android.view.View
import android.widget.ListView
import android.widget.SimpleAdapter
import androidx.fragment.app.Fragment
import com.example.bugs.R

class AuthorsFragment : Fragment(R.layout.fragment_authors) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val authors = listOf(
            mapOf(
                "photo" to R.drawable.photo,
                "name" to getString(R.string.author_matvey_evdokimov)
            )
        )

        view.findViewById<ListView>(R.id.authors_list).adapter = SimpleAdapter(
            requireContext(),
            authors,
            R.layout.item_author,
            arrayOf("photo", "name"),
            intArrayOf(R.id.author_photo, R.id.author_name)
        )
    }
}
