package com.example.bugs.domain.player

import java.util.Locale

private val nameWhitespace = Regex("[\\s\\p{Z}]+")

fun cleanPlayerName(fullName: String): String = fullName.replace(nameWhitespace, " ").trim()

fun normalizePlayerName(fullName: String): String = cleanPlayerName(fullName).lowercase(Locale.ROOT)
