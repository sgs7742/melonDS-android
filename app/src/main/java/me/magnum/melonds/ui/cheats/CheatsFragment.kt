package me.magnum.melonds.ui.cheats

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import dagger.hilt.android.AndroidEntryPoint
import me.magnum.melonds.R
import me.magnum.melonds.databinding.FragmentCheatsBinding
import me.magnum.melonds.domain.model.Game

@AndroidEntryPoint
class CheatsFragment : Fragment() {
    companion object {
        private const val TAG_BACK_STACK_CHEATS = "back_stack_cheats"
    }

    private val viewModel: CheatsViewModel by activityViewModels()

    private var contentTitleChangeListener: ((String?) -> Unit)? = null
    private var navigationInitialized = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = FragmentCheatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (savedInstanceState != null) {
            navigationInitialized = childFragmentManager.backStackEntryCount > 0
        }

        val romInfo = viewModel.getRomInfo()
        if (romInfo != null) {
            viewModel.getRomCheats(romInfo).observe(viewLifecycleOwner) { games ->
                if (!navigationInitialized) {
                    initializeNavigation(games)
                } else {
                    refreshLists()
                }
            }
        }

        childFragmentManager.addOnBackStackChangedListener {
            updateTitleFromBackStack()
            val topFragment = childFragmentManager.fragments.lastOrNull()
            if (topFragment !is CheatsSubScreenFragment) {
                viewModel.clearSelectedFolder()
            }
        }

        viewModel.getSelectedFolder().observe(viewLifecycleOwner) { folder ->
            if (folder != null) {
                openCheatsFragment()
            }
        }
    }

    fun initializeNavigation(games: List<Game>) {
        if (!isAdded || navigationInitialized) {
            return
        }

        navigationInitialized = true
        val gameList = games.ifEmpty {
            viewModel.getPlaceholderOrSelectedGame()?.let { listOf(it) } ?: emptyList()
        }

        if (gameList.size == 1) {
            viewModel.setSelectedGame(gameList.first())
            childFragmentManager.commit {
                replace<FoldersSubScreenFragment>(R.id.layout_cheats_root)
                addToBackStack(TAG_BACK_STACK_CHEATS)
            }
        } else if (gameList.isNotEmpty()) {
            childFragmentManager.commit {
                replace<GamesSubScreenFragment>(R.id.layout_cheats_root)
                addToBackStack(TAG_BACK_STACK_CHEATS)
            }
            viewModel.getSelectedGame().observe(viewLifecycleOwner) { game ->
                if (game != null) {
                    openGameFoldersFragment()
                }
            }
        } else {
            viewModel.getRomInfo()?.let { romInfo ->
                viewModel.setSelectedGame(
                    Game(
                        null,
                        romInfo.gameTitle,
                        romInfo.gameCode,
                        romInfo.headerChecksumString(),
                        emptyList(),
                    )
                )
            }
            childFragmentManager.commit {
                replace<FoldersSubScreenFragment>(R.id.layout_cheats_root)
                addToBackStack(TAG_BACK_STACK_CHEATS)
            }
        }

        updateTitleFromBackStack()
    }

    fun refreshLists() {
        if (!isAdded) {
            return
        }
        childFragmentManager.fragments.filterIsInstance<SubScreenFragment>().forEach {
            it.refreshAdapter()
        }
    }

    fun openFoldersScreen() {
        openGameFoldersFragment()
    }

    fun getCheatsSubScreenFragment(): CheatsSubScreenFragment? {
        return childFragmentManager.fragments.lastOrNull() as? CheatsSubScreenFragment
    }

    private fun openGameFoldersFragment() {
        openSubScreenFragment<FoldersSubScreenFragment>()
    }

    private fun openCheatsFragment() {
        openSubScreenFragment<CheatsSubScreenFragment>()
    }

    private inline fun <reified T : Fragment> openSubScreenFragment() {
        if (!isAdded) {
            return
        }

        val currentFragment = childFragmentManager.fragments.lastOrNull()
        if (currentFragment is T) {
            return
        }

        childFragmentManager.commit {
            setCustomAnimations(
                R.anim.fragment_translate_enter_push,
                R.anim.fragment_translate_exit_push,
                R.anim.fragment_translate_enter_pop,
                R.anim.fragment_translate_exit_pop,
            )
            replace<T>(R.id.layout_cheats_root)
            addToBackStack(TAG_BACK_STACK_CHEATS)
        }
    }

    private fun updateTitleFromBackStack() {
        val fragment = childFragmentManager.fragments.lastOrNull()
        val currentTitle = when (fragment) {
            is FoldersSubScreenFragment -> {
                if (viewModel.getGames().size <= 1) {
                    null
                } else {
                    viewModel.getSelectedGame().value?.name
                }
            }
            is CheatsSubScreenFragment -> viewModel.getSelectedFolder().value?.name
            else -> null
        }
        contentTitleChangeListener?.invoke(currentTitle)
    }

    fun setOnContentTitleChangedListener(listener: (String?) -> Unit) {
        contentTitleChangeListener = listener
    }
}
