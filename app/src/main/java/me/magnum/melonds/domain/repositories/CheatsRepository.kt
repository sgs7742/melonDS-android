package me.magnum.melonds.domain.repositories

import android.net.Uri
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single
import me.magnum.melonds.domain.model.Cheat
import me.magnum.melonds.domain.model.CheatImportProgress
import me.magnum.melonds.domain.model.CheatFolder
import me.magnum.melonds.domain.model.Game
import me.magnum.melonds.domain.model.RomCheatsBackup
import me.magnum.melonds.domain.model.RomInfo
import me.magnum.melonds.ui.cheats.model.CheatSubmissionForm

interface CheatsRepository {
    fun getAllRomCheats(romInfo: RomInfo): Maybe<List<Game>>
    fun findGameForRom(romInfo: RomInfo): Maybe<Game>
    fun getRomEnabledCheats(romInfo: RomInfo): Single<List<Cheat>>
    fun updateCheatsStatus(cheats: List<Cheat>): Completable
    fun addGameCheats(game: Game)
    fun addCheatFolder(folderName: String, game: Game): Completable
    fun addCustomCheat(folder: CheatFolder, cheatForm: CheatSubmissionForm): Completable
    fun updateCheat(cheat: Cheat): Completable
    fun deleteCheat(cheat: Cheat): Completable
    fun deleteCheats(cheats: List<Cheat>): Completable
    fun buildRomCheatsBackup(romInfo: RomInfo): Single<RomCheatsBackup>
    fun restoreRomCheats(romInfo: RomInfo, backup: RomCheatsBackup, targetFolderId: Long?): Completable
    fun writeRomCheatsBackup(uri: Uri, backup: RomCheatsBackup): Completable
    fun readRomCheatsBackup(uri: Uri, romInfo: RomInfo, defaultFolderName: String): Single<RomCheatsBackup>
    fun deleteAllCheats()
    fun importCheats(uri: Uri)
    fun isCheatImportOngoing(): Boolean
    fun getCheatImportProgress(): Observable<CheatImportProgress>
}
