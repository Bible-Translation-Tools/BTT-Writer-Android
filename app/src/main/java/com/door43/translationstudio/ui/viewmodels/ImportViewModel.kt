package com.door43.translationstudio.ui.viewmodels

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.door43.OnProgressListener
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.ui.dialogs.ProgressHelper
import com.door43.usecases.AdvancedGogsRepoSearch
import com.door43.usecases.BackupRC
import com.door43.usecases.CloneRepository
import com.door43.usecases.ImportProjectFromUri
import com.door43.usecases.RegisterSSHKeys
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.unfoldingword.gogsclient.Repository
import org.unfoldingword.gogsclient.User
import java.io.File
import java.security.InvalidParameterException
import javax.inject.Inject

@HiltViewModel
class ImportViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {
    @Inject lateinit var profile: Profile
    @Inject lateinit var translator: Translator
    @Inject lateinit var advancedGogsRepoSearch: AdvancedGogsRepoSearch
    @Inject lateinit var cloneRepository: CloneRepository
    @Inject lateinit var registerSSHKeys: RegisterSSHKeys
    @Inject lateinit var importProjectFromUri: ImportProjectFromUri
    @Inject lateinit var backupRC: BackupRC

    private val _translation = MutableLiveData<TargetTranslation?>(null)
    val translation: LiveData<TargetTranslation?> = _translation

    private val _progress = MutableLiveData<ProgressHelper.Progress?>(null)
    val progress: LiveData<ProgressHelper.Progress?> = _progress

    private val _repositories = MutableLiveData<List<Repository>>(null)
    val repositories: LiveData<List<Repository>> = _repositories

    private val _cloneRepoResult = MutableLiveData<CloneRepository.Result?>(null)
    val cloneRepoResult: LiveData<CloneRepository.Result?> = _cloneRepoResult

    private val _importFromUriResult = MutableLiveData<ImportProjectFromUri.Result?>(null)
    val importFromUriResult: LiveData<ImportProjectFromUri.Result?> = _importFromUriResult

    private val _registeredSSHKeys = MutableLiveData<Boolean>()
    val registeredSSHKeys: LiveData<Boolean> = _registeredSSHKeys

    fun loadTargetTranslation(translationID: String) {
        translator.getTargetTranslation(translationID)?.let {
            it.setDefaultContributor(profile.nativeSpeaker)
            _translation.value = it
        } ?: throw InvalidParameterException(
            "The target translation '$translationID' is invalid"
        )
    }

    fun searchRepositories(authUser: User, userQuery: String, repoQuery: String, limit: Int) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                advancedGogsRepoSearch.execute(authUser, userQuery, repoQuery, limit, object : OnProgressListener {
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
            _repositories.value = result
            _progress.value = null
        }
    }

    fun cloneRepository(cloneUrl: String) {
        viewModelScope.launch {
            _cloneRepoResult.value = withContext(Dispatchers.IO) {
                cloneRepository.execute(cloneUrl, object : OnProgressListener {
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
            _progress.value = null
        }
    }

    fun importProjectFromUri(path: Uri, mergeOverwrite: Boolean) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                importProjectFromUri.execute(path, mergeOverwrite, object : OnProgressListener {
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
            _importFromUriResult.value = result
            _progress.value = null
        }
    }

    fun registerSSHKeys(force: Boolean) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                registerSSHKeys.execute(force, object : OnProgressListener {
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
            _registeredSSHKeys.value = result
            _progress.value = null
        }
    }

    fun backupAndDeleteTranslation(dir: File) {
        try {
            backupRC.backupTargetTranslation(dir)
            translator.deleteTargetTranslation(dir)
        } catch (e: Exception) {
            Log.w(this::class.simpleName, e)
        }
    }

    fun clearResults() {
        _cloneRepoResult.value = null
        _importFromUriResult.value = null
    }
}