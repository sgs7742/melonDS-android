package me.magnum.melonds.ui.cheats

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isGone
import androidx.fragment.app.commit
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import me.magnum.melonds.R
import me.magnum.melonds.databinding.ActivityCheatsBinding
import me.magnum.melonds.databinding.DialogTextInputBinding
import me.magnum.melonds.extensions.addTo
import me.magnum.melonds.domain.model.RomCheatsBackup
import me.magnum.melonds.parcelables.RomInfoParcelable

@AndroidEntryPoint
class CheatsActivity : AppCompatActivity() {
    companion object {
        const val KEY_ROM_INFO = "key_rom_info"
        private const val CHEATS_BACKUP_MIME = "text/plain"
    }

    private val viewModel: CheatsViewModel by viewModels()
    private lateinit var binding: ActivityCheatsBinding
    private lateinit var romInfoParcelable: RomInfoParcelable
    private var cheatsFragment: CheatsFragment? = null
    private val disposables = CompositeDisposable()

    private val backupFilePicker = registerForActivityResult(ActivityResultContracts.CreateDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        viewModel.buildRomCheatsBackup()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ backup ->
                viewModel.writeRomCheatsBackup(uri, backup)
            }, {
                Toast.makeText(this, R.string.failed_backup_cheats, Toast.LENGTH_LONG).show()
            }).addTo(disposables)
    }

    private val restoreFilePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        val defaultFolderName = viewModel.getSelectedFolder().value?.name
            ?: getString(R.string.cheat_folder_default_name)
        viewModel.readRomCheatsBackup(uri, defaultFolderName)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCheatsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        romInfoParcelable = intent.getParcelableExtra(KEY_ROM_INFO)
            ?: throw NullPointerException("KEY_ROM_INFO argument is required")
        val romInfo = romInfoParcelable.toRomInfo()
        viewModel.setRomInfo(romInfo)

        viewModel.getRomCheats(romInfo).observe(this) {
            binding.progressBarCheats.isGone = true
            ensureCheatsFragmentAdded()
        }

        viewModel.onRefreshComplete().observe(this) {
            cheatsFragment?.takeIf { it.isAdded }?.refreshLists()
        }

        viewModel.onOperationResult().observe(this) { result ->
            when (result) {
                is CheatsViewModel.OperationResult.Success -> {
                    result.messageRes?.let { resId ->
                        val text = if (result.messageArg != null) {
                            getString(resId, result.messageArg)
                        } else {
                            getString(resId)
                        }
                        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
                    }
                }
                is CheatsViewModel.OperationResult.Error -> {
                    Toast.makeText(this, result.messageRes, Toast.LENGTH_LONG).show()
                }
            }
        }

        viewModel.committingCheatsChangesStatus().observe(this) {
            binding.viewBlock.isGone = !it
        }

        viewModel.onCheatChangesCommitted().observe(this) {
            finish()
        }

        viewModel.onPendingRestoreBackup().observe(this) { backup ->
            showRestoreConfirmationDialog(backup)
        }
    }

    private fun showRestoreConfirmationDialog(backup: RomCheatsBackup) {
        if (isFinishing || isDestroyed) {
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.restore_cheats)
            .setMessage(R.string.restore_cheats_confirmation)
            .setPositiveButton(R.string.restore) { _, _ ->
                viewModel.restoreRomCheats(backup) {
                    ensureCheatsFragmentAdded()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroy() {
        super.onDestroy()
        disposables.dispose()
    }

    private fun getDefaultChtExportFileName(): String {
        val romFileName = romInfoParcelable.romFileName
        if (!romFileName.isNullOrBlank()) {
            val baseName = romFileName.substringBeforeLast('.').ifBlank { romFileName }
            val sanitized = baseName.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
            if (sanitized.isNotEmpty()) {
                return "$sanitized.cht"
            }
        }
        return "melonds-cheats-${romInfoParcelable.gameCode}-${romInfoParcelable.headerChecksum.toString(16)}.cht"
    }

    private fun ensureCheatsFragmentAdded() {
        binding.textCheatsNotFound.isGone = true

        if (cheatsFragment != null) {
            return
        }

        cheatsFragment = CheatsFragment().also { fragment ->
            fragment.setOnContentTitleChangedListener { title ->
                supportActionBar?.title = title ?: getString(R.string.cheats)
            }
            supportFragmentManager.commit {
                replace(binding.cheatsRoot.id, fragment)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.cheats_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val prepared = super.onPrepareOptionsMenu(menu)
        val selectItem = menu.findItem(R.id.action_cheats_select)
        val subScreen = cheatsFragment?.getCheatsSubScreenFragment()
        selectItem?.isVisible = viewModel.isCheatsSubScreenActive() && subScreen?.isSelectionModeActive() != true
        return prepared
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                if (!popChildBackStackIfNeeded()) {
                    commitCheatChangesAndFinish()
                }
                true
            }
            R.id.action_cheats_add -> {
                handleAddAction()
                true
            }
            R.id.action_cheats_backup -> {
                backupFilePicker.launch(getDefaultChtExportFileName())
                true
            }
            R.id.action_cheats_restore -> {
                restoreFilePicker.launch(arrayOf("*/*"))
                true
            }
            R.id.action_cheats_select -> {
                cheatsFragment?.getCheatsSubScreenFragment()?.startSelectionMode()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun handleAddAction() {
        if (viewModel.isCheatsSubScreenActive()) {
            showAddCheatDialog()
            return
        }

        val dialogBinding = DialogTextInputBinding.inflate(layoutInflater)
        dialogBinding.editText.setText(getString(R.string.cheat_folder_default_name))
        AlertDialog.Builder(this)
            .setTitle(R.string.add_cheat_folder)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.ok) { _, _ ->
                val folderName = dialogBinding.editText.text.toString().trim()
                if (folderName.isNotEmpty()) {
                    viewModel.addCheatFolder(folderName) {
                        cheatsFragment?.openFoldersScreen()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showAddCheatDialog() {
        AddCheatDialog.newCheat().apply {
            setOnSaveListener { form ->
                viewModel.addCustomCheat(form)
            }
        }.show(supportFragmentManager, "add_cheat")
    }

    override fun onBackPressed() {
        if (cheatsFragment?.getCheatsSubScreenFragment()?.isSelectionModeActive() == true) {
            cheatsFragment?.getCheatsSubScreenFragment()?.endSelectionModeForBack()
            return
        }
        if (!popChildBackStackIfNeeded()) {
            commitCheatChangesAndFinish()
        }
    }

    private fun popChildBackStackIfNeeded(): Boolean {
        supportFragmentManager.fragments.forEach { fragment ->
            if (fragment.isVisible) {
                if (fragment.childFragmentManager.backStackEntryCount > 1) {
                    fragment.childFragmentManager.popBackStack()
                    return true
                }
            }
        }
        return false
    }

    private fun commitCheatChangesAndFinish() {
        if (viewModel.committingCheatsChangesStatus().value == true) {
            return
        }

        viewModel.commitCheatChanges().observe(this) {
            if (!it) {
                Toast.makeText(this, R.string.failed_save_cheat_changes, Toast.LENGTH_LONG).show()
            }
        }
    }
}
