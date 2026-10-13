package com.door43.translationstudio.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.devtools.library.ChoiceOption
import com.door43.translationstudio.ui.devtools.library.EditableField
import com.door43.translationstudio.ui.devtools.library.LibraryRow
import com.door43.translationstudio.ui.devtools.library.LibraryTable
import com.door43.translationstudio.ui.dialogs.ProgressHelper
import com.door43.usecases.UpdateTargetLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.door43client.models.TargetLanguage
import org.unfoldingword.tools.logger.Logger
import javax.inject.Inject

@HiltViewModel
class EditLibraryViewModel @Inject constructor(
    private val application: Application
) : AndroidViewModel(application) {

    @Inject lateinit var library: Door43Client
    @Inject lateinit var updateTargetLanguage: UpdateTargetLanguage

    val tables: List<LibraryTable> = LibraryTable.values().toList()

    private val _progress = MutableLiveData<ProgressHelper.Progress?>()
    val progress: LiveData<ProgressHelper.Progress?> = _progress

    private val _rows = MutableLiveData<List<LibraryRow>>()
    val rows: LiveData<List<LibraryRow>> = _rows

    private val _saveResult = MutableLiveData<Boolean?>()
    val saveResult: LiveData<Boolean?> = _saveResult

    fun loadRows(table: LibraryTable) {
        viewModelScope.launch {
            _progress.value = ProgressHelper.Progress(application.getString(R.string.please_wait))
            _rows.value = withContext(Dispatchers.IO) {
                when (table) {
                    LibraryTable.TARGET_LANGUAGES -> library.index.targetLanguages.map {
                        LibraryRow(it.slug, it.name, it.slug)
                    }
                }
            }
            _progress.value = null
        }
    }

    fun getFields(table: LibraryTable, rowId: String): List<EditableField> {
        return when (table) {
            LibraryTable.TARGET_LANGUAGES -> targetLanguageFields(rowId)
        }
    }

    fun save(table: LibraryTable, rowId: String, values: Map<String, String>) {
        viewModelScope.launch {
            _progress.value = ProgressHelper.Progress(application.getString(R.string.edit_library_saving))
            _saveResult.value = withContext(Dispatchers.IO) {
                try {
                    when (table) {
                        LibraryTable.TARGET_LANGUAGES -> saveTargetLanguage(rowId, values)
                    }
                } catch (e: Exception) {
                    Logger.e(TAG, "Failed to save $table row $rowId", e)
                    false
                }
            }
            _progress.value = null
        }
    }

    fun clearSaveResult() {
        _saveResult.value = null
    }

    private fun targetLanguageFields(slug: String): List<EditableField> {
        val language = library.index.getTargetLanguage(slug) ?: return emptyList()
        return listOf(
            EditableField.Text("slug", R.string.field_slug, language.slug, readOnly = true),
            EditableField.Text("name", R.string.field_name, language.name),
            EditableField.Text("anglicized_name", R.string.field_anglicized_name, language.anglicizedName ?: ""),
            EditableField.Choice(
                "direction",
                R.string.field_direction,
                language.direction,
                listOf(
                    ChoiceOption("ltr", application.getString(R.string.direction_ltr)),
                    ChoiceOption("rtl", application.getString(R.string.direction_rtl))
                )
            ),
            EditableField.Choice(
                "region",
                R.string.field_region,
                language.region ?: "",
                REGIONS.map { ChoiceOption(it, it) }
            ),
            EditableField.Bool("is_gateway_language", R.string.field_is_gateway_language, language.isGatewayLanguage)
        )
    }

    private fun saveTargetLanguage(slug: String, values: Map<String, String>): Boolean {
        val current = library.index.getTargetLanguage(slug) ?: return false
        val language = TargetLanguage(
            slug,
            values["name"] ?: current.name,
            values["anglicized_name"] ?: current.anglicizedName,
            values["direction"] ?: current.direction,
            values["region"] ?: current.region,
            values["is_gateway_language"]?.toBoolean() ?: current.isGatewayLanguage
        )
        return updateTargetLanguage.execute(language)
    }

    companion object {
        private const val TAG = "EditLibraryViewModel"
        private val REGIONS = listOf("Africa", "Americas", "Asia", "Europe", "Pacific")
    }
}
