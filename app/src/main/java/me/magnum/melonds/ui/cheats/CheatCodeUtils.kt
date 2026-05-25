package me.magnum.melonds.ui.cheats

import me.magnum.melonds.common.cheats.CheatCodeNormalizer

object CheatCodeUtils {
    fun formatCheatCode(input: String): String {
        val stringBuilder = StringBuilder()
        var cheatLineSize = 0
        var cheatWordSize = 0

        input.forEach { char ->
            if (char == '\n' || char == ' ') {
                return@forEach
            }
            if (cheatLineSize == 16) {
                stringBuilder.append('\n')
                cheatLineSize = 0
                cheatWordSize = 0
            } else if (cheatWordSize == 8) {
                stringBuilder.append(' ')
                cheatWordSize = 0
            }
            if (isValidChar(char)) {
                stringBuilder.append(char.uppercaseChar())
                cheatLineSize++
                cheatWordSize++
            }
        }

        return stringBuilder.toString()
    }

    fun isCheatCodeValid(codeText: String): Boolean {
        return codeText.replace("[ \n]".toRegex(), "").length % 16 == 0
    }

    fun normalizeCheatCode(codeText: String): String {
        return CheatCodeNormalizer.normalizeForEmulator(codeText)
    }

    private fun isValidChar(char: Char): Boolean {
        val upperChar = char.uppercaseChar()
        return upperChar in '0'..'9' || upperChar in 'A'..'F'
    }
}
