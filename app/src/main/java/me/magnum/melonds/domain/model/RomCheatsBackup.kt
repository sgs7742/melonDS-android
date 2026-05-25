package me.magnum.melonds.domain.model

data class RomCheatsBackup(
    val version: Int = 1,
    val gameCode: String,
    val gameChecksum: String,
    val gameName: String,
    val folders: List<RomCheatsBackupFolder>,
)

data class RomCheatsBackupFolder(
    val name: String,
    val cheats: List<RomCheatsBackupCheat>,
)

data class RomCheatsBackupCheat(
    val name: String,
    val description: String?,
    val code: String,
    val enabled: Boolean,
)
