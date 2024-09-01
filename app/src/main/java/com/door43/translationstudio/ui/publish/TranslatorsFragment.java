package com.door43.translationstudio.ui.publish;

import android.content.DialogInterface;
import android.os.Bundle;
import com.google.android.material.snackbar.Snackbar;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.door43.translationstudio.App;
import com.door43.translationstudio.R;
import com.door43.translationstudio.core.NativeSpeaker;
import com.door43.translationstudio.core.Profile;
import com.door43.translationstudio.core.TargetTranslation;
import com.door43.translationstudio.core.Translator;
import com.door43.translationstudio.ui.ContributorsAdapter;
import com.door43.translationstudio.ui.dialogs.ContributorDialog;
import com.door43.widget.ViewUtil;

/**
 * Created by joel on 9/20/2015.
 */
public class TranslatorsFragment extends PublishStepFragment implements ContributorsAdapter.OnClickListener {

    private TargetTranslation mTargetTranslation;
    private RecyclerView mRecylerView;
    private ContributorsAdapter mContributorsAdapter;
    private View.OnClickListener mOnNativeSpeakerDialogClick;

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View view = inflater.inflate(R.layout.fragment_contributors, container, false);

        Bundle args = getArguments();
        String targetTranslationId = args.getString(PublishActivity.EXTRA_TARGET_TRANSLATION_ID);
        Translator translator = App.getTranslator();
        mTargetTranslation = translator.getTargetTranslation(targetTranslationId);

        // auto add profile
        Profile profile = App.getProfile();
        if(profile != null) {
            mTargetTranslation.addContributor(profile.getNativeSpeaker());
        }

        mRecylerView = (RecyclerView)view.findViewById(R.id.recycler_view);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getActivity());
        mRecylerView.setLayoutManager(linearLayoutManager);
        mRecylerView.setItemAnimator(new DefaultItemAnimator());
        mContributorsAdapter = new ContributorsAdapter();
        mContributorsAdapter.setContributors(mTargetTranslation.getContributors());
        mContributorsAdapter.setOnClickListener(this);
        mRecylerView.setAdapter(mContributorsAdapter);

        mOnNativeSpeakerDialogClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mContributorsAdapter.setContributors(mTargetTranslation.getContributors());
            }
        };

        // re-attach to dialogs
        Fragment prevEditDialog = getParentFragmentManager().findFragmentByTag("edit-native-speaker");
        if(prevEditDialog != null) {
            ((ContributorDialog)prevEditDialog).setOnClickListener(mOnNativeSpeakerDialogClick);
        }
        Fragment prevAddDialog = getParentFragmentManager().findFragmentByTag("add-native-speaker");
        if(prevAddDialog != null) {
            ((ContributorDialog)prevAddDialog).setOnClickListener(mOnNativeSpeakerDialogClick);
        }
        return view;
    }

    @Override
    public void onEditNativeSpeaker(NativeSpeaker speaker) {
        FragmentTransaction ft = getParentFragmentManager().beginTransaction();
        Fragment prev = getParentFragmentManager().findFragmentByTag("edit-native-speaker");
        if(prev != null) {
            ft.remove(prev);
        }
        ft.addToBackStack(null);

        ContributorDialog dialog = new ContributorDialog();
        Bundle args = new Bundle();
        args.putString(ContributorDialog.ARG_TARGET_TRANSLATION, mTargetTranslation.getId());
        args.putString(ContributorDialog.ARG_NATIVE_SPEAKER, speaker.getName());
        dialog.setArguments(args);
        dialog.setOnClickListener(mOnNativeSpeakerDialogClick);
        dialog.show(ft, "edit-native-speaker");
    }

    @Override
    public void onClickAddNativeSpeaker() {
        showAddNativeSpeakerDialog();
    }

    @Override
    public void onClickNext() {
        if(!mTargetTranslation.getContributors().isEmpty()) {
            getListener().nextStep();
        } else {
            Snackbar snack = Snackbar.make(getActivity().findViewById(android.R.id.content), R.string.need_translator_notice, Snackbar.LENGTH_LONG);
            snack.setAction(R.string.add_contributor, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showAddNativeSpeakerDialog();
                }
            });
            ViewUtil.setSnackBarTextColor(snack, getResources().getColor(R.color.light_primary_text));
            snack.show();
        }
    }

    @Override
    public void onClickPrivacyNotice() {
        showPrivacyNotice(null);
    }

    public void showAddNativeSpeakerDialog() {
        FragmentTransaction ft = getParentFragmentManager().beginTransaction();
        Fragment prev = getParentFragmentManager().findFragmentByTag("add-native-speaker");
        if(prev != null) {
            ft.remove(prev);
        }
        ft.addToBackStack(null);

        ContributorDialog dialog = new ContributorDialog();
        Bundle args = new Bundle();
        args.putString(ContributorDialog.ARG_TARGET_TRANSLATION, mTargetTranslation.getId());
        dialog.setArguments(args);
        dialog.setOnClickListener(mOnNativeSpeakerDialogClick);
        dialog.show(ft, "add-native-speaker");
    }

    /**
     * Displays the privacy notice
     * @param listener if set the dialog will become a confirmation dialog
     */
    public void showPrivacyNotice(DialogInterface.OnClickListener listener) {

        if(listener != null) {
            new AlertDialog.Builder(getActivity(), R.style.AppTheme_Dialog)
                    .setTitle(R.string.privacy_notice)
                    .setIcon(R.drawable.ic_info_secondary_24dp)
                    .setMessage(R.string.publishing_privacy_notice)
                    .setPositiveButton(R.string.label_continue, listener)
                    .setNegativeButton(R.string.title_cancel, null)
                    .show();
        } else {
            new AlertDialog.Builder(getActivity(), R.style.AppTheme_Dialog)
                    .setTitle(R.string.privacy_notice)
                    .setIcon(R.drawable.ic_info_secondary_24dp)
                    .setMessage(R.string.publishing_privacy_notice)
                    .setPositiveButton(R.string.dismiss, null)
                    .show();
        }
    }
}
