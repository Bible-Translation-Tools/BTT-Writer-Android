package com.door43.translationstudio.ui.viewmodels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.door43.OnProgressListener
import com.door43.data.IDirectoryProvider
import com.door43.translationstudio.App.Companion.recoverRepo
import com.door43.translationstudio.R
import com.door43.translationstudio.core.DownloadImages
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.ui.dialogs.ProgressHelper
import com.door43.usecases.CreateRepository
import com.door43.usecases.Export
import com.door43.usecases.GogsLogout
import com.door43.usecases.PullTargetTranslation
import com.door43.usecases.PushTargetTranslation
import com.door43.usecases.RegisterSSHKeys
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.errors.NoHeadException
import org.eclipse.jgit.merge.MergeStrategy
import org.unfoldingword.tools.logger.Logger
import java.io.File
import java.security.InvalidParameterException
import javax.inject.Inject

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val application: Application,
) : AndroidViewModel(application) {

    @Inject lateinit var export: Export
    @Inject lateinit var downloadImages: DownloadImages
    @Inject lateinit var translator: Translator
    @Inject lateinit var profile: Profile
    @Inject lateinit var pullTargetTranslation: PullTargetTranslation
    @Inject lateinit var pushTargetTranslation: PushTargetTranslation
    @Inject lateinit var registerSSHKeys: RegisterSSHKeys
    @Inject lateinit var createRepository: CreateRepository
    @Inject lateinit var gogsLogout: GogsLogout
    @Inject lateinit var directoryProvider: IDirectoryProvider

    private val _translation = MutableLiveData<TargetTranslation?>(null)
    val translation: LiveData<TargetTranslation?> = _translation

    private val _progress = MutableLiveData<ProgressHelper.Progress?>(null)
    val progress: LiveData<ProgressHelper.Progress?> = _progress

    private val _exportResult = MutableLiveData<Export.Result?>(null)
    val exportResult: LiveData<Export.Result?> = _exportResult

    private val _downloadResult = MutableLiveData<DownloadImages.Result?>(null)
    val downloadResult: LiveData<DownloadImages.Result?> = _downloadResult

    private val _pullTranslationResult = MutableLiveData<PullTargetTranslation.Result?>(null)
    val pullTranslationResult: LiveData<PullTargetTranslation.Result?> = _pullTranslationResult

    private val _pushTranslationResult = MutableLiveData<PushTargetTranslation.Result?>(null)
    val pushTranslationResult: LiveData<PushTargetTranslation.Result?> = _pushTranslationResult

    private val _registeredSSHKeys = MutableLiveData<Boolean>()
    val registeredSSHKeys: LiveData<Boolean> = _registeredSSHKeys

    private val _repoCreated = MutableLiveData<Boolean>()
    val repoCreated: LiveData<Boolean> = _repoCreated

    private val _exportedToApp = MutableLiveData<File?>()
    val exportedToApp: LiveData<File?> = _exportedToApp

    fun loadTargetTranslation(translationID: String) {
        translator.getTargetTranslation(translationID)?.let {
            it.setDefaultContributor(profile.nativeSpeaker)
            _translation.value = it
        } ?: throw InvalidParameterException(
            "The target translation '$translationID' is invalid"
        )
    }

    /**
     * back up project - will try to write to user selected destination
     * @param uri
     */
    fun exportProject(uri: Uri) {
        translation.value?.let { targetTranslation ->
            viewModelScope.launch {
                _progress.value = ProgressHelper.Progress()
                var success: Boolean
                try {
                    export.exportProject(targetTranslation, uri)
                    success = true
                } catch (e: NoHeadException) {
                    // fix corrupt repo and try again
                    recoverRepo(targetTranslation)
                    export.exportProject(targetTranslation, uri)
                    success = true
                } catch (e: Exception) {
                    success = false
                }

                _progress.value = null
                _exportResult.value = Export.Result(uri, success, Export.TaskName.EXPORT_PROJECT)
            }
        }
    }

    /**
     * Save file to USFM format
     */
    fun exportUSFM(uri: Uri) {
        viewModelScope.launch {
            _progress.value = ProgressHelper.Progress(application.getString(R.string.exporting))
            var success = false

            translation.value?.let { targetTranslation ->
                try {
                    export.exportUSFM(targetTranslation, uri)
                    success = true
                } catch (e: Exception) {
                }
            }
            _progress.value = null
            _exportResult.value = Export.Result(uri, success, Export.TaskName.EXPORT_USFM)
        }
    }

    fun exportPDF(
        uri: Uri,
        includeImages: Boolean,
        includeIncompleteFrames: Boolean,
        imagesDir: File?
    ) {
        viewModelScope.launch {
            _progress.value = ProgressHelper.Progress(application.getString(R.string.printing))
            var success = false
            translation.value?.let { targetTranslation ->
                try {
                    export.exportPDF(
                        targetTranslation,
                        uri,
                        includeImages,
                        includeIncompleteFrames,
                        imagesDir
                    )
                    success = true
                } catch (e: Exception) {
                }
            }
            _progress.value = null
            _exportResult.value = Export.Result(uri, success, Export.TaskName.EXPORT_PDF)
        }
    }

    fun downloadImages() {
        viewModelScope.launch {
            _progress.value = ProgressHelper.Progress(application.getString(R.string.downloading_images))
            _downloadResult.value = withContext(Dispatchers.IO) {
                val imagesDir = downloadImages.download(object : OnProgressListener {
                    override fun onProgress(progress: Int, max: Int, message: String?) {
                        launch(Dispatchers.Main) {
                            _progress.value = ProgressHelper.Progress(
                                message,
                                progress,
                                max
                            )
                        }
                    }
                    override fun onIndeterminate() {
                        launch(Dispatchers.Main) {
                            _progress.value = ProgressHelper.Progress()
                        }
                    }
                })
                DownloadImages.Result(imagesDir?.exists() == true, imagesDir)
            }
            _progress.value = null
        }
    }

    fun pullTargetTranslation(strategy: MergeStrategy) {
        viewModelScope.launch {
            translation.value?.let { targetTranslation ->
                _progress.value = ProgressHelper.Progress(
                    application.getString(R.string.uploading)
                )
                val result = withContext(Dispatchers.IO) {
                    pullTargetTranslation.execute(
                        targetTranslation,
                        strategy,
                        progressListener = object : OnProgressListener {
                            override fun onProgress(progress: Int, max: Int, message: String?) {
                                launch(Dispatchers.Main) {
                                    _progress.value = ProgressHelper.Progress(
                                        message,
                                        progress,
                                        max
                                    )
                                }
                            }
                            override fun onIndeterminate() {
                                launch(Dispatchers.Main) {
                                    _progress.value = ProgressHelper.Progress()
                                }
                            }
                        }
                    )
                }
                _pullTranslationResult.value = result
                _progress.value = null
            }
        }
    }

    fun pushTargetTranslation() {
        viewModelScope.launch {
            translation.value?.let { targetTranslation ->
                _progress.value = ProgressHelper.Progress(
                    application.getString(R.string.uploading)
                )
                val result = withContext(Dispatchers.IO) {
                    pushTargetTranslation.execute(targetTranslation, object : OnProgressListener {
                        override fun onProgress(progress: Int, max: Int, message: String?) {
                            launch(Dispatchers.Main) {
                                _progress.value = ProgressHelper.Progress(
                                    message,
                                    progress,
                                    max
                                )
                            }
                        }
                        override fun onIndeterminate() {
                            launch(Dispatchers.Main) {
                                _progress.value = ProgressHelper.Progress()
                            }
                        }
                    })
                }
                _pushTranslationResult.value = result
                _progress.value = null
            }
        }
    }

    fun registerSSHKeys(force: Boolean) {
        viewModelScope.launch {
            _progress.value = ProgressHelper.Progress(
                application.getString(R.string.registering_keys)
            )
            val result = withContext(Dispatchers.IO) {
                registerSSHKeys.execute(force, object : OnProgressListener {
                    override fun onProgress(progress: Int, max: Int, message: String?) {
                        launch(Dispatchers.Main) {
                            _progress.value = ProgressHelper.Progress(
                                message,
                                progress,
                                max
                            )
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

    fun createRepository() {
        viewModelScope.launch {
            translation.value?.let { targetTranslation ->
                _progress.value = ProgressHelper.Progress(
                    application.getString(R.string.creating_repository)
                )
                _repoCreated.value = createRepository.execute(targetTranslation, object : OnProgressListener {
                    override fun onProgress(progress: Int, max: Int, message: String?) {
                        launch(Dispatchers.Main) {
                            _progress.value = ProgressHelper.Progress(
                                message,
                                progress,
                                max
                            )
                        }
                    }
                    override fun onIndeterminate() {
                        launch(Dispatchers.Main) {
                            _progress.value = ProgressHelper.Progress()
                        }
                    }
                })
                _progress.value = null
            }
        }
    }

    /**
     * Log out the current gogs user
     */
    fun logout() {
        viewModelScope.launch {
            _progress.value = ProgressHelper.Progress(
                application.getString(R.string.log_out)
            )
            withContext(Dispatchers.IO) {
                gogsLogout.execute()
            }
            _progress.value = null
        }
    }

    fun exportToApp() {
        viewModelScope.launch {
            translation.value?.let { translation ->
                _progress.value = ProgressHelper.Progress(application.getString(R.string.exporting))
                val filename = translation.id + "." + Translator.TSTUDIO_EXTENSION
                val exportFile = File(directoryProvider.sharingDir, filename)
                try {
                    export.exportProject(translation, exportFile)
                    _exportedToApp.value = exportFile
                } catch (e: Exception) {
                    Logger.e(
                        TAG,
                        "Failed to export the target translation " + translation.id,
                        e
                    )
                }
                _progress.value = null
            }
        }
    }

    fun clearResults() {
        _exportResult.value = null
        _downloadResult.value = null
    }

    companion object {
        const val TAG = "BackupViewModel"
    }

}