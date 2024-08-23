package com.door43.translationstudio.ui.translate;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import org.unfoldingword.door43client.Door43Client;
import org.unfoldingword.door43client.models.Translation;
import org.unfoldingword.resourcecontainer.Project;
import org.unfoldingword.tools.logger.Logger;

import com.door43.translationstudio.App;
import com.door43.translationstudio.R;
import com.door43.translationstudio.core.TargetTranslation;
import com.door43.translationstudio.core.Translator;
import com.door43.translationstudio.ui.BaseFragment;

import org.json.JSONException;

import java.security.InvalidParameterException;
import java.util.List;

/**
 * Gives some instructions when no source text has been selected
 */
public class FirstTabFragment extends BaseFragment implements ChooseSourceTranslationDialog.OnClickListener {

    private Translator mTranslator;
    private Door43Client mLibrary;
    private OnEventListener mListener;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_first_tab, container, false);

        mTranslator = App.getTranslator();
        mLibrary = App.getLibrary();

        Bundle args = getArguments();
        final String targetTranslationId = args.getString(App.EXTRA_TARGET_TRANSLATION_ID, null);
        TargetTranslation targetTranslation = mTranslator.getTargetTranslation(targetTranslationId);
        if(targetTranslation == null) {
            throw new InvalidParameterException("a valid target translation id is required");
        }

        ImageButton newTabButton = (ImageButton) rootView.findViewById(R.id.newTabButton);
        LinearLayout secondaryNewTabButton = (LinearLayout) rootView.findViewById(R.id.secondaryNewTabButton);
        TextView translationTitle = (TextView) rootView.findViewById(R.id.source_translation_title);
        try {
            Project p = mLibrary.index.getProject(App.getDeviceLanguageCode(), targetTranslation.getProjectId(), true);
            translationTitle.setText(p.name + " - " + targetTranslation.getTargetLanguageName());
        } catch (Exception e) {
            Logger.e(FirstTabFragment.class.getSimpleName(),"Error getting resource container for '" + targetTranslationId + "'", e);
        }

        View.OnClickListener clickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FragmentTransaction ft = getParentFragmentManager().beginTransaction();
                Fragment prev = getParentFragmentManager().findFragmentByTag("tabsDialog");
                if (prev != null) {
                    ft.remove(prev);
                }
                ft.addToBackStack(null);

                ChooseSourceTranslationDialog dialog = new ChooseSourceTranslationDialog();
                Bundle args = new Bundle();
                args.putString(ChooseSourceTranslationDialog.ARG_TARGET_TRANSLATION_ID, targetTranslationId);
                dialog.setOnClickListener(FirstTabFragment.this);
                dialog.setArguments(args);
                dialog.show(ft, "tabsDialog");
            }
        };

        newTabButton.setOnClickListener(clickListener);
        secondaryNewTabButton.setOnClickListener(clickListener);

        // attach to tabs dialog
        if(savedInstanceState != null) {
            ChooseSourceTranslationDialog dialog = (ChooseSourceTranslationDialog) getParentFragmentManager().findFragmentByTag("tabsDialog");
            if(dialog != null) {
                dialog.setOnClickListener(this);
            }
        }

        return rootView;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            this.mListener = (OnEventListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context + " must implement FirstTabFragment.OnEventListener");
        }
    }

    /**
     * user has selected to update sources
     */
    public void onUpdateSources() {
        if(mListener != null) mListener.onUpdateSources();
    }

    @Override
    public void onCancelTabsDialog(String targetTranslationId) {

    }

    @Override
    public void onConfirmTabsDialog(String targetTranslationId, List<String> sourceTranslationIds) {
        String[] oldSourceTranslationIds = App.getOpenSourceTranslations(targetTranslationId);
        for(String id:oldSourceTranslationIds) {
            App.removeOpenSourceTranslation(targetTranslationId, id);
        }

        if(!sourceTranslationIds.isEmpty()) {
            // save open source language tabs
            for(String slug:sourceTranslationIds) {
                Translation t = mLibrary.index().getTranslation(slug);
                int modifiedAt = mLibrary.getResourceContainerLastModified(t.language.slug, t.project.slug, t.resource.slug);
                try {
                    App.addOpenSourceTranslation(targetTranslationId, slug);
                    TargetTranslation targetTranslation = mTranslator.getTargetTranslation(targetTranslationId);
                    if (targetTranslation != null) {
                        try {
                            targetTranslation.addSourceTranslation(t, modifiedAt);
                        } catch (JSONException e) {
                            Logger.e(this.getClass().getName(), "Failed to record source translation (" + slug + ") usage in the target translation " + targetTranslation.getId(), e);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            // redirect back to previous mode
            if(mListener != null) mListener.onHasSourceTranslations();
        }
    }

    public interface OnEventListener {
        void onHasSourceTranslations();

        /**
         * user has selected to update sources
         */
        void onUpdateSources();
    }
}
