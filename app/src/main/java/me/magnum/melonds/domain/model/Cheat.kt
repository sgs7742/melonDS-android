package me.magnum.melonds.domain.model

import me.magnum.melonds.common.cheats.CheatCodeNormalizer

data class Cheat(val id: Long?, val name: String, val description: String?, val code: String, val enabled: Boolean) {
    fun isValid(): Boolean {
        return CheatCodeNormalizer.isValidForEmulator(code) && CheatCodeNormalizer.hexWordCount(code) <= 128
    }

    fun forEmulator(): Cheat {
        return copy(code = CheatCodeNormalizer.normalizeForEmulator(code))
    }
}