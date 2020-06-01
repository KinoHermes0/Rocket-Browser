package org.mozilla.rocket.settings.defaultbrowser.ui

import android.os.Build
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.mozilla.rocket.download.SingleLiveEvent
import org.mozilla.rocket.settings.defaultbrowser.data.DefaultBrowserRepository

class DefaultBrowserPreferenceViewModel(private val defaultBrowserRepository: DefaultBrowserRepository) : ViewModel() {

    private val _uiModel = MutableLiveData<DefaultBrowserPreferenceUiModel>()
    val uiModel: LiveData<DefaultBrowserPreferenceUiModel>
        get() = _uiModel

    val openDefaultAppsSettings = SingleLiveEvent<Unit>()
    val openAppDetailSettings = SingleLiveEvent<Unit>()
    val openSumoPage = SingleLiveEvent<Unit>()
    val triggerWebOpen = SingleLiveEvent<Unit>()

    val openDefaultAppsSettingsTutorialDialog = SingleLiveEvent<Unit>()

    val successToSetDefaultBrowser = SingleLiveEvent<Unit>()
    val failToSetDefaultBrowser = SingleLiveEvent<Unit>()

    private var isDefaultBrowser: Boolean = false
    private var hasDefaultBrowser: Boolean = false

    private var tryToSetDefaultBrowser: Boolean = false

    fun refreshSettings() {
        isDefaultBrowser = defaultBrowserRepository.isDefaultBrowser()
        hasDefaultBrowser = defaultBrowserRepository.hasDefaultBrowser()

        _uiModel.value = DefaultBrowserPreferenceUiModel(isDefaultBrowser, hasDefaultBrowser)
    }

    fun performSettingDefaultBrowserAction() {
        when {
            isDefaultBrowser -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    openDefaultAppsSettings.call()
                } else {
                    openAppDetailSettings.call()
                }
            }
            hasDefaultBrowser -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    openDefaultAppsSettingsTutorialDialog.call()
                } else {
                    // TODO: Change to the flow #4 in SPEC
                    openSumoPage.call()
                }
            }
            else -> {
                triggerWebOpen.call()
            }
        }
    }

    fun onResume() {
        refreshSettings()
        if (tryToSetDefaultBrowser) {
            if (isDefaultBrowser) {
                successToSetDefaultBrowser.call()
            } else {
                failToSetDefaultBrowser.call()
            }
            tryToSetDefaultBrowser = false
        }
    }

    fun onPause() {
    }

    fun clickGoToSystemDefaultAppsSettings() {
        tryToSetDefaultBrowser = true
        openDefaultAppsSettings.call()
    }

    fun cancelGoToSystemDefaultAppsSettings() {
    }

    data class DefaultBrowserPreferenceUiModel(
        val isDefaultBrowser: Boolean,
        val hasDefaultBrowser: Boolean
    )
}