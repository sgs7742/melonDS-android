package me.magnum.melonds.ui.layouts

sealed class LayoutFileOperationResult {
    data class ExportSuccess(val fileName: String) : LayoutFileOperationResult()
    data class ImportSuccess(val layoutName: String) : LayoutFileOperationResult()
    data class Error(val messageResId: Int) : LayoutFileOperationResult()
}
