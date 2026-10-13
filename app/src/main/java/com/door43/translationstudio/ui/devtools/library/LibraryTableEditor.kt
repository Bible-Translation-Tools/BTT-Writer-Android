package com.door43.translationstudio.ui.devtools.library

/**
 * Provides list/edit/save logic for a single library table.
 * Register implementations in LibraryEditorsModule.
 */
interface LibraryTableEditor {
    val table: LibraryTable

    /** Loads all rows of the table for the list view. */
    fun loadRows(): List<LibraryRow>

    /** Builds the editable fields of a row. Empty list if the row does not exist. */
    fun getFields(rowId: String): List<EditableField>

    /**
     * Saves changed values of a row.
     * @param values field key -> new value. Keys not present keep their current value.
     * @return true if saved
     */
    fun save(rowId: String, values: Map<String, String>): Boolean
}
