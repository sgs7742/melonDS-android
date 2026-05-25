package me.magnum.melonds.common.cheats

import me.magnum.melonds.domain.model.RomCheatsBackup
import me.magnum.melonds.domain.model.RomCheatsBackupCheat
import me.magnum.melonds.domain.model.RomCheatsBackupFolder

object ChtFileCodec {
    data class ParsedCheat(val name: String, val code: String)

    // Action Replay / DeSmuME / Drastic style: [Name], [Name]+, [Name]*
    private val CHEAT_HEADER_REGEX = Regex("""^\[(.+?)]\s*[*+]?\s*$""")

    private val LIBRETRO_DESC_REGEX = Regex("""cheat(\d+)_desc\s*=\s*"(.*)"""", RegexOption.IGNORE_CASE)
    private val LIBRETRO_CODE_REGEX = Regex("""cheat(\d+)_code\s*=\s*"(.*)"""", RegexOption.IGNORE_CASE)

    private val HEX_CODE_LINE_REGEX = Regex("""^[\da-fA-F]{8}(?:\s+[\da-fA-F]{8})+$""")

    fun parse(content: String): List<ParsedCheat> {
        val normalized = normalizeLineEndings(content.removePrefix("\uFEFF").trim())

        parseLibretroFormat(normalized)?.takeIf { it.isNotEmpty() }?.let { return it }
        parseActionReplayFormat(normalized)?.takeIf { it.isNotEmpty() }?.let { return it }

        return emptyList()
    }

    private fun normalizeLineEndings(content: String): String {
        return content.replace("\r\n", "\n").replace('\r', '\n')
    }

    private fun parseLibretroFormat(content: String): List<ParsedCheat>? {
        if (!content.contains("cheat0_desc", ignoreCase = true) && !content.contains("cheats =", ignoreCase = true)) {
            return null
        }

        val cheatsByIndex = mutableMapOf<Int, Pair<String?, String?>>()

        content.lineSequence().forEach { rawLine ->
            val line = stripInlineComment(rawLine).trim()
            if (line.isEmpty()) return@forEach

            LIBRETRO_DESC_REGEX.matchEntire(line)?.let { match ->
                val index = match.groupValues[1].toInt()
                val name = match.groupValues[2].trim()
                val existing = cheatsByIndex[index]
                cheatsByIndex[index] = name to existing?.second
                return@forEach
            }

            LIBRETRO_CODE_REGEX.matchEntire(line)?.let { match ->
                val index = match.groupValues[1].toInt()
                val code = normalizeCode(match.groupValues[2])
                val existing = cheatsByIndex[index]
                cheatsByIndex[index] = existing?.first to code
            }
        }

        return cheatsByIndex.toSortedMap().mapNotNull { (_, pair) ->
            val name = pair.first
            val code = pair.second
            if (!name.isNullOrBlank() && !code.isNullOrBlank()) {
                ParsedCheat(name, code)
            } else {
                null
            }
        }
    }

    private fun parseActionReplayFormat(content: String): List<ParsedCheat> {
        val cheats = mutableListOf<ParsedCheat>()
        var currentName: String? = null
        val codeLines = mutableListOf<String>()

        fun flushCheat() {
            val name = currentName ?: return
            if (codeLines.isEmpty()) {
                return
            }
            val code = normalizeCode(codeLines.joinToString(" "))
            if (code.isNotBlank()) {
                cheats.add(ParsedCheat(name, code))
            }
            codeLines.clear()
        }

        content.lineSequence().forEach { rawLine ->
            val line = stripInlineComment(rawLine).trim()
            if (line.isEmpty() || isSkippableLine(line)) {
                return@forEach
            }

            val headerMatch = CHEAT_HEADER_REGEX.matchEntire(line)
            if (headerMatch != null) {
                flushCheat()
                currentName = headerMatch.groupValues[1].trim().trimEnd('+', '*').trim()
                return@forEach
            }

            if (currentName != null && isCodeLine(line)) {
                codeLines.add(line)
            }
        }

        flushCheat()
        return cheats
    }

    private fun stripInlineComment(line: String): String {
        var result = line
        val semicolonIndex = result.indexOf(';')
        if (semicolonIndex >= 0) {
            result = result.substring(0, semicolonIndex)
        }
        val slashIndex = result.indexOf("//")
        if (slashIndex >= 0) {
            result = result.substring(0, slashIndex)
        }
        return result
    }

    private fun isSkippableLine(line: String): Boolean {
        if (line.startsWith("#")) return true
        if (line.startsWith("//")) return true
        // Skip standalone game-id headers like [IPKE01] with no cheat codes following
        return false
    }

    private fun isCodeLine(line: String): Boolean {
        if (HEX_CODE_LINE_REGEX.matches(line)) {
            return true
        }
        val compact = line.replace(Regex("\\s+"), "")
        if (compact.length == 8 && compact.all { isHexChar(it) }) {
            return true
        }
        // Drastic / some exporters: 94000130FCFF0000 (16 hex chars, no space)
        return compact.length >= 16 && compact.length % 8 == 0 && compact.all { isHexChar(it) }
    }

    private fun normalizeCode(code: String): String {
        return CheatCodeNormalizer.normalizeForEmulator(code)
    }

    private fun isHexChar(c: Char): Boolean {
        return c.isDigit() || c in 'A'..'F' || c in 'a'..'f'
    }

    fun toRomCheatsBackup(
        cheats: List<ParsedCheat>,
        gameCode: String,
        gameChecksum: String,
        gameName: String,
        folderName: String,
    ): RomCheatsBackup {
        return RomCheatsBackup(
            gameCode = gameCode,
            gameChecksum = gameChecksum,
            gameName = gameName,
            folders = listOf(
                RomCheatsBackupFolder(
                    name = folderName,
                    cheats = cheats.map {
                        RomCheatsBackupCheat(
                            name = it.name,
                            description = null,
                            code = it.code,
                            enabled = false,
                        )
                    },
                ),
            ),
        )
    }

    fun serialize(backup: RomCheatsBackup): String {
        val builder = StringBuilder()
        backup.folders.flatMap { folder ->
            folder.cheats.map { cheat -> cheat }
        }.forEachIndexed { index, cheat ->
            if (index > 0) {
                builder.appendLine()
            }
            builder.appendLine("[${cheat.name}]+")
            builder.append(serializeCode(cheat.code))
        }
        return builder.toString()
    }

    private fun serializeCode(code: String): String {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) {
            return ""
        }

        if (trimmed.contains('\n')) {
            return trimmed.lines().joinToString("\n") { it.trim() }.let { if (it.endsWith("\n")) it else "$it\n" }
        }

        val tokens = trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }
        return buildString {
            tokens.chunked(2).forEach { chunk ->
                appendLine(chunk.joinToString(" "))
            }
        }
    }
}
