package me.magnum.melonds.ui.cheats

import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import me.magnum.melonds.R
import me.magnum.melonds.databinding.ItemCheatsCheatBinding
import me.magnum.melonds.domain.model.Cheat
import me.magnum.melonds.extensions.setViewEnabledRecursive

class CheatsSubScreenFragment : SubScreenFragment() {
    private var cheatsAdapter: CheatsAdapter? = null
    private var selectionMode = false
    private val selectedCheatIds = mutableSetOf<Long>()
    private var actionMode: ActionMode? = null

    private val selectionBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            endSelectionMode()
        }
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, selectionBackCallback)

        viewModel.getSelectedFolder().observe(viewLifecycleOwner) {
            refreshCheatsList()
        }
    }

    fun isSelectionModeActive(): Boolean = selectionMode

    fun endSelectionModeForBack() {
        endSelectionMode()
    }

    fun startSelectionMode(initialCheat: Cheat? = null) {
        if (selectionMode) {
            initialCheat?.id?.let { toggleSelection(it) }
            return
        }

        selectionMode = true
        selectedCheatIds.clear()
        initialCheat?.id?.let { selectedCheatIds.add(it) }
        cheatsAdapter?.setSelectionMode(true, selectedCheatIds)
        selectionBackCallback.isEnabled = true
        requireActivity().invalidateOptionsMenu()

        actionMode = requireActivity().startActionMode(object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                mode.menuInflater.inflate(R.menu.cheats_selection_menu, menu)
                updateActionModeTitle(mode)
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                updateActionModeTitle(mode)
                return true
            }

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                return when (item.itemId) {
                    R.id.action_cheats_select_all -> {
                        selectAllCheats()
                        true
                    }
                    R.id.action_cheats_delete_selected -> {
                        showDeleteSelectedConfirmation()
                        true
                    }
                    else -> false
                }
            }

            override fun onDestroyActionMode(mode: ActionMode) {
                clearSelectionModeState()
            }
        })
    }

    private fun endSelectionMode() {
        actionMode?.finish()
    }

    private fun clearSelectionModeState() {
        selectionMode = false
        selectedCheatIds.clear()
        actionMode = null
        cheatsAdapter?.setSelectionMode(false, selectedCheatIds)
        selectionBackCallback.isEnabled = false
        requireActivity().invalidateOptionsMenu()
    }

    private fun toggleSelection(cheatId: Long) {
        if (!selectionMode) {
            return
        }

        if (cheatId in selectedCheatIds) {
            selectedCheatIds.remove(cheatId)
        } else {
            selectedCheatIds.add(cheatId)
        }

        cheatsAdapter?.setSelectionMode(true, selectedCheatIds)
        actionMode?.let { updateActionModeTitle(it) }
    }

    private fun selectAllCheats() {
        selectedCheatIds.clear()
        cheatsAdapter?.getCheats()?.mapNotNull { it.id }?.let { selectedCheatIds.addAll(it) }
        cheatsAdapter?.setSelectionMode(true, selectedCheatIds)
        actionMode?.let { updateActionModeTitle(it) }
    }

    private fun updateActionModeTitle(mode: ActionMode) {
        mode.title = getString(R.string.cheats_selected_count, selectedCheatIds.size)
    }

    private fun showDeleteSelectedConfirmation() {
        val selectedCheats = getSelectedCheats()
        if (selectedCheats.isEmpty()) {
            return
        }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_cheat)
            .setMessage(getString(R.string.delete_cheats_confirmation, selectedCheats.size))
            .setPositiveButton(R.string.delete_cheat) { _, _ ->
                viewModel.deleteCheats(selectedCheats)
                actionMode?.finish()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun getSelectedCheats(): List<Cheat> {
        return cheatsAdapter?.getCheats()?.filter { it.id in selectedCheatIds } ?: emptyList()
    }

    override fun refreshAdapter() {
        refreshCheatsList()
    }

    private fun refreshCheatsList() {
        val cheats = viewModel.getSelectedFolderCheats()
        val adapter = cheatsAdapter
        if (adapter == null) {
            super.refreshAdapter()
        } else {
            adapter.setCheats(cheats)
            adapter.notifyDataSetChanged()
            if (selectionMode) {
                selectedCheatIds.retainAll(cheats.mapNotNull { it.id }.toSet())
                adapter.setSelectionMode(true, selectedCheatIds)
                actionMode?.let { updateActionModeTitle(it) }
            }
        }
    }

    override fun getSubScreenAdapter(): RecyclerView.Adapter<*> {
        return cheatsAdapter ?: CheatsAdapter(
            cheats = viewModel.getSelectedFolderCheats(),
            onCheatEnableToggled = { cheat, isEnabled ->
                viewModel.notifyCheatEnabledStatusChanged(cheat, isEnabled)
            },
            onEditCheat = { showEditCheatDialog(it) },
            onDeleteCheat = { showDeleteCheatConfirmation(it) },
            onItemClick = { cheat -> handleItemClick(cheat) },
            onItemLongClick = { cheat -> startSelectionMode(cheat) },
        ).also { cheatsAdapter = it }
    }

    private fun handleItemClick(cheat: Cheat) {
        if (selectionMode) {
            cheat.id?.let { toggleSelection(it) }
        }
    }

    private fun showEditCheatDialog(cheat: Cheat) {
        AddCheatDialog.editCheat(cheat).apply {
            setOnSaveListener { form ->
                viewModel.updateCheat(cheat, form)
            }
        }.show(parentFragmentManager, "edit_cheat")
    }

    private fun showDeleteCheatConfirmation(cheat: Cheat) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_cheat)
            .setMessage(getString(R.string.delete_cheat_confirmation, cheat.name))
            .setPositiveButton(R.string.delete_cheat) { _, _ ->
                viewModel.deleteCheat(cheat)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private class CheatsAdapter(
        private var cheats: List<Cheat>,
        private val onCheatEnableToggled: (Cheat, Boolean) -> Unit,
        private val onEditCheat: (Cheat) -> Unit,
        private val onDeleteCheat: (Cheat) -> Unit,
        private val onItemClick: (Cheat) -> Unit,
        private val onItemLongClick: (Cheat) -> Unit,
    ) : RecyclerView.Adapter<CheatsAdapter.ViewHolder>() {
        private var selectionMode = false
        private var selectedIds: Set<Long> = emptySet()

        fun getCheats(): List<Cheat> = cheats

        fun setCheats(cheats: List<Cheat>) {
            this.cheats = cheats
        }

        fun setSelectionMode(enabled: Boolean, selectedIds: Set<Long>) {
            selectionMode = enabled
            this.selectedIds = selectedIds
            notifyDataSetChanged()
        }

        class ViewHolder(
            private val binding: ItemCheatsCheatBinding,
            private val onCheatEnableToggled: (Cheat, Boolean) -> Unit,
            private val onEditCheat: (Cheat) -> Unit,
            private val onDeleteCheat: (Cheat) -> Unit,
        ) : RecyclerView.ViewHolder(binding.root) {
            private lateinit var cheat: Cheat

            fun bind(
                cheat: Cheat,
                selectionMode: Boolean,
                isSelected: Boolean,
            ) {
                this.cheat = cheat

                val isCheatValid = cheat.isValid()
                binding.root.setViewEnabledRecursive(isCheatValid)
                binding.textCheatName.text = cheat.name
                binding.textCheatDescription.isGone = cheat.description.isNullOrEmpty()
                binding.textCheatDescription.text = cheat.description

                binding.checkboxCheatSelected.isVisible = selectionMode
                binding.checkboxCheatSelected.isChecked = isSelected

                binding.checkboxCheatEnabled.isVisible = !selectionMode
                binding.buttonCheatMore.isVisible = !selectionMode

                binding.checkboxCheatEnabled.setOnCheckedChangeListener(null)
                binding.checkboxCheatEnabled.isChecked = isCheatValid && cheat.enabled
                binding.checkboxCheatEnabled.setOnCheckedChangeListener { _, isEnabled ->
                    onCheatEnableToggled.invoke(cheat, isEnabled)
                }

                binding.buttonCheatMore.setOnClickListener { anchor ->
                    val popup = android.widget.PopupMenu(anchor.context, anchor)
                    popup.menuInflater.inflate(R.menu.cheat_item_menu, popup.menu)
                    popup.setOnMenuItemClickListener { item ->
                        when (item.itemId) {
                            R.id.action_cheat_edit -> {
                                onEditCheat(cheat)
                                true
                            }
                            R.id.action_cheat_delete -> {
                                onDeleteCheat(cheat)
                                true
                            }
                            else -> false
                        }
                    }
                    popup.show()
                }
            }

            fun toggleEnabled() {
                binding.checkboxCheatEnabled.toggle()
            }
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemCheatsCheatBinding.inflate(
                android.view.LayoutInflater.from(parent.context),
                parent,
                false,
            )
            return ViewHolder(binding, onCheatEnableToggled, onEditCheat, onDeleteCheat)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val cheat = cheats[position]
            holder.bind(
                cheat = cheat,
                selectionMode = selectionMode,
                isSelected = cheat.id != null && cheat.id in selectedIds,
            )
            holder.itemView.setOnClickListener {
                if (selectionMode) {
                    onItemClick(cheat)
                } else {
                    holder.toggleEnabled()
                }
            }
            holder.itemView.setOnLongClickListener {
                onItemLongClick(cheat)
                true
            }
        }

        override fun getItemCount(): Int = cheats.size
    }
}
