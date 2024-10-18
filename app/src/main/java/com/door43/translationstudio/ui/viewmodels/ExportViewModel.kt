package com.door43.translationstudio.ui.viewmodels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.door43.OnProgressListener
import com.door43.data.IDirectoryProvider
import com.door43.data.IPreferenceRepository
import com.door43.translationstudio.App.Companion.recoverRepo
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
import org.eclipse.jgit.api.errors.NoHeadException
import org.eclipse.jgit.merge.MergeStrategy
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.door43client.models.Translation
import org.unfoldingword.resourcecontainer.Project
import org.unfoldingword.tools.logger.Logger
import java.io.File
import java.security.InvalidParameterException
import java.security.PrivateKey
import java.security.PublicKey
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

    fun getProject(targetTranslation: TargetTranslation): Project {
        return library.index().getProject(
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
                _exportResult.value = ExportProjects.Result(uri, success, ExportProjects.TaskName.EXPORT_PROJECT)
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
            _exportResult.value = ExportProjects.Result(uri, success, ExportProjects.TaskName.EXPORT_USFM)
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
            _exportResult.value = ExportProjects.Result(uri, success, ExportProjects.TaskName.EXPORT_PDF)
        }
    }

    fun downloadImages() {
        viewModelScope.launch {
            _progress.value = ProgressHelper.Progress(application.getString(R.string.downloading_images))
            _downloadResult.value = withContext(Dispatchers.IO) {
                val imagesDir = downloadImages.download(object : OnProgressListener {
                    override fun onProgress(progress: Int, max: Int, message: String?) {
                        _progress.postValue(ProgressHelper.Progress(message, progress, max))
                    }
                    override fun onIndeterminate() {
                        _progress.postValue(ProgressHelper.Progress())
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
                                _progress.postValue(ProgressHelper.Progress(message, progress, max))
                            }
                            override fun onIndeterminate() {
                                _progress.postValue(ProgressHelper.Progress())
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
                            _progress.postValue(ProgressHelper.Progress(message, progress, max))
                        }
                        override fun onIndeterminate() {
                            _progress.postValue(ProgressHelper.Progress())
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
                        _progress.postValue(ProgressHelper.Progress(message, progress, max))
                    }
                    override fun onIndeterminate() {
                        _progress.postValue(ProgressHelper.Progress())
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
                        _progress.postValue(ProgressHelper.Progress(message, progress, max))
                    }
                    override fun onIndeterminate() {
                        _progress.postValue(ProgressHelper.Progress())
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

    fun initializeP2PKeys(): P2PKeys {
        if (!directoryProvider.p2pPrivateKey.exists() || !directoryProvider.p2pPublicKey.exists()) {
            generateP2PKeys()
        }

        val (privateKey, publicKey) = try {
            Pair(
                RSAEncryption.readPrivateKeyFromFile(directoryProvider.p2pPrivateKey),
                RSAEncryption.readPublicKeyFromFile(directoryProvider.p2pPublicKey)
            )
        } catch (e: Exception) {
            // try to regenerate the keys if loading fails
            Logger.w(
                this.javaClass.name,
                "Failed to load the p2p keys. Attempting to regenerate...",
                e
            )
            generateP2PKeys()
            Pair(
                RSAEncryption.readPrivateKeyFromFile(directoryProvider.p2pPrivateKey),
                RSAEncryption.readPublicKeyFromFile(directoryProvider.p2pPublicKey)
            )
        }
        return P2PKeys(privateKey, publicKey)
    }

    fun getTargetTranslationName(translationId: String?): String {
        return translator.getTargetTranslation(translationId)?.let { targetTranslation ->
            val sourceTranslation = library.index().getTranslation(targetTranslation.id)
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
    }

    companion object {
        const val TAG = "BackupViewModel"
    }

    data class P2PKeys(val privateKey: PrivateKey, val publicKey: PublicKey)

}