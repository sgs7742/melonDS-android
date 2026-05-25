package me.magnum.melonds.common.cheats

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

object CheatFileTextDecoder {
    private val FALLBACK_CHARSETS = listOf(
        StandardCharsets.UTF_8,
        StandardCharsets.UTF_16LE,
        StandardCharsets.UTF_16BE,
        charsetOrNull("windows-949"),
        charsetOrNull("EUC-KR"),
        charsetOrNull("MS949"),
        StandardCharsets.ISO_8859_1,
    ).filterNotNull().distinctBy { it.name() }

    fun decode(bytes: ByteArray): String {
        if (bytes.isEmpty()) {
            return ""
        }

        decodeWithBom(bytes)?.let { return it }

        if (looksLikeUtf16Le(bytes)) {
            decodeUtf16Le(bytes)?.let { if (hasCheatMarkers(it)) return it }
        }
        if (looksLikeUtf16Be(bytes)) {
            String(bytes, StandardCharsets.UTF_16BE).let { if (hasCheatMarkers(it)) return it }
        }

        for (charset in FALLBACK_CHARSETS) {
            val decoded = decode(bytes, charset)
            if (hasCheatMarkers(decoded)) {
                return decoded
            }
        }

        return decode(bytes, StandardCharsets.UTF_8)
    }

    private fun decodeWithBom(bytes: ByteArray): String? {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return decode(bytes.copyOfRange(3, bytes.size), StandardCharsets.UTF_8)
        }
        if (bytes.size >= 2) {
            if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
                return decodeUtf16Le(bytes.copyOfRange(2, bytes.size))
            }
            if (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
                return String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16BE)
            }
        }
        return null
    }

    private fun decodeUtf16Le(bytes: ByteArray): String? {
        if (bytes.isEmpty() || bytes.size % 2 != 0) {
            return null
        }
        return try {
            String(bytes, StandardCharsets.UTF_16LE)
        } catch (_: Exception) {
            null
        }
    }

    private fun decode(bytes: ByteArray, charset: Charset): String {
        return String(bytes, charset)
    }

    private fun looksLikeUtf16Le(bytes: ByteArray): Boolean {
        if (bytes.size < 8) return false
        val sampleSize = minOf(bytes.size, 256)
        var zeroAtOdd = 0
        var checked = 0
        for (i in 1 until sampleSize step 2) {
            checked++
            if (bytes[i] == 0.toByte()) {
                zeroAtOdd++
            }
        }
        return checked > 0 && zeroAtOdd > checked / 2
    }

    private fun looksLikeUtf16Be(bytes: ByteArray): Boolean {
        if (bytes.size < 8) return false
        val sampleSize = minOf(bytes.size, 256)
        var zeroAtEven = 0
        var checked = 0
        for (i in 0 until sampleSize step 2) {
            checked++
            if (bytes[i] == 0.toByte()) {
                zeroAtEven++
            }
        }
        return checked > 0 && zeroAtEven > checked / 2
    }

    private fun hasCheatMarkers(text: String): Boolean {
        return text.contains('[') && (
            text.contains("cheat0_desc", ignoreCase = true) ||
                CHEAT_HEADER_MARKER.containsMatchIn(text) ||
                text.lines().any { line ->
                    val trimmed = line.trim()
                    trimmed.startsWith('[') && trimmed.contains(']')
                }
        )
    }

    private val CHEAT_HEADER_MARKER = Regex("""\[[^\]]+\]""")

    private fun charsetOrNull(name: String): Charset? {
        return try {
            Charset.forName(name)
        } catch (_: Exception) {
            null
        }
    }
}
