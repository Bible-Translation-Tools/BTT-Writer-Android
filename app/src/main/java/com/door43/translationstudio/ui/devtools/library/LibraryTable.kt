package com.door43.translationstudio.ui.devtools.library

import androidx.annotation.StringRes
import com.door43.translationstudio.R

/**
 * Library tables that can be edited by the user
 */
enum class LibraryTable(@StringRes val titleRes: Int) {
    TARGET_LANGUAGES(R.string.edit_library_target_languages)
}

/**
 * A single row of a library table as displayed in a list
 */
data class LibraryRow(
    val id: String,
    val title: String,
    val subtitle: String = ""
)

data class ChoiceOption(val value: String, val label: String)

/**
 * An editable field of a library row
 */
sealed class EditableField(val key: String, @StringRes val labelRes: Int) {
    class Text(
        key: String,
        @StringRes labelRes: Int,
        val value: String,
        val readOnly: Boolean = false
    ) : EditableField(key, labelRes)

    class Choice(
        key: String,
        @StringRes labelRes: Int,
        val value: String,
        val options: List<ChoiceOption>
    ) : EditableField(key, labelRes)

    class Bool(
        key: String,
        @StringRes labelRes: Int,
        val value: Boolean
    ) : EditableField(key, labelRes)
}
