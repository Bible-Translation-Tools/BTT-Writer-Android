package com.door43.translationstudio.ui.devtools.library

import android.content.Context
import com.door43.translationstudio.R
import com.door43.usecases.UpdateTargetLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import org.unfoldingword.door43client.Door43Client
import org.unfoldingword.door43client.models.TargetLanguage
import javax.inject.Inject

class TargetLanguageEditor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val library: Door43Client,
    private val updateTargetLanguage: UpdateTargetLanguage
) : LibraryTableEditor {

    override val table = LibraryTable.TARGET_LANGUAGES

    override fun loadRows(): List<LibraryRow> {
        val languages: List<TargetLanguage> = library.index.targetLanguages ?: emptyList()
        return languages.map { LibraryRow(it.slug, it.name, it.slug) }
    }

    override fun getFields(rowId: String): List<EditableField> {
        val language = library.index.getTargetLanguage(rowId) ?: return emptyList()
        return listOf(
            EditableField.Text(KEY_SLUG, R.string.field_slug, language.slug, readOnly = true),
            EditableField.Text(KEY_NAME, R.string.field_name, language.name),
            EditableField.Text(KEY_ANGLICIZED_NAME, R.string.field_anglicized_name, language.anglicizedName ?: ""),
            EditableField.Choice(
                KEY_DIRECTION,
                R.string.field_direction,
                language.direction,
                listOf(
                    ChoiceOption("ltr", context.getString(R.string.direction_ltr)),
                    ChoiceOption("rtl", context.getString(R.string.direction_rtl))
                )
            ),
            EditableField.Choice(
                KEY_REGION,
                R.string.field_region,
                language.region ?: "",
                REGIONS.map { ChoiceOption(it, it) }
            ),
            EditableField.Bool(KEY_IS_GATEWAY, R.string.field_is_gateway_language, language.isGatewayLanguage)
        )
    }

    override fun save(rowId: String, values: Map<String, String>): Boolean {
        val current = library.index.getTargetLanguage(rowId) ?: return false
        val language = TargetLanguage(
            rowId,
            values[KEY_NAME] ?: current.name,
            values[KEY_ANGLICIZED_NAME] ?: current.anglicizedName,
            values[KEY_DIRECTION] ?: current.direction,
            values[KEY_REGION] ?: current.region,
            values[KEY_IS_GATEWAY]?.toBoolean() ?: current.isGatewayLanguage
        )
        return updateTargetLanguage.execute(language)
    }

    companion object {
        private const val KEY_SLUG = "slug"
        private const val KEY_NAME = "name"
        private const val KEY_ANGLICIZED_NAME = "anglicized_name"
        private const val KEY_DIRECTION = "direction"
        private const val KEY_REGION = "region"
        private const val KEY_IS_GATEWAY = "is_gateway_language"
        private val REGIONS = listOf("Africa", "Americas", "Asia", "Europe", "Pacific")
    }
}
