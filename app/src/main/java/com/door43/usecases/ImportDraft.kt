package com.door43.usecases

import android.content.Context
import com.door43.OnProgressListener
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.Translator
import dagger.hilt.android.qualifiers.ApplicationContext
import org.unfoldingword.resourcecontainer.ResourceContainer
import javax.inject.Inject

class ImportDraft @Inject constructor(
    @ApplicationContext private val context: Context,
    private val translator: Translator,
    private val profile: Profile
) {
    fun execute(
        draftTranslation: ResourceContainer,
        progressListener: OnProgressListener? = null
    ): Result {
        progressListener?.onProgress(-1, 100, context.getString(R.string.importing_draft))

        val targetTranslation = translator.importDraftTranslation(
            profile.nativeSpeaker,
            draftTranslation
        )

        return Result(targetTranslation)
    }

    data class Result(val targetTranslation: TargetTranslation?)
}