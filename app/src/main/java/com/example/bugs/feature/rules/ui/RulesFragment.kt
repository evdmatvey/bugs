package com.example.bugs.feature.rules.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import com.example.bugs.R

class RulesFragment : Fragment(R.layout.fragment_rules) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rulesHtml = resources
            .openRawResource(R.raw.game_rules)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

        view.findViewById<TextView>(R.id.rules_content).text = HtmlCompat.fromHtml(
            rulesHtml,
            HtmlCompat.FROM_HTML_MODE_COMPACT
        )
    }
}
