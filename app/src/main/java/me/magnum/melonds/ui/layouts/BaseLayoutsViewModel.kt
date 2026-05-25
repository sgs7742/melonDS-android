package me.magnum.melonds.ui.layouts

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import io.reactivex.disposables.CompositeDisposable
import me.magnum.melonds.R
import me.magnum.melonds.common.Schedulers
import me.magnum.melonds.domain.model.LayoutConfiguration
import me.magnum.melonds.domain.repositories.LayoutsRepository
import me.magnum.melonds.extensions.addTo
import me.magnum.melonds.impl.LayoutFileOperations
import me.magnum.melonds.utils.SingleLiveEvent
import java.util.*

abstract class BaseLayoutsViewModel(
        protected val layoutsRepository: LayoutsRepository,
        private val layoutFileOperations: LayoutFileOperations,
        private val schedulers: Schedulers,
) : ViewModel() {
    protected val disposables = CompositeDisposable()
    protected val layoutsLiveData = MutableLiveData<List<LayoutConfiguration>>()
    private val layoutFileOperationResult = SingleLiveEvent<LayoutFileOperationResult>()
    private val importableLayoutFiles = SingleLiveEvent<List<LayoutFileOperations.LayoutFileEntry>>()

    fun getLayouts(): LiveData<List<LayoutConfiguration>> {
        return layoutsLiveData
    }

    fun addLayout(layout: LayoutConfiguration) {
        layoutsRepository.saveLayout(layout)
    }

    fun deleteLayout(layout: LayoutConfiguration) {
        layoutsRepository.deleteLayout(layout)
                .subscribe()
                .addTo(disposables)
    }

    fun getLayoutFileOperationResult(): LiveData<LayoutFileOperationResult> = layoutFileOperationResult

    fun getImportableLayoutFiles(): LiveData<List<LayoutFileOperations.LayoutFileEntry>> = importableLayoutFiles

    fun exportLayout(layout: LayoutConfiguration) {
        layoutFileOperations.exportLayout(layout)
                .subscribeOn(schedulers.backgroundThreadScheduler)
                .observeOn(schedulers.uiThreadScheduler)
                .subscribe({ fileName ->
                    layoutFileOperationResult.value = LayoutFileOperationResult.ExportSuccess(fileName)
                }, { error ->
                    layoutFileOperationResult.value = mapLayoutFileError(error)
                })
                .addTo(disposables)
    }

    fun loadImportableLayoutFiles() {
        layoutFileOperations.listExportedLayoutFiles()
                .subscribeOn(schedulers.backgroundThreadScheduler)
                .observeOn(schedulers.uiThreadScheduler)
                .subscribe({ files ->
                    if (files.isEmpty()) {
                        layoutFileOperationResult.value = LayoutFileOperationResult.Error(R.string.layout_import_no_files)
                    } else {
                        importableLayoutFiles.value = files
                    }
                }, { error ->
                    layoutFileOperationResult.value = mapLayoutFileError(error)
                })
                .addTo(disposables)
    }

    fun importLayout(fileUri: Uri) {
        layoutFileOperations.importLayout(fileUri)
                .subscribeOn(schedulers.backgroundThreadScheduler)
                .observeOn(schedulers.uiThreadScheduler)
                .subscribe({ layout ->
                    layoutsRepository.saveLayout(layout)
                    layoutFileOperationResult.value = LayoutFileOperationResult.ImportSuccess(layout.name ?: "")
                }, { error ->
                    layoutFileOperationResult.value = mapLayoutFileError(error)
                })
                .addTo(disposables)
    }

    private fun mapLayoutFileError(error: Throwable): LayoutFileOperationResult.Error {
        return when (error) {
            is LayoutFileOperations.NoStorageDirectoryException ->
                LayoutFileOperationResult.Error(R.string.layout_file_no_directory)
            is LayoutFileOperations.LayoutFileReadException ->
                LayoutFileOperationResult.Error(R.string.layout_import_failed)
            is LayoutFileOperations.LayoutFileWriteException ->
                LayoutFileOperationResult.Error(R.string.layout_export_failed)
            else -> LayoutFileOperationResult.Error(R.string.layout_file_operation_failed)
        }
    }

    abstract fun getSelectedLayoutId(): UUID?
    abstract fun setSelectedLayoutId(id: UUID?)

    override fun onCleared() {
        super.onCleared()
        disposables.dispose()
    }
}