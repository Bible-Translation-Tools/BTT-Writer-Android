package com.door43.translationstudio.ui.publish

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.door43.translationstudio.core.TranslationViewMode
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.ui.BaseFragment
import com.door43.translationstudio.ui.translate.TargetTranslationActivity

/**
 * Created by joel on 9/20/2015.
 */
abstract class PublishStepFragment : BaseFragment() {
    protected var listener: OnEventListener? = null
        private set

    override fun onAttach(context: Context) {
        super.onAttach(context)
        try {
            this.listener = context as OnEventListener
        } catch (e: ClassCastException) {
            throw ClassCastException("$context must implement OnEventListener")
        }
    }

    /**
     * requests the parent activity to navigate to a new activity
     * @param targetTranslationId
     * @param chapterId
     * @param frameId
     */
    protected fun openReview(targetTranslationId: String, chapterId: String, frameId: String) {
        val intent = Intent(activity, TargetTranslationActivity::class.java)
        val args = Bundle()
        args.putString(Translator.EXTRA_TARGET_TRANSLATION_ID, targetTranslationId)
        args.putString(Translator.EXTRA_CHAPTER_ID, chapterId)
        args.putString(Translator.EXTRA_FRAME_ID, frameId)
        args.putInt(Translator.EXTRA_VIEW_MODE, TranslationViewMode.REVIEW.ordinal)
        intent.putExtras(args)

        startActivity(intent)
        requireActivity().finish()
    }

    interface OnEventListener {
        fun nextStep()
        fun finishPublishing()
    }

    companion object {
        const val ARG_SOURCE_TRANSLATION_ID: String = "arg_source_translation_id"
        const val ARG_PUBLISH_FINISHED: String = "arg_publish_finished"
    }
}
