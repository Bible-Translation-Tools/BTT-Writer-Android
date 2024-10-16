package com.door43.translationstudio.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.door43.OnProgressListener
import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref
import com.door43.data.setDefaultPref
import com.door43.data.setPrivatePref
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.SettingsActivity.Companion.KEY_PREF_CHECK_HARDWARE
import com.door43.translationstudio.ui.dialogs.ProgressHelper
import com.door43.usecases.UpdateApp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SplashScreenViewModel @Inject constructor(
    private val application: Application
) : AndroidViewModel(application) {

    @Inject
    lateinit var updateApp: UpdateApp
    @Inject
    lateinit var prefRepository: IPreferenceRepository

    private val _progress = MutableLiveData<ProgressHelper.Progress?>()
    val progress: LiveData<ProgressHelper.Progress?> = _progress

    private val _updateFinished = MutableLiveData(false)
    val updateFinished: LiveData<Boolean> = _updateFinished

    fun updateApp() {
        viewModelScope.launch {
            _progress.value = ProgressHelper.Progress(
                application.getString(R.string.updating_app)
            )
            withContext(Dispatchers.IO) {
                updateApp.execute(object : OnProgressListener {
                    override fun onProgress(progress: Int, max: Int, message: String?) {
                        launch(Dispatchers.Main) {
                            _progress.value = ProgressHelper.Progress(message, progress, max)
                        }
                    }
                    override fun onIndeterminate() {
                        launch(Dispatchers.Main) {
                            _progress.value = ProgressHelper.Progress()
                        }
                    }
                })
            }
            _updateFinished.value = true
            _progress.value = null
        }
    }

    fun checkHardware(): Boolean {
        return prefRepository.getDefaultPref(
            KEY_PREF_CHECK_HARDWARE,
            true
        )
    }

    fun saveHardwareCheck(checked: Boolean) {
        prefRepository.setDefaultPref(KEY_PREF_CHECK_HARDWARE, checked)
    }
}