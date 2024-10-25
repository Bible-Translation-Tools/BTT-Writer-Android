package com.door43.translationstudio.ui.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.door43.OnProgressListener
import com.door43.data.IDirectoryProvider
import com.door43.data.IPreferenceRepository
import com.door43.translationstudio.App
import com.door43.translationstudio.core.ContainerCache
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.ui.dialogs.ProgressHelper
import com.door43.translationstudio.ui.translate.ChooseSourceTranslationAdapter.RCItem
import com.door43.usecases.DownloadResourceContainers
import com.door43.usecases.DownloadResourceContainers.DownloadResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.door43client.models.Translation
import javax.inject.Inject

@HiltViewModel
class ChooseSourcesViewModel @Inject constructor(
    application: Application
) : AndroidViewModel(application) {

    @Inject lateinit var downloadResourceContainers: DownloadResourceContainers
    @Inject lateinit var directoryProvider: IDirectoryProvider
    @Inject lateinit var translator: Translator
    @Inject lateinit var prefRepository: IPreferenceRepository
    @Inject lateinit var library: Door43Client

    private val jobs = arrayListOf<Job>()

    private var _targetTranslation: TargetTranslation? = null
    val targetTranslation: TargetTranslation get() = _targetTranslation!!

    private val _progress = MutableLiveData<ProgressHelper.Progress?>(null)
    val progress: LiveData<ProgressHelper.Progress?> = _progress

    private val _items = MutableLiveData<List<RCItem>>(listOf())
    val items: LiveData<List<RCItem>> = _items

    private val _downloadResult = MutableLiveData<DownloadResult?>(null)
    val downloadResult: LiveData<DownloadResult?> = _downloadResult

    private val _itemResult = MutableLiveData<ItemResult?>(null)
    val itemResult: LiveData<ItemResult?> = _itemResult

    var downloadItemPosition = 0

    data class ItemResult(val position: Int, val hasUpdates: Boolean)

    fun setTargetTranslation(translationId: String) {
        _targetTranslation = translator.getTargetTranslation(translationId)
    }

    fun noTargetTranslation(): Boolean {
        return _targetTranslation == null
    }

    fun cancelJobs() {
        jobs.forEach { it.cancel() }
    }

    fun loadData() {
        viewModelScope.launch {
            _progress.value = ProgressHelper.Progress()

            withContext(Dispatchers.IO) {
                val items = arrayListOf<RCItem>()
                // add selected source translations
                val sourceTranslationSlugs = getOpenSourceTranslations(targetTranslation.id)
                for (slug in sourceTranslationSlugs) {
                    val st = library.index.getTranslation(slug)
                    if (st != null) {
                        _progress.postValue(ProgressHelper.Progress(st.resourceContainerSlug))
                        items.add(addSourceTranslation(st, true))
                    }
                }

                val availableTranslations = library.index.findTranslations(
                    null,
                    targetTranslation.projectId,
                    null,
                    "book",
                    null,
                    App.MIN_CHECKING_LEVEL,
                    -1
                )
                for (sourceTranslation in availableTranslations) {
                    _progress.postValue(ProgressHelper.Progress(sourceTranslation.resourceContainerSlug))
                    items.add(addSourceTranslation(sourceTranslation, false))
                }
                _items.postValue(items)
            }
            _progress.value = null
        }.also(jobs::add)
    }

    fun downloadResourceContainer(sourceTranslation: Translation, position: Int) {
        viewModelScope.launch {
            downloadItemPosition = position
            _progress.value = ProgressHelper.Progress()
            _downloadResult.value = withContext(Dispatchers.IO) {
                downloadResourceContainers.download(sourceTranslation, object : OnProgressListener {
                    override fun onProgress(progress: Int, max: Int, message: String?) {
                        _progress.postValue(ProgressHelper.Progress(message, progress, max))
                    }
                    override fun onIndeterminate() {
                        _progress.postValue(ProgressHelper.Progress())
                    }
                })
            }
            _progress.value = null
        }.also(jobs::add)
    }

    fun deleteResourceContainer(slug: String) {
        library.delete(slug)
    }

    fun getOpenSourceTranslations(targetTranslationId: String): Array<String> {
        return prefRepository.getOpenSourceTranslations(targetTranslationId)
    }

    fun checkForContainerUpdates(containerSlug: String, position: Int) {
        viewModelScope.launch {
            _progress.value = ProgressHelper.Progress()
            _itemResult.value = withContext(Dispatchers.IO) {
                Log.i(
                    ChooseSourcesViewModel::class.java.simpleName,
                    "Checking for updates on $containerSlug"
                )
                try {
                    val container = library.open(containerSlug)
                    val lastModified = library.getResourceContainerLastModified(
                        container.language.slug,
                        container.project.slug,
                        container.resource.slug
                    )
                    ItemResult(position, lastModified > container.modifiedAt)
                } catch (e: Exception) {
                    e.printStackTrace()
                    ItemResult(position, false)
                }
            }
            _progress.value = null
        }
    }

    fun clearResults() {
        _downloadResult.value = null
    }

    /**
     * adds this source translation to the adapter
     * @param sourceTranslation
     * @param selected
     */
    private fun addSourceTranslation(sourceTranslation: Translation, selected: Boolean): RCItem {
        val title = sourceTranslation.language.name + " (" + sourceTranslation.language.slug + ") - " + sourceTranslation.resource.name
        val isDownloaded = library.exists(sourceTranslation.resourceContainerSlug)

        val item = RCItem(
            title,
            sourceTranslation,
            selected,
            isDownloaded
        )

        if (item.selected) { // see if there are updates available to download
            Log.i(
                this::class.java.simpleName,
                "Checking for updates on " + item.containerSlug
            )
            var hasUpdates = false
            try {
                val container = ContainerCache.cache(library, item.containerSlug)
                val lastModified: Int = library.getResourceContainerLastModified(
                    container.language.slug,
                    container.project.slug,
                    container.resource.slug
                )
                hasUpdates = (lastModified > container.modifiedAt)
                Log.i(
                    this::class.java.simpleName,
                    "Checking for updates on " + item.containerSlug + " finished, needs updates: " + hasUpdates
                )
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
            }
            item.hasUpdates = hasUpdates
            item.checkedUpdates = true
        }

        return item
    }
}