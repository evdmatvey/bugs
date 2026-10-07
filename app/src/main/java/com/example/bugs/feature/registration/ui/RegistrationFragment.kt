package com.example.bugs.feature.registration.ui

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.bugs.R
import com.example.bugs.domain.game.GameDifficulty
import com.example.bugs.domain.player.Gender
import com.example.bugs.domain.player.ZodiacSign
import com.example.bugs.feature.registration.presentation.RegistrationViewModel
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class RegistrationFragment : Fragment(R.layout.fragment_registration) {

    private lateinit var viewModel: RegistrationViewModel

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[RegistrationViewModel::class.java]

        val fullNameLayout =
            view.findViewById<TextInputLayout>(R.id.registration_full_name_layout)
        val fullNameInput = view.findViewById<TextInputEditText>(R.id.registration_full_name)

        val genderGroup = view.findViewById<RadioGroup>(R.id.registration_gender)
        val genderError = view.findViewById<TextView>(R.id.registration_gender_error)

        val courseLayout =
            view.findViewById<TextInputLayout>(R.id.registration_course_layout)
        val courseInput = view.findViewById<MaterialAutoCompleteTextView>(R.id.registration_course)

        val difficultyInput = view.findViewById<SeekBar>(R.id.registration_difficulty)

        val birthDateLayout =
            view.findViewById<TextInputLayout>(R.id.registration_birth_date_layout)
        val birthDateInput =
            view.findViewById<TextInputEditText>(R.id.registration_birth_date)

        val submitButton = view.findViewById<View>(R.id.registration_submit)

        val result = view.findViewById<View>(R.id.registration_result)
        val resultText = view.findViewById<TextView>(R.id.registration_result_text)
        val zodiacImage = view.findViewById<ImageView>(R.id.registration_zodiac_image)

        val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

        courseInput.setSimpleItems(R.array.registration_courses)

        fullNameInput.doAfterTextChanged {
            viewModel.onFullNameChanged(it?.toString().orEmpty())
        }

        genderGroup.setOnCheckedChangeListener { _, checkedId ->
            val gender = when (checkedId) {
                R.id.registration_gender_male -> Gender.MALE
                R.id.registration_gender_female -> Gender.FEMALE
                else -> return@setOnCheckedChangeListener
            }

            viewModel.onGenderChanged(gender)
        }

        courseInput.onItemClickListener = AdapterView.OnItemClickListener { parent, _, position, _ ->
            val course = parent
                .getItemAtPosition(position)
                .toString()
                .toIntOrNull()
                ?: return@OnItemClickListener

            viewModel.onCourseChanged(course)
        }

        difficultyInput.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {
                    val difficulty =
                        GameDifficulty.entries.getOrNull(progress) ?: return

                    viewModel.onDifficultyChanged(difficulty)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit

                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            }
        )

        birthDateInput.setOnClickListener {
            val initialDate =
                viewModel.uiState.value.birthDate ?: LocalDate.now()

            DatePickerDialog(
                requireContext(),
                { _, year, month, dayOfMonth ->
                    val birthDate =
                        LocalDate.of(year, month + 1, dayOfMonth)

                    viewModel.onBirthDateChanged(birthDate)
                    birthDateInput.setText(birthDate.format(dateFormatter))
                },
                initialDate.year,
                initialDate.monthValue - 1,
                initialDate.dayOfMonth
            ).apply {
                datePicker.maxDate = System.currentTimeMillis()
            }.show()
        }

        submitButton.setOnClickListener {
            viewModel.onSubmit()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    fullNameLayout.error = if (state.fullNameError) {
                        getString(R.string.registration_full_name_required)
                    } else {
                        null
                    }

                    genderError.isVisible = state.genderError

                    courseLayout.error = if (state.courseError) {
                        getString(R.string.registration_course_required)
                    } else {
                        null
                    }

                    birthDateLayout.error = if (state.birthDateError) {
                        getString(R.string.registration_birth_date_required)
                    } else {
                        null
                    }

                    val player = state.submittedPlayer
                    result.isVisible = player != null

                    if (player != null) {
                        val zodiacSign = player.zodiacSign

                        resultText.text = getString(
                            R.string.registration_result_format,
                            player.fullName,
                            getString(player.gender.labelRes()),
                            player.course,
                            getString(player.difficulty.labelRes()),
                            player.birthDate.format(dateFormatter),
                            getString(zodiacSign.labelRes())
                        )
                        zodiacImage.setImageResource(zodiacSign.drawableRes())
                    }
                }
            }
        }
    }
}

private fun Gender.labelRes(): Int = when (this) {
    Gender.MALE -> R.string.registration_gender_male
    Gender.FEMALE -> R.string.registration_gender_female
}

private fun GameDifficulty.labelRes(): Int = when (this) {
    GameDifficulty.EASY -> R.string.registration_difficulty_easy
    GameDifficulty.MEDIUM -> R.string.registration_difficulty_medium
    GameDifficulty.HARD -> R.string.registration_difficulty_hard
}

private fun ZodiacSign.labelRes(): Int = when (this) {
    ZodiacSign.CAPRICORN -> R.string.zodiac_capricorn
    ZodiacSign.AQUARIUS -> R.string.zodiac_aquarius
    ZodiacSign.PISCES -> R.string.zodiac_pisces
    ZodiacSign.ARIES -> R.string.zodiac_aries
    ZodiacSign.TAURUS -> R.string.zodiac_taurus
    ZodiacSign.GEMINI -> R.string.zodiac_gemini
    ZodiacSign.CANCER -> R.string.zodiac_cancer
    ZodiacSign.LEO -> R.string.zodiac_leo
    ZodiacSign.VIRGO -> R.string.zodiac_virgo
    ZodiacSign.LIBRA -> R.string.zodiac_libra
    ZodiacSign.SCORPIO -> R.string.zodiac_scorpio
    ZodiacSign.SAGITTARIUS -> R.string.zodiac_sagittarius
}

private fun ZodiacSign.drawableRes(): Int = when (this) {
    ZodiacSign.CAPRICORN -> R.drawable.zodiac_capricorn
    ZodiacSign.AQUARIUS -> R.drawable.zodiac_aquarius
    ZodiacSign.PISCES -> R.drawable.zodiac_pisces
    ZodiacSign.ARIES -> R.drawable.zodiac_aries
    ZodiacSign.TAURUS -> R.drawable.zodiac_taurus
    ZodiacSign.GEMINI -> R.drawable.zodiac_gemini
    ZodiacSign.CANCER -> R.drawable.zodiac_cancer
    ZodiacSign.LEO -> R.drawable.zodiac_leo
    ZodiacSign.VIRGO -> R.drawable.zodiac_virgo
    ZodiacSign.LIBRA -> R.drawable.zodiac_libra
    ZodiacSign.SCORPIO -> R.drawable.zodiac_scorpio
    ZodiacSign.SAGITTARIUS -> R.drawable.zodiac_sagittarius
}
