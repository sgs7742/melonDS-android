package me.magnum.melonds.ui.cheats

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import me.magnum.melonds.databinding.ItemCheatsFolderBinding
import me.magnum.melonds.domain.model.CheatFolder

class FoldersSubScreenFragment : SubScreenFragment() {
    override fun onViewCreated(view: android.view.View, savedInstanceState: android.os.Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.getSelectedGame().observe(viewLifecycleOwner) {
            refreshAdapter()
        }
    }

    override fun getSubScreenAdapter(): RecyclerView.Adapter<*> {
        val folders = viewModel.getPlaceholderOrSelectedGame()?.cheats ?: emptyList()
        return FoldersAdapter(folders) {
            viewModel.setSelectedFolder(it)
        }
    }

    private class FoldersAdapter(val folders: List<CheatFolder>, private val onFolderClicked: (CheatFolder) -> Unit) : RecyclerView.Adapter<FoldersAdapter.ViewHolder>() {
        class ViewHolder(private val binding: ItemCheatsFolderBinding) : RecyclerView.ViewHolder(binding.root) {
            private lateinit var folder: CheatFolder

            fun getFolder(): CheatFolder = folder

            fun setFolder(folder: CheatFolder) {
                this.folder = folder
                binding.textFolderName.text = folder.name
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemCheatsFolderBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding).apply {
                itemView.setOnClickListener {
                    onFolderClicked(getFolder())
                }
            }
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.setFolder(folders[position])
        }

        override fun getItemCount(): Int = folders.size
    }
}
