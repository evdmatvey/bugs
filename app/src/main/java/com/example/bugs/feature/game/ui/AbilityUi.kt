package com.example.bugs.feature.game.ui

import com.example.bugs.R
import com.example.bugs.domain.game.GameAbility

val GameAbility.label: Int
    get() = when (this) {
        GameAbility.BAIT -> R.string.ability_bait
        GameAbility.SLOW -> R.string.ability_slow
        GameAbility.FREEZE -> R.string.ability_freeze
        GameAbility.MULTITOUCH -> R.string.ability_multitouch
        GameAbility.BOMB -> R.string.ability_bomb
        GameAbility.CHAIN -> R.string.ability_chain
    }

val GameAbility.icon: Int
    get() = when (this) {
        GameAbility.BAIT -> R.drawable.ic_ability_bait
        GameAbility.SLOW -> R.drawable.ic_ability_slow
        GameAbility.FREEZE -> R.drawable.ic_ability_freeze
        GameAbility.MULTITOUCH -> R.drawable.ic_ability_multitouch
        GameAbility.BOMB -> R.drawable.ic_ability_bomb
        GameAbility.CHAIN -> R.drawable.ic_ability_chain
    }

val GameAbility.color: Int
    get() = when (this) {
        GameAbility.BAIT -> R.color.ability_bait
        GameAbility.SLOW -> R.color.ability_slow
        GameAbility.FREEZE -> R.color.ability_freeze
        GameAbility.MULTITOUCH -> R.color.ability_multitouch
        GameAbility.BOMB -> R.color.ability_bomb
        GameAbility.CHAIN -> R.color.ability_chain
    }
