package com.door43.translationstudio.ui.translate;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import org.unfoldingword.door43client.models.Translation;
import org.unfoldingword.resourcecontainer.Project;
import org.unfoldingword.tools.logger.Logger;

import com.door43.translationstudio.core.TargetTranslation;
import com.door43.translationstudio.databinding.FragmentFirstTabBinding;
import com.door43.translationstudio.ui.BaseFragment;
import com.door43.translationstudio.ui.viewmodels.TargetTranslationViewModel;

import org.json.JSONException;

import java.util.List;

/**
 * Gives some instructions when no source text has been selected
 */
public class FirstTabFragment extends BaseFragment implements ChooseSourceTranslationDialog.OnClickListener {

    private OnEventListener listener;
    protected TargetTranslationViewModel viewModel;

    private FragmentFirstTabBinding binding;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentFirstTabBinding.inflate(inflater, container, false);
        viewModel = new ViewModelProvider(requireActivity()).get(TargetTranslationViewModel.class);

        Bundle args = getArguments();
        assert args != null;

        setupObservers();

        try {
            Project p = viewModel.getProject();
            binding.sourceTranslationTitle.setText(
                    p.name + " - " + viewModel.getTargetTranslation().getTargetLanguageName()
            );
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

        binding.newTabButton.setOnClickListener(clickListener);
        binding.secondaryNewTabButton.setOnClickListener(clickListener);

        // attach to tabs dialog
        if(savedInstanceState != null) {
            ChooseSourceTranslationDialog dialog = (ChooseSourceTranslationDialog) getParentFragmentManager().findFragmentByTag("tabsDialog");
            if(dialog != null) {
                dialog.setOnClickListener(this);
            }
        }

        return binding.getRoot();
    }

    private void setupObservers() {
        viewModel.getListItems().observe(getViewLifecycleOwner(), items -> {
            if (!items.isEmpty() && listener != null) {
                listener.onHasSourceTranslations();
            }
        });
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            this.listener = (OnEventListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context + " must implement FirstTabFragment.OnEventListener");
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    /**
     * user has selected to update sources
     */
    public void onUpdateSources() {
        if(listener != null) listener.onUpdateSources();
    }

    @Override
    public void onCancelTabsDialog(String targetTranslationId) {

    }

    @Override
    public void onConfirmTabsDialog(@NonNull List<String> sourceTranslationIds) {
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
            if(listener != null) listener.onHasSourceTranslations();
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
