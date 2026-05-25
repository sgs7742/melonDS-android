package me.magnum.melonds.impl

import android.content.Context
import android.net.Uri
import androidx.work.*
import me.magnum.melonds.common.cheats.CheatCodeNormalizer
import me.magnum.melonds.common.cheats.CheatFileTextDecoder
import me.magnum.melonds.common.cheats.ChtFileCodec
import io.reactivex.Completable
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import me.magnum.melonds.common.workers.CheatImportWorker
import me.magnum.melonds.database.MelonDatabase
import me.magnum.melonds.database.entities.CheatEntity
import me.magnum.melonds.database.entities.CheatFolderEntity
import me.magnum.melonds.database.entities.CheatStatusUpdate
import me.magnum.melonds.database.entities.GameEntity
import me.magnum.melonds.domain.model.*
import me.magnum.melonds.domain.repositories.CheatsRepository
import me.magnum.melonds.ui.cheats.model.CheatSubmissionForm
import java.io.OutputStreamWriter
import javax.inject.Inject

class RoomCheatsRepository @Inject constructor(
    private val context: Context,
    private val database: MelonDatabase,
) : CheatsRepository {
    companion object {
        private const val IMPORT_WORKER_NAME = "cheat_import_worker"
        private const val BACKUP_VERSION = 1
    }

    override fun getAllRomCheats(romInfo: RomInfo): Maybe<List<Game>> {
        return mapGames(database.gameDao().findGameWithCheats(romInfo.gameCode, romInfo.headerChecksumString()))
    }

    override fun findGameForRom(romInfo: RomInfo): Maybe<Game> {
        return Maybe.fromCallable {
            database.gameDao().findGame(romInfo.gameCode, romInfo.headerChecksumString())?.toGame()
        }.flatMap { game ->
            if (game != null) Maybe.just(game) else Maybe.empty()
        }.subscribeOn(Schedulers.io())
    }

    override fun getRomEnabledCheats(romInfo: RomInfo): Single<List<Cheat>> {
        return database.cheatDao().getEnabledRomCheats(romInfo.gameCode, romInfo.headerChecksumString()).map {
            it.map { cheat ->
                Cheat(
                    cheat.id,
                    cheat.name,
                    cheat.description,
                    cheat.code,
                    cheat.enabled
                )
            }
        }.subscribeOn(Schedulers.io())
    }

    override fun updateCheatsStatus(cheats: List<Cheat>): Completable {
        val cheatEntities = cheats.map {
            CheatStatusUpdate(it.id!!, it.enabled)
        }

        return Completable.fromAction {
            database.cheatDao().updateCheatsStatus(cheatEntities)
        }.subscribeOn(Schedulers.io())
    }

    override fun addGameCheats(game: Game) {
        val gameEntity = GameEntity(
            null,
            game.name,
            game.gameCode,
            game.gameChecksum
        )

        val gameId = database.gameDao().insertGame(gameEntity)
        val categoryEntities = game.cheats.map { category ->
            CheatFolderEntity(
                null,
                gameId,
                category.name
            )
        }
        val categoryIds = database.cheatFolderDao().insertCheatFolders(categoryEntities)

        val cheatEntities = game.cheats.zip(categoryIds).flatMap { pair ->
            pair.first.cheats.map {
                CheatEntity(
                    null,
                    pair.second,
                    it.name,
                    it.description,
                    it.code,
                    false
                )
            }
        }
        database.cheatDao().insertCheats(cheatEntities)
    }

    override fun addCheatFolder(folderName: String, game: Game): Completable {
        return Completable.fromAction {
            val gameId = ensureGameId(game)
            database.cheatFolderDao().insertCheatFolder(CheatFolderEntity(null, gameId, folderName))
        }.subscribeOn(Schedulers.io())
    }

    override fun addCustomCheat(folder: CheatFolder, cheatForm: CheatSubmissionForm): Completable {
        return Completable.fromAction {
            val cheatEntity = CheatEntity(
                null,
                folder.id!!,
                cheatForm.name,
                cheatForm.description.takeUnless { it.isBlank() },
                CheatCodeNormalizer.normalizeForEmulator(cheatForm.code),
                false,
            )
            database.cheatDao().insertCheat(cheatEntity)
        }.subscribeOn(Schedulers.io())
    }

    override fun updateCheat(cheat: Cheat): Completable {
        return Completable.fromAction {
            val originalEntity = database.cheatDao().getCheat(cheat.id!!) ?: return@fromAction
            val updatedCheatEntity = CheatEntity(
                cheat.id,
                originalEntity.cheatFolderId,
                cheat.name,
                cheat.description,
                CheatCodeNormalizer.normalizeForEmulator(cheat.code),
                cheat.enabled,
            )
            database.cheatDao().updateCheat(updatedCheatEntity)
        }.subscribeOn(Schedulers.io())
    }

    override fun deleteCheat(cheat: Cheat): Completable {
        return deleteCheats(listOf(cheat))
    }

    override fun deleteCheats(cheats: List<Cheat>): Completable {
        return Completable.fromAction {
            val cheatIds = cheats.mapNotNull { it.id }
            if (cheatIds.isEmpty()) {
                return@fromAction
            }
            database.cheatDao().deleteCheats(cheatIds)
        }.subscribeOn(Schedulers.io())
    }

    override fun buildRomCheatsBackup(romInfo: RomInfo): Single<RomCheatsBackup> {
        return getAllRomCheats(romInfo)
            .defaultIfEmpty(emptyList())
            .flatMapSingle { games ->
                val game = games.firstOrNull()
                Single.just(
                    RomCheatsBackup(
                        version = BACKUP_VERSION,
                        gameCode = romInfo.gameCode,
                        gameChecksum = romInfo.headerChecksumString(),
                        gameName = game?.name ?: romInfo.gameTitle,
                        folders = game?.cheats?.map { folder ->
                            RomCheatsBackupFolder(
                                name = folder.name,
                                cheats = folder.cheats.map { cheat ->
                                    RomCheatsBackupCheat(
                                        name = cheat.name,
                                        description = cheat.description,
                                        code = cheat.code,
                                        enabled = cheat.enabled,
                                    )
                                }
                            )
                        } ?: emptyList(),
                    )
                )
            }
            .subscribeOn(Schedulers.io())
    }

    override fun restoreRomCheats(romInfo: RomInfo, backup: RomCheatsBackup, targetFolderId: Long?): Completable {
        return Completable.fromAction {
            val cheatsToImport = backup.folders.flatMap { it.cheats }
            if (cheatsToImport.isEmpty()) {
                return@fromAction
            }

            val folderId = if (targetFolderId != null) {
                targetFolderId
            } else {
                val gameEntity = ensureGameEntity(
                    Game(
                        id = null,
                        name = backup.gameName.ifBlank { romInfo.gameTitle },
                        gameCode = romInfo.gameCode,
                        gameChecksum = romInfo.headerChecksumString(),
                        cheats = emptyList(),
                    )
                )
                val gameId = gameEntity.id!!
                database.cheatFolderDao().deleteFoldersForGame(gameId)
                val folderName = backup.folders.firstOrNull()?.name ?: "Custom"
                database.cheatFolderDao().insertCheatFolder(
                    CheatFolderEntity(null, gameId, folderName)
                )
            }

            database.cheatDao().deleteCheatsInFolder(folderId)
            val cheatEntities = cheatsToImport.map {
                CheatEntity(
                    null,
                    folderId,
                    it.name,
                    it.description,
                    CheatCodeNormalizer.normalizeForEmulator(it.code),
                    it.enabled,
                )
            }
            database.cheatDao().insertCheats(cheatEntities)
        }.subscribeOn(Schedulers.io())
    }

    override fun writeRomCheatsBackup(uri: Uri, backup: RomCheatsBackup): Completable {
        return Completable.fromAction {
            val chtContent = ChtFileCodec.serialize(backup)
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream, Charsets.UTF_8).use { it.write(chtContent) }
            } ?: throw IllegalStateException("Could not open output stream")
        }.subscribeOn(Schedulers.io())
    }

    override fun readRomCheatsBackup(uri: Uri, romInfo: RomInfo, defaultFolderName: String): Single<RomCheatsBackup> {
        return Single.fromCallable {
            val bytes = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.readBytes()
            } ?: throw IllegalStateException("Could not open input stream")
            val content = CheatFileTextDecoder.decode(bytes)

            val parsedCheats = ChtFileCodec.parse(content)
            if (parsedCheats.isEmpty()) {
                throw IllegalStateException("No cheats found in file")
            }

            ChtFileCodec.toRomCheatsBackup(
                cheats = parsedCheats,
                gameCode = romInfo.gameCode,
                gameChecksum = romInfo.headerChecksumString(),
                gameName = romInfo.gameTitle,
                folderName = defaultFolderName,
            )
        }.subscribeOn(Schedulers.io())
    }

    override fun deleteAllCheats() {
        database.gameDao().deleteAll()
    }

    override fun importCheats(uri: Uri) {
        val workRequest = OneTimeWorkRequestBuilder<CheatImportWorker>()
            .setInputData(workDataOf(CheatImportWorker.KEY_URI to uri.toString()))
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(IMPORT_WORKER_NAME, ExistingWorkPolicy.KEEP, workRequest)
    }

    override fun isCheatImportOngoing(): Boolean {
        val workManager = WorkManager.getInstance(context)
        val statuses = workManager.getWorkInfosForUniqueWork(IMPORT_WORKER_NAME)
        val infos = statuses.get()

        return infos.any { !it.state.isFinished }
    }

    override fun getCheatImportProgress(): Observable<CheatImportProgress> {
        return Observable.create { emitter ->
            val workManager = WorkManager.getInstance(context)
            val statuses = workManager.getWorkInfosForUniqueWork(IMPORT_WORKER_NAME)
            val infos = statuses.get()

            if (infos.all { it.state.isFinished }) {
                emitter.onNext(CheatImportProgress(CheatImportProgress.CheatImportStatus.NOT_IMPORTING, 0f, null))
                emitter.onComplete()
            } else {
                val observer = androidx.lifecycle.Observer<MutableList<WorkInfo>> {
                    val workInfo = it.firstOrNull()
                    if (workInfo != null) {
                        when (workInfo.state) {
                            WorkInfo.State.ENQUEUED -> CheatImportProgress(CheatImportProgress.CheatImportStatus.STARTING, 0f, null)
                            WorkInfo.State.RUNNING -> {
                                val relativeProgress = workInfo.progress.getFloat(CheatImportWorker.KEY_PROGRESS_RELATIVE, 0f)
                                val itemName = workInfo.progress.getString(CheatImportWorker.KEY_PROGRESS_ITEM)
                                CheatImportProgress(CheatImportProgress.CheatImportStatus.ONGOING, relativeProgress, itemName)
                            }
                            WorkInfo.State.SUCCEEDED -> CheatImportProgress(CheatImportProgress.CheatImportStatus.FINISHED, 1f, null)
                            WorkInfo.State.CANCELLED,
                            WorkInfo.State.FAILED -> CheatImportProgress(CheatImportProgress.CheatImportStatus.FAILED, 0f, null)
                            else -> null
                        }?.let { progress ->
                            emitter.onNext(progress)
                            if (progress.status == CheatImportProgress.CheatImportStatus.FAILED || progress.status == CheatImportProgress.CheatImportStatus.FINISHED) {
                                emitter.onComplete()
                            }
                        }
                    }
                }

                val workInfosLiveData = workManager.getWorkInfosForUniqueWorkLiveData(IMPORT_WORKER_NAME)
                workInfosLiveData.observeForever(observer)

                emitter.setCancellable {
                    workInfosLiveData.removeObserver(observer)
                }
            }
        }
    }

    private fun mapGames(gamesMaybe: Maybe<List<me.magnum.melonds.database.entities.GameWithCheatCategories>>): Maybe<List<Game>> {
        return gamesMaybe.map { games ->
            games.map { game ->
                Game(
                    game.game.id,
                    game.game.name,
                    game.game.gameCode,
                    game.game.gameChecksum,
                    game.cheatFolders.map { category ->
                        CheatFolder(
                            category.cheatFolder.id,
                            category.cheatFolder.name,
                            category.cheats.map { cheat ->
                                cheat.toDomainCheat()
                            }
                        )
                    }
                )
            }
        }.defaultIfEmpty(emptyList()).subscribeOn(Schedulers.io())
    }

    private fun ensureGameId(game: Game): Long {
        return ensureGameEntity(game).id!!
    }

    private fun ensureGameEntity(game: Game): GameEntity {
        if (game.id != null) {
            return GameEntity(game.id, game.name, game.gameCode, game.gameChecksum)
        }

        database.gameDao().insertGame(
            GameEntity(null, game.name, game.gameCode, game.gameChecksum)
        )
        return database.gameDao().findGame(game.gameCode, game.gameChecksum!!)
            ?: throw IllegalStateException("Failed to create game entry for cheats")
    }

    private fun GameEntity.toGame(): Game {
        return Game(id, name, gameCode, gameChecksum, emptyList())
    }

    private fun CheatEntity.toDomainCheat(): Cheat {
        return Cheat(id, name, description, code, enabled)
    }
}
