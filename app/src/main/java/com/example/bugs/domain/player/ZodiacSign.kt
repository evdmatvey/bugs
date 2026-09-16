package com.example.bugs.domain.player

import java.time.LocalDate
import java.time.MonthDay

enum class ZodiacSign {
    CAPRICORN,
    AQUARIUS,
    PISCES,
    ARIES,
    TAURUS,
    GEMINI,
    CANCER,
    LEO,
    VIRGO,
    LIBRA,
    SCORPIO,
    SAGITTARIUS;

    companion object {
        fun from(date: LocalDate): ZodiacSign {
            val day = MonthDay.from(date)

            return when (day) {
                in MonthDay.of(1, 20)..MonthDay.of(2, 18) -> AQUARIUS
                in MonthDay.of(2, 19)..MonthDay.of(3, 20) -> PISCES
                in MonthDay.of(3, 21)..MonthDay.of(4, 19) -> ARIES
                in MonthDay.of(4, 20)..MonthDay.of(5, 20) -> TAURUS
                in MonthDay.of(5, 21)..MonthDay.of(6, 20) -> GEMINI
                in MonthDay.of(6, 21)..MonthDay.of(7, 22) -> CANCER
                in MonthDay.of(7, 23)..MonthDay.of(8, 22) -> LEO
                in MonthDay.of(8, 23)..MonthDay.of(9, 22) -> VIRGO
                in MonthDay.of(9, 23)..MonthDay.of(10, 22) -> LIBRA
                in MonthDay.of(10, 23)..MonthDay.of(11, 21) -> SCORPIO
                in MonthDay.of(11, 22)..MonthDay.of(12, 21) -> SAGITTARIUS
                else -> CAPRICORN
            }
        }
    }
}