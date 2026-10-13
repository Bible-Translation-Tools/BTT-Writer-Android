package com.door43.translationstudio.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.devtools.library.EditableField
import com.door43.translationstudio.ui.devtools.library.LibraryRow
import com.door43.translationstudio.ui.devtools.library.LibraryTable
import com.door43.translationstudio.ui.devtools.library.LibraryTableEditor
import com.door43.translationstudio.ui.dialogs.ProgressHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.unfoldingword.tools.logger.Logger
import javax.inject.Inject

@HiltViewModel
class EditLibraryViewModel @Inject constructor(
    private val application: Application,
    editors: Set<@JvmSuppressWildcards LibraryTableEditor>
) : AndroidViewModel(application) {

    private val editors: Map<LibraryTable, LibraryTableEditor> = editors.associateBy { it.table }

    /** Tables that have a registered editor, in enum order */
    val tables: List<LibraryTable> = LibraryTable.entries.filter { it in this.editors }

    private val _progress = MutableLiveData<ProgressHelper.Progress?>()
    val progress: LiveData<ProgressHelper.Progress?> = _progress

    private val _rows = MutableLiveData<List<LibraryRow>>()
    val rows: LiveData<List<LibraryRow>> = _rows

    private val _saveResult = MutableLiveData<Boolean?>()
    val saveResult: LiveData<Boolean?> = _saveResult

    fun loadRows(table: LibraryTable) {
        viewModelScope.launch {
            _progress.value = ProgressHelper.Progress(application.getString(R.string.please_wait))
            val rows: List<LibraryRow> = withContext(Dispatchers.IO) {
                editorFor(table).loadRows()
            }
            _rows.value = rows
            _progress.value = null
        }
    }

    fun getFields(table: LibraryTable, rowId: String): List<EditableField> {
        return editorFor(table).getFields(rowId)
    }

    fun save(table: LibraryTable, rowId: String, values: Map<String, String>) {
        viewModelScope.launch {
            _progress.value = ProgressHelper.Progress(application.getString(R.string.edit_library_saving))
            val result: Boolean = withContext(Dispatchers.IO) {
                try {
                    editorFor(table).save(rowId, values)
                } catch (e: Exception) {
                    Logger.e(TAG, "Failed to save $table row $rowId", e)
                    false
                }
            }
            _saveResult.value = result
            _progress.value = null
        }
    }

    fun clearSaveResult() {
        _saveResult.value = null
    }

    private fun editorFor(table: LibraryTable): LibraryTableEditor {
        return editors[table] ?: throw IllegalStateException("No editor registered for $table")
    }

    companion object {
        private const val TAG = "EditLibraryViewModel"
    }
}
