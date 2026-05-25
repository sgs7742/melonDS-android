package me.magnum.melonds.ui.cheats

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.DialogFragment
import me.magnum.melonds.R
import me.magnum.melonds.databinding.DialogAddCheatBinding
import me.magnum.melonds.domain.model.Cheat
import me.magnum.melonds.ui.cheats.model.CheatSubmissionForm

class AddCheatDialog : DialogFragment() {
    companion object {
        private const val KEY_NAME = "name"
        private const val KEY_DESCRIPTION = "description"
        private const val KEY_CODE = "code"

        fun newCheat(): AddCheatDialog {
            return AddCheatDialog()
        }

        fun editCheat(cheat: Cheat): AddCheatDialog {
            return AddCheatDialog().apply {
                arguments = bundleOf(
                    KEY_NAME to cheat.name,
                    KEY_DESCRIPTION to (cheat.description ?: ""),
                    KEY_CODE to cheat.code,
                )
            }
        }
    }

    private var onSaveListener: ((CheatSubmissionForm) -> Unit)? = null

    fun setOnSaveListener(listener: (CheatSubmissionForm) -> Unit) {
        onSaveListener = listener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogAddCheatBinding.inflate(layoutInflater)
        binding.editCheatName.setText(arguments?.getString(KEY_NAME))
        binding.editCheatDescription.setText(arguments?.getString(KEY_DESCRIPTION))
        binding.editCheatCode.setText(arguments?.getString(KEY_CODE))

        val isEdit = arguments?.containsKey(KEY_NAME) == true
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(if (isEdit) R.string.edit_cheat else R.string.new_cheat)
            .setView(binding.root)
            .setPositiveButton(R.string.save, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        binding.editCheatCode.doOnTextChanged { text, _, _, _ ->
            val formatted = CheatCodeUtils.formatCheatCode(text?.toString().orEmpty())
            if (formatted != text?.toString()) {
                binding.editCheatCode.setText(formatted)
                binding.editCheatCode.setSelection(formatted.length)
            }
            binding.layoutCheatCode.error = null
        }

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = binding.editCheatName.text?.toString()?.trim().orEmpty()
                val description = binding.editCheatDescription.text?.toString()?.trim().orEmpty()
                val code = CheatCodeUtils.normalizeCheatCode(binding.editCheatCode.text?.toString().orEmpty())

                var valid = true
                if (name.isBlank()) {
                    binding.editCheatName.error = getString(R.string.error_name_cannot_be_empty)
                    valid = false
                }
                if (code.isBlank()) {
                    binding.layoutCheatCode.error = getString(R.string.error_code_cannot_be_empty)
                    valid = false
                } else if (!CheatCodeUtils.isCheatCodeValid(code)) {
                    binding.layoutCheatCode.error = getString(R.string.error_code_invalid_format)
                    valid = false
                }
                if (!valid) {
                    return@setOnClickListener
                }

                onSaveListener?.invoke(CheatSubmissionForm(name, description, code))
                dismiss()
            }
        }

        return dialog
    }
}
