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
import androidx.lifecycle.ViewModelProvider;

import org.unfoldingword.door43client.models.Translation;
import org.unfoldingword.resourcecontainer.Project;
import org.unfoldingword.tools.logger.Logger;

import com.door43.translationstudio.R;
import com.door43.translationstudio.core.TargetTranslation;
import com.door43.translationstudio.ui.BaseFragment;
import com.door43.translationstudio.ui.viewmodels.TargetTranslationViewModel;

import org.json.JSONException;

import java.util.List;

/**
 * Gives some instructions when no source text has been selected
 */
public class FirstTabFragment extends BaseFragment implements ChooseSourceTranslationDialog.OnClickListener {

    private OnEventListener mListener;
    protected TargetTranslationViewModel viewModel;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_first_tab, container, false);
        viewModel = new ViewModelProvider(requireActivity()).get(TargetTranslationViewModel.class);

        Bundle args = getArguments();
        assert args != null;

        ImageButton newTabButton = rootView.findViewById(R.id.newTabButton);
        LinearLayout secondaryNewTabButton = rootView.findViewById(R.id.secondaryNewTabButton);
        TextView translationTitle = rootView.findViewById(R.id.source_translation_title);
        try {
            Project p = viewModel.getProject();
            translationTitle.setText(p.name + " - " + viewModel.getTargetTranslation().getTargetLanguageName());
        } catch (Exception e) {
            Logger.e(
                FirstTabFragment.class.getSimpleName(),
                "Error getting resource container for '" + viewModel.getTargetTranslation().getId() + "'",
                e
            );
        }

        View.OnClickListener clickListener = v -> {
            FragmentTransaction ft = getParentFragmentManager().beginTransaction();
            Fragment prev = getParentFragmentManager().findFragmentByTag("tabsDialog");
            if (prev != null) {
                ft.remove(prev);
            }
            ft.addToBackStack(null);

            ChooseSourceTranslationDialog dialog = new ChooseSourceTranslationDialog();
            Bundle args1 = new Bundle();
            args1.putString(ChooseSourceTranslationDialog.ARG_TARGET_TRANSLATION_ID, viewModel.getTargetTranslation().getId());
            dialog.setOnClickListener(FirstTabFragment.this);
            dialog.setArguments(args1);
            dialog.show(ft, "tabsDialog");
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
    public void onConfirmTabsDialog(List<String> sourceTranslationIds) {
        String[] oldSourceTranslationIds = viewModel.getOpenSourceTranslations();
        for(String id:oldSourceTranslationIds) {
            viewModel.removeOpenSourceTranslation(id);
        }

        if(!sourceTranslationIds.isEmpty()) {
            // save open source language tabs
            for(String slug:sourceTranslationIds) {
                Translation t = viewModel.getTranslation(slug);
                int modifiedAt = viewModel.getResourceContainerLastModified(t);
                try {
                    viewModel.addOpenSourceTranslation(slug);
                    TargetTranslation targetTranslation = viewModel.getTargetTranslation();
                    try {
                        targetTranslation.addSourceTranslation(t, modifiedAt);
                    } catch (JSONException e) {
                        Logger.e(this.getClass().getName(), "Failed to record source translation (" + slug + ") usage in the target translation " + targetTranslation.getId(), e);
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
