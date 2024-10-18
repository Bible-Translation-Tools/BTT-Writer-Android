package com.door43.translationstudio.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.ui.dialogs.ProgressHelper
import com.door43.usecases.ImportDraft
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.door43client.models.SourceLanguage
import org.unfoldingword.door43client.models.Translation
import org.unfoldingword.resourcecontainer.ResourceContainer
import javax.inject.Inject

@HiltViewModel
class DraftViewModel @Inject constructor(
    private val application: Application
) : AndroidViewModel(application) {
    @Inject lateinit var importDraft: ImportDraft
    @Inject lateinit var translator: Translator
    @Inject lateinit var library: Door43Client

    private val _progress = MutableLiveData<ProgressHelper.Progress?>(null)
    val progress: LiveData<ProgressHelper.Progress?> = _progress

    private val _importResult = MutableLiveData<ImportDraft.Result?>()
    val importResult: LiveData<ImportDraft.Result?> = _importResult

    private val _draftTranslations = MutableLiveData<List<Translation>>()
    val draftTranslations: LiveData<List<Translation>> = _draftTranslations

    fun loadDraftTranslations(targetTranslationId: String?) {
        viewModelScope.launch {
            translator.getTargetTranslation(targetTranslationId)?.let { targetTranslation ->
                _draftTranslations.value = library.index().findTranslations(
                    targetTranslation.targetLanguage.slug,
                    targetTranslation.projectId,
                    null,
                    "book",
                    null,
                    0,
                    -1
                )
            }
        }
    }

    fun importDraft(sourceContainer: ResourceContainer) {
        viewModelScope.launch {
            _progress.value = ProgressHelper.Progress(
                application.getString(R.string.importing_draft)
            )
            _importResult.value = withContext(Dispatchers.IO) {
                importDraft.execute(sourceContainer)
            }
            _progress.value = null
        }
    }

    fun getResourceContainer(rcSlug: String): ResourceContainer? {
        return try {
            library.open(rcSlug)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getSourceLanguage(draftTranslation: ResourceContainer): SourceLanguage? {
        return try {
            library.index().getSourceLanguage(
                draftTranslation.info.getJSONObject("language").getString("slug")
            )
        } catch (e: JSONException) {
            e.printStackTrace()
            null
        }
    }
}