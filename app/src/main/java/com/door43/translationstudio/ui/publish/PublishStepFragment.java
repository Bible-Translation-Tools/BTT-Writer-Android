package com.door43.translationstudio.ui.publish;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.door43.translationstudio.App;
import com.door43.translationstudio.core.TranslationViewMode;
import com.door43.translationstudio.core.Translator;
import com.door43.translationstudio.ui.BaseFragment;
import com.door43.translationstudio.ui.translate.TargetTranslationActivity;

/**
 * Created by joel on 9/20/2015.
 */
public abstract class PublishStepFragment extends BaseFragment {
    public static final String ARG_SOURCE_TRANSLATION_ID = "arg_source_translation_id";
    public static final String ARG_PUBLISH_FINISHED = "arg_publish_finished";
    private OnEventListener mListener;

    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            this.mListener = (OnEventListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement OnEventListener");
        }
    }

    /**
     * requests the parent activity to navigate to a new activity
     * @param targetTranslationId
     * @param chapterId
     * @param frameId
     */
    protected void openReview(String targetTranslationId, String chapterId, String frameId) {
        Intent intent = new Intent(getActivity(), TargetTranslationActivity.class);
        Bundle args = new Bundle();
        args.putString(Translator.EXTRA_TARGET_TRANSLATION_ID, targetTranslationId);
        args.putString(Translator.EXTRA_CHAPTER_ID, chapterId);
        args.putString(Translator.EXTRA_FRAME_ID, frameId);
        args.putInt(Translator.EXTRA_VIEW_MODE, TranslationViewMode.REVIEW.ordinal());
        intent.putExtras(args);
        startActivity(intent);
        getActivity().finish();
    }

    protected OnEventListener getListener() {
        return mListener;
    }

    public interface OnEventListener {
        void nextStep();

        void finishPublishing();
    }
}
