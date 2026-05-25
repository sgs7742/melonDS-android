package me.magnum.melonds.ui.cheats

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import me.magnum.melonds.R
import me.magnum.melonds.domain.model.Cheat
import me.magnum.melonds.domain.model.CheatFolder
import me.magnum.melonds.domain.model.Game
import me.magnum.melonds.domain.model.RomCheatsBackup
import me.magnum.melonds.domain.model.RomInfo
import me.magnum.melonds.domain.repositories.CheatsRepository
import me.magnum.melonds.extensions.addTo
import me.magnum.melonds.ui.cheats.model.CheatSubmissionForm
import me.magnum.melonds.utils.SingleLiveEvent
import javax.inject.Inject

@HiltViewModel
class CheatsViewModel @Inject constructor(private val cheatsRepository: CheatsRepository) : ViewModel() {
    private var romInfo: RomInfo? = null
    private var selectedGame = MutableLiveData<Game>()
    private var selectedFolder = MutableLiveData<CheatFolder?>()
    private var allRomCheatsLiveData: MutableLiveData<List<Game>>? = null
    private val committingCheatsChangesStatusLiveData = MutableLiveData<Boolean>(false)
    private val cheatChangesCommittedLiveEvent = SingleLiveEvent<Unit>()
    private val refreshCompleteLiveEvent = SingleLiveEvent<Unit>()
    private val operationResultLiveEvent = SingleLiveEvent<OperationResult>()
    private val pendingRestoreBackupLiveEvent = SingleLiveEvent<RomCheatsBackup>()
    private val modifiedCheatSet = mutableListOf<Cheat>()

    private val disposables = CompositeDisposable()

    sealed class OperationResult {
        data class Success(val messageRes: Int? = null, val messageArg: Int? = null) : OperationResult()
        data class Error(val messageRes: Int) : OperationResult()
    }

    fun setRomInfo(romInfo: RomInfo) {
        this.romInfo = romInfo
    }

    fun getRomInfo(): RomInfo? = romInfo

    fun getRomCheats(romInfo: RomInfo): LiveData<List<Game>> {
        if (allRomCheatsLiveData == null) {
            allRomCheatsLiveData = MutableLiveData()
            loadRomCheats(romInfo)
        }
        return allRomCheatsLiveData!!
    }

    fun refreshRomCheats() {
        val rom = romInfo ?: return
        loadRomCheats(rom)
    }

    private fun loadRomCheats(romInfo: RomInfo) {
        cheatsRepository.getAllRomCheats(romInfo)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ games ->
                allRomCheatsLiveData!!.postValue(games)
                syncSelectedGame(games)
                refreshCompleteLiveEvent.postValue(Unit)
            }, {
                allRomCheatsLiveData!!.postValue(emptyList())
                refreshCompleteLiveEvent.postValue(Unit)
            })
            .addTo(disposables)
    }

    private fun syncSelectedGame(games: List<Game>) {
        val currentSelected = selectedGame.value
        if (currentSelected?.id != null) {
            games.firstOrNull { it.id == currentSelected.id }?.let { selectedGame.postValue(it) }
        } else if (games.size == 1) {
            selectedGame.postValue(games.first())
        } else if (games.isEmpty() && romInfo != null) {
            selectedGame.postValue(buildPlaceholderGame(romInfo!!))
        }

        val currentFolder = selectedFolder.value
        if (currentFolder?.id != null) {
            games.forEach { game ->
                game.cheats.firstOrNull { it.id == currentFolder.id }?.let { selectedFolder.postValue(it) }
            }
        }
    }

    fun getGames(): List<Game> {
        return allRomCheatsLiveData?.value ?: emptyList()
    }

    fun getPlaceholderOrSelectedGame(): Game? {
        return selectedGame.value ?: romInfo?.let { buildPlaceholderGame(it) }
    }

    private fun buildPlaceholderGame(romInfo: RomInfo): Game {
        return Game(
            id = null,
            name = romInfo.gameTitle,
            gameCode = romInfo.gameCode,
            gameChecksum = romInfo.headerChecksumString(),
            cheats = emptyList(),
        )
    }

    fun getSelectedGame(): LiveData<Game> {
        return selectedGame
    }

    fun setSelectedGame(game: Game) {
        selectedGame.value = game
    }

    fun getSelectedFolder(): LiveData<CheatFolder?> {
        return selectedFolder
    }

    fun setSelectedFolder(folder: CheatFolder?) {
        selectedFolder.value = folder
    }

    fun clearSelectedFolder() {
        selectedFolder.value = null
    }

    fun getSelectedFolderCheats(): List<Cheat> {
        val cheats = selectedFolder.value?.cheats?.toMutableList() ?: mutableListOf()
        if (cheats.isEmpty() || modifiedCheatSet.isEmpty()) {
            return cheats
        }

        modifiedCheatSet.forEach { cheat ->
            val originalCheatIndex = cheats.indexOfFirst { it.id == cheat.id }
            if (originalCheatIndex >= 0) {
                cheats[originalCheatIndex] = cheat
            }
        }
        return cheats
    }

    fun isCheatsSubScreenActive(): Boolean = selectedFolder.value != null

    fun notifyCheatEnabledStatusChanged(cheat: Cheat, isEnabled: Boolean) {
        if (isEnabled == cheat.enabled) {
            modifiedCheatSet.removeAll { it.id == cheat.id }
        } else {
            modifiedCheatSet.removeAll { it.id == cheat.id }
            modifiedCheatSet.add(cheat.copy(enabled = isEnabled))
        }
    }

    fun addCheatFolder(folderName: String, onComplete: () -> Unit = {}) {
        val game = getPlaceholderOrSelectedGame() ?: return
        if (folderName.isBlank()) return

        cheatsRepository.addCheatFolder(folderName, game)
            .andThen(Completable.fromAction { refreshRomCheats() })
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                onComplete()
            }, {
                operationResultLiveEvent.postValue(OperationResult.Error(R.string.failed_add_cheat_folder))
            })
            .addTo(disposables)
    }

    fun addCustomCheat(cheatForm: CheatSubmissionForm, onComplete: () -> Unit = {}) {
        val folder = selectedFolder.value ?: return
        if (!cheatForm.isValid()) return

        cheatsRepository.addCustomCheat(folder, cheatForm)
            .andThen(Completable.fromAction { refreshRomCheats() })
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                onComplete()
            }, {
                operationResultLiveEvent.postValue(OperationResult.Error(R.string.failed_add_cheat))
            })
            .addTo(disposables)
    }

    fun updateCheat(originalCheat: Cheat, cheatForm: CheatSubmissionForm) {
        if (!cheatForm.isValid()) return
        if (originalCheat.name == cheatForm.name &&
            originalCheat.description.orEmpty() == cheatForm.description &&
            originalCheat.code == cheatForm.code
        ) {
            return
        }

        val updatedCheat = originalCheat.copy(
            name = cheatForm.name,
            description = cheatForm.description.takeUnless { it.isBlank() },
            code = cheatForm.code,
        )

        cheatsRepository.updateCheat(updatedCheat)
            .andThen(Completable.fromAction { refreshRomCheats() })
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                modifiedCheatSet.removeAll { it.id == originalCheat.id }
                if (updatedCheat.enabled) {
                    modifiedCheatSet.add(updatedCheat)
                }
            }, {
                operationResultLiveEvent.postValue(OperationResult.Error(R.string.failed_update_cheat))
            })
            .addTo(disposables)
    }

    fun deleteCheat(cheat: Cheat) {
        deleteCheats(listOf(cheat))
    }

    fun deleteCheats(cheats: List<Cheat>) {
        if (cheats.isEmpty()) {
            return
        }
        val deletedIds = cheats.mapNotNull { it.id }.toSet()
        cheatsRepository.deleteCheats(cheats)
            .andThen(Completable.fromAction { refreshRomCheats() })
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                modifiedCheatSet.removeAll { it.id in deletedIds }
                operationResultLiveEvent.postValue(
                    OperationResult.Success(R.string.cheats_deleted_count, cheats.size)
                )
            }, {
                operationResultLiveEvent.postValue(OperationResult.Error(R.string.failed_delete_cheats))
            })
            .addTo(disposables)
    }

    fun buildRomCheatsBackup(): Single<RomCheatsBackup> {
        val rom = romInfo ?: return Single.error(IllegalStateException("ROM info not set"))
        return cheatsRepository.buildRomCheatsBackup(rom)
    }

    fun restoreRomCheats(backup: RomCheatsBackup, onComplete: () -> Unit = {}) {
        val rom = romInfo ?: return
        val cheatCount = backup.folders.sumOf { it.cheats.size }
        if (cheatCount == 0) {
            operationResultLiveEvent.postValue(OperationResult.Error(R.string.failed_read_cheats_backup))
            return
        }

        modifiedCheatSet.clear()
        val targetFolderId = selectedFolder.value?.id
        cheatsRepository.restoreRomCheats(rom, backup, targetFolderId)
            .andThen(Completable.fromAction { refreshRomCheats() })
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                operationResultLiveEvent.postValue(
                    OperationResult.Success(R.string.cheats_restored_count, cheatCount)
                )
                onComplete()
            }, {
                operationResultLiveEvent.postValue(OperationResult.Error(R.string.failed_restore_cheats))
            })
            .addTo(disposables)
    }

    fun writeRomCheatsBackup(uri: android.net.Uri, backup: RomCheatsBackup) {
        cheatsRepository.writeRomCheatsBackup(uri, backup)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                operationResultLiveEvent.postValue(OperationResult.Success(R.string.cheats_backed_up))
            }, {
                operationResultLiveEvent.postValue(OperationResult.Error(R.string.failed_backup_cheats))
            })
            .addTo(disposables)
    }

    fun readRomCheatsBackup(uri: android.net.Uri, defaultFolderName: String) {
        val rom = romInfo ?: return
        cheatsRepository.readRomCheatsBackup(uri, rom, defaultFolderName)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ backup ->
                pendingRestoreBackupLiveEvent.value = backup
            }, {
                operationResultLiveEvent.postValue(OperationResult.Error(R.string.failed_read_cheats_backup))
            })
            .addTo(disposables)
    }

    fun onPendingRestoreBackup(): LiveData<RomCheatsBackup> = pendingRestoreBackupLiveEvent

    fun onRefreshComplete(): LiveData<Unit> = refreshCompleteLiveEvent

    fun onOperationResult(): LiveData<OperationResult> = operationResultLiveEvent

    fun committingCheatsChangesStatus(): LiveData<Boolean> {
        return committingCheatsChangesStatusLiveData
    }

    fun onCheatChangesCommitted(): LiveData<Unit> {
        return cheatChangesCommittedLiveEvent
    }

    fun commitCheatChanges(): LiveData<Boolean> {
        val liveData = MutableLiveData<Boolean>()

        if (modifiedCheatSet.isEmpty()) {
            cheatChangesCommittedLiveEvent.postValue(Unit)
            return liveData
        }

        committingCheatsChangesStatusLiveData.value = true
        cheatsRepository.updateCheatsStatus(modifiedCheatSet).doAfterTerminate {
            committingCheatsChangesStatusLiveData.postValue(false)
            cheatChangesCommittedLiveEvent.postValue(Unit)
        }.observeOn(AndroidSchedulers.mainThread())
            .subscribe({
            liveData.postValue(true)
        }, {
            liveData.postValue(false)
        }).addTo(disposables)

        return liveData
    }

    override fun onCleared() {
        super.onCleared()
        disposables.dispose()
    }
}
