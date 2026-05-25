package me.magnum.melonds.ui.layouts

import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.schedulers.Schedulers as RxSchedulers
import me.magnum.melonds.common.Schedulers
import me.magnum.melonds.domain.repositories.LayoutsRepository
import me.magnum.melonds.domain.repositories.SettingsRepository
import me.magnum.melonds.extensions.addTo
import me.magnum.melonds.impl.LayoutFileOperations
import java.util.*
import javax.inject.Inject

@HiltViewModel
class LayoutsViewModel @Inject constructor(
        layoutsRepository: LayoutsRepository,
        layoutFileOperations: LayoutFileOperations,
        schedulers: Schedulers,
        private val settingsRepository: SettingsRepository,
) : BaseLayoutsViewModel(layoutsRepository, layoutFileOperations, schedulers) {
    init {
        layoutsRepository.getLayouts()
                .subscribeOn(RxSchedulers.io())
                .subscribe {
                    layoutsLiveData.postValue(it)
                }.addTo(disposables)
    }

    override fun getSelectedLayoutId(): UUID {
        return settingsRepository.getSelectedLayoutId()
    }

    override fun setSelectedLayoutId(id: UUID?) {
        id?.let {
            settingsRepository.setSelectedLayoutId(it)
        }
    }
}