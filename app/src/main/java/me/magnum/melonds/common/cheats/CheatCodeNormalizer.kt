package me.magnum.melonds.common.cheats

object CheatCodeNormalizer {
    /**
     * Space-separated 8-digit hex words for melonDS (Action Replay).
     * Accepts Drastic-style newlines, tabs, 16-char lines, and 0x prefixes.
     */
    fun normalizeForEmulator(codeText: String): String {
        val hexOnly = codeText.replace(Regex("[^0-9A-Fa-f]"), "")
        if (hexOnly.length < 16 || hexOnly.length % 8 != 0) {
            return codeText.trim().replace(Regex("\\s+"), " ")
        }
        return hexOnly.uppercase().chunked(8).joinToString(" ")
    }

    fun isValidForEmulator(codeText: String): Boolean {
        val hexOnly = codeText.replace(Regex("[^0-9A-Fa-f]"), "")
        return hexOnly.length >= 16 && hexOnly.length % 8 == 0
    }

    fun hexWordCount(codeText: String): Int {
        return codeText.replace(Regex("[^0-9A-Fa-f]"), "").length / 8
    }
}
