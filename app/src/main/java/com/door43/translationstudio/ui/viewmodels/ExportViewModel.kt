package com.door43.translationstudio.ui.viewmodels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.door43.data.IDirectoryProvider
import com.door43.data.IPreferenceRepository
import com.door43.translationstudio.R
import com.door43.translationstudio.core.DownloadImages
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.ui.dialogs.ProgressHelper
import com.door43.usecases.CreateRepository
import com.door43.usecases.ExportProjects
import com.door43.usecases.GogsLogout
import com.door43.usecases.PullTargetTranslation
import com.door43.usecases.PushTargetTranslation
import com.door43.usecases.RegisterSSHKeys
import com.door43.util.RSAEncryption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.jgit.merge.MergeStrategy
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.door43client.models.Translation
import org.unfoldingword.resourcecontainer.Project
import org.unfoldingword.tools.logger.Logger
import java.io.File
import java.security.InvalidParameterException
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ExportViewModel @Inject constructor(
    private val application: Application,
) : AndroidViewModel(application) {

    @Inject lateinit var export: ExportProjects
    @Inject lateinit var downloadImages: DownloadImages
    @Inject lateinit var translator: Translator
    @Inject lateinit var profile: Profile
    @Inject lateinit var pullTargetTranslation: PullTargetTranslation
    @Inject lateinit var pushTargetTranslation: PushTargetTranslation
    @Inject lateinit var registerSSHKeys: RegisterSSHKeys
    @Inject lateinit var createRepository: CreateRepository
    @Inject lateinit var gogsLogout: GogsLogout
    @Inject lateinit var directoryProvider: IDirectoryProvider
    @Inject lateinit var prefRepository: IPreferenceRepository
    @Inject lateinit var library: Door43Client

    private val _translation = MutableLiveData<TargetTranslation?>(null)
    val translation: LiveData<TargetTranslation?> = _translation

    private val _progress = MutableLiveData<ProgressHelper.Progress?>(null)
    val progress: LiveData<ProgressHelper.Progress?> = _progress

    private val _exportResult = MutableLiveData<ExportProjects.Result?>(null)
    val exportResult: LiveData<ExportProjects.Result?> = _exportResult

    private val _downloadResult = MutableLiveData<DownloadImages.Result?>(null)
    val downloadResult: LiveData<DownloadImages.Result?> = _downloadResult

    private val _pullTranslationResult = MutableLiveData<PullTargetTranslation.Result?>(null)
    val pullTranslationResult: LiveData<PullTargetTranslation.Result?> = _pullTranslationResult

    private val _pushTranslationResult = MutableLiveData<PushTargetTranslation.Result?>(null)
    val pushTranslationResult: LiveData<PushTargetTranslation.Result?> = _pushTranslationResult

    private val _registeredSSHKeys = MutableLiveData<Boolean?>()
    val registeredSSHKeys: LiveData<Boolean?> = _registeredSSHKeys

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

    fun getProject(targetTranslation: TargetTranslation): Project {
        return library.index.getProject(
            "en",
            targetTranslation.projectId,
            true
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
                _exportResult.value = withContext(Dispatchers.IO) {
                    export.exportProject(targetTranslation, uri)
                }
                _progress.value = null
            }
        }
    }

    /**
     * Save file to USFM format
     */
    fun exportUSFM(uri: Uri) {
        viewModelScope.launch {
            _progress.value = ProgressHelper.Progress(application.getString(R.string.exporting))
            translation.value?.let { targetTranslation ->
                _exportResult.value = withContext(Dispatchers.IO) {
                    export.exportUSFM(targetTranslation, uri)
                }
            }
            _progress.value = null
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
            translation.value?.let { targetTranslation ->
                _exportResult.value = withContext(Dispatchers.IO) {
                    export.exportPDF(
                        targetTranslation,
                        uri,
                        includeImages,
                        includeIncompleteFrames,
                        imagesDir
                    )
                }
            }
            _progress.value = null
        }
    }

    fun downloadImages() {
        viewModelScope.launch {
            _progress.value = ProgressHelper.Progress(application.getString(R.string.downloading_images))
            _downloadResult.value = withContext(Dispatchers.IO) {
                val imagesDir = downloadImages.download { progress, max, message ->
                    _progress.postValue(ProgressHelper.Progress(message, progress, max))
                }
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
                _pullTranslationResult.value = withContext(Dispatchers.IO) {
                    pullTargetTranslation.execute(
                        targetTranslation,
                        strategy,
                        null
                    ) { progress, max, message ->
                        _progress.postValue(
                            ProgressHelper.Progress(
                                message,
                                progress,
                                max
                            )
                        )
                    }
                }
                _progress.value = null
            }
        }
    }

    fun pushTargetTranslation() {
        viewModelScope.launch {
            translation.value?.let { targetTranslation ->
                _progress.value = ProgressHelper.Progress(application.getString(R.string.uploading))
                _pullTranslationResult.value = null
                _pushTranslationResult.value = withContext(Dispatchers.IO) {
                    pushTargetTranslation.execute(targetTranslation) { progress, max, message ->
                        _progress.postValue(
                            ProgressHelper.Progress(
                                message,
                                progress,
                                max
                            )
                        )
                    }
                }
                _progress.value = null
            }
        }
    }

    fun registerSSHKeys(force: Boolean) {
        viewModelScope.launch {
            _progress.value = ProgressHelper.Progress(
                application.getString(R.string.registering_keys)
            )
            _registeredSSHKeys.value = withContext(Dispatchers.IO) {
                registerSSHKeys.execute(force) { progress, max, message ->
                    _progress.postValue(
                        ProgressHelper.Progress(
                            message,
                            progress,
                            max
                        )
                    )
                }
            }
            _progress.value = null
        }
    }

    fun createRepository() {
        viewModelScope.launch {
            translation.value?.let { targetTranslation ->
                _progress.value = ProgressHelper.Progress(
                    application.getString(R.string.creating_repository)
                )
                _repoCreated.value =
                    createRepository.execute(targetTranslation) { progress, max, message ->
                        _progress.postValue(
                            ProgressHelper.Progress(
                                message,
                                progress,
                                max
                            )
                        )
                }
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
                try {
                    val filename = translation.id + "." + Translator.TSTUDIO_EXTENSION
                    val exportFile = File(directoryProvider.sharingDir, filename)
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

    fun initializeP2PKeys() {
        if (!directoryProvider.p2pPrivateKey.exists() || !directoryProvider.p2pPublicKey.exists()) {
            generateP2PKeys()
        }

        try {
            RSAEncryption.readPrivateKeyFromFile(directoryProvider.p2pPrivateKey)
            RSAEncryption.readPublicKeyFromFile(directoryProvider.p2pPublicKey)
        } catch (e: Exception) {
            // try to regenerate the keys if loading fails
            Logger.w(
                this.javaClass.name,
                "Failed to load the p2p keys. Attempting to regenerate...",
                e
            )
            generateP2PKeys()
        }
    }

    fun getTargetTranslationName(translationId: String?): String {
        return translator.getTargetTranslation(translationId)?.let { targetTranslation ->
            val sourceTranslation = library.index.getTranslation(targetTranslation.id)
            return if (sourceTranslation != null) {
                sourceTranslation.project.name + " - " + targetTranslation.targetLanguage.name
            } else {
                targetTranslation.projectId + " - " + targetTranslation.targetLanguage.name
            }
        } ?: ""
    }

    fun getProjectTitle(targetTranslation: TargetTranslation): String {
        val project = library.index.getProject(
            Locale.getDefault().language,
            targetTranslation.projectId,
            true
        )
        return if (project != null) {
            project.name + " - " + targetTranslation.targetLanguageName
        } else {
            targetTranslation.projectId + " - " + targetTranslation.targetLanguageName
        }
    }

    fun getProject(targetTranslationId: String?): Project? {
        return library.index.getProject(
            Locale.getDefault().language,
            targetTranslationId
        )
    }

    fun getTranslation(sourceTranslationId: String): Translation? {
        return library.index.getTranslation(sourceTranslationId)
    }

    fun getOpenSourceTranslations(targetTranslationId: String?): Array<String> {
        return targetTranslationId?.let {
            prefRepository.getOpenSourceTranslations(it)
        } ?: arrayOf()
    }

    private fun generateP2PKeys() {
        RSAEncryption.generateKeys(directoryProvider.p2pPrivateKey, directoryProvider.p2pPublicKey)
    }

    fun clearResults() {
        _exportResult.value = null
        _downloadResult.value = null
        _pushTranslationResult.value = null
        _pullTranslationResult.value = null
    }

    companion object {
        const val TAG = "BackupViewModel"
    }

}