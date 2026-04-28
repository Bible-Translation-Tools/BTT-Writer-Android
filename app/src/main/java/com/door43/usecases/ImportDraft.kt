package com.door43.usecases

import android.content.Context
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.Translator
import org.bibletranslationtools.resourcecontainer.ResourceContainer

class ImportDraft(
    private val context: Context,
    private val translator: Translator,
    private val profile: Profile
) {
    fun execute(
        draftTranslation: ResourceContainer,
        onProgress: (Float, String?) -> Unit = {_,_->}
    ): Result {
        onProgress(-1f, context.getString(R.string.importing_draft))

        val targetTranslation = translator.importDraftTranslation(
            profile.nativeSpeaker,
            draftTranslation
        )

        return Result(targetTranslation)
    }

    data class Result(val targetTranslation: TargetTranslation?)
}