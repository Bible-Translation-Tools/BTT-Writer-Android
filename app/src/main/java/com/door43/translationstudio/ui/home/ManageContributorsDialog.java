package com.door43.translationstudio.ui.home;

import android.content.DialogInterface;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.door43.translationstudio.R;
import com.door43.translationstudio.core.NativeSpeaker;
import com.door43.translationstudio.core.Profile;
import com.door43.translationstudio.core.TargetTranslation;
import com.door43.translationstudio.core.Translator;
import com.door43.translationstudio.ui.ContributorsAdapter;
import com.door43.translationstudio.ui.dialogs.ContributorDialog;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Created by joel on 2/22/2016.
 */
@AndroidEntryPoint
public class ManageContributorsDialog extends DialogFragment implements ContributorsAdapter.OnClickListener  {
    @Inject
    Translator translator;
    @Inject
    Profile profile;

    public static final String EXTRA_TARGET_TRANSLATION_ID = "target_translation_id";
    private TargetTranslation targetTranslation;
    private RecyclerView recyclerView;
    private ContributorsAdapter contributorsAdapter;
    private View.OnClickListener onNativeSpeakerDialogClick;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NO_TITLE, 0);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Bundle args = getArguments();
        View view = inflater.inflate(R.layout.fragment_contributors, container, false);

        String targetTranslationId = args.getString(ManageContributorsDialog.EXTRA_TARGET_TRANSLATION_ID);

        targetTranslation = translator.getTargetTranslation(targetTranslationId);

//         auto add profile
        targetTranslation.addContributor(profile.getNativeSpeaker());

        recyclerView = (RecyclerView)view.findViewById(R.id.recycler_view);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getActivity());
        recyclerView.setLayoutManager(linearLayoutManager);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        contributorsAdapter = new ContributorsAdapter();
        contributorsAdapter.setDisplayNext(false);
        contributorsAdapter.setContributors(targetTranslation.getContributors());
        contributorsAdapter.setOnClickListener(this);
        recyclerView.setAdapter(contributorsAdapter);

        onNativeSpeakerDialogClick = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                contributorsAdapter.setContributors(targetTranslation.getContributors());
            }
        };

        // re-attach to dialogs
        Fragment prevEditDialog = getParentFragmentManager().findFragmentByTag("edit-native-speaker");
        if(prevEditDialog != null) {
            ((ContributorDialog)prevEditDialog).setOnClickListener(onNativeSpeakerDialogClick);
        }
        Fragment prevAddDialog = getParentFragmentManager().findFragmentByTag("add-native-speaker");
        if(prevAddDialog != null) {
            ((ContributorDialog)prevAddDialog).setOnClickListener(onNativeSpeakerDialogClick);
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
        args.putString(ContributorDialog.ARG_TARGET_TRANSLATION, targetTranslation.getId());
        args.putString(ContributorDialog.ARG_NATIVE_SPEAKER, speaker.getName());
        dialog.setArguments(args);
        dialog.setOnClickListener(onNativeSpeakerDialogClick);
        dialog.show(ft, "edit-native-speaker");
    }

    @Override
    public void onClickAddNativeSpeaker() {
        showAddNativeSpeakerDialog();
    }

    @Override
    public void onClickNext() {

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
        args.putString(ContributorDialog.ARG_TARGET_TRANSLATION, targetTranslation.getId());
        dialog.setArguments(args);
        dialog.setOnClickListener(onNativeSpeakerDialogClick);
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
