package com.door43.translationstudio.ui.publish;

import android.content.Intent;
import android.os.Bundle;

import com.door43.translationstudio.databinding.ActivityPublishBinding;
import com.door43.translationstudio.ui.viewmodels.TargetTranslationViewModel;
import com.google.android.material.snackbar.Snackbar;

import androidx.activity.OnBackPressedCallback;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

import android.view.MenuItem;
import android.widget.Button;

import com.door43.translationstudio.R;
import com.door43.translationstudio.core.TargetTranslation;
import com.door43.translationstudio.core.Translator;
import com.door43.translationstudio.ui.dialogs.BackupDialog;
import com.door43.translationstudio.ui.BaseActivity;
import com.door43.translationstudio.ui.translate.TargetTranslationActivity;
import com.door43.widget.ViewUtil;

import java.security.InvalidParameterException;
import org.unfoldingword.tools.logger.Logger;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class PublishActivity extends BaseActivity implements PublishStepFragment.OnEventListener {

    public static final int STEP_VALIDATE = 0;
    public static final int STEP_PROFILE = 1;
    public static final String EXTRA_TARGET_TRANSLATION_ID = "extra_target_translation_id";
    public static final String EXTRA_CALLING_ACTIVITY = "extra_calling_activity";
    private static final String STATE_STEP = "state_step";
    private static final String STATE_PUBLISH_FINISHED = "state_publish_finished";
    public static final int ACTIVITY_HOME = 1001;
    public static final int ACTIVITY_TRANSLATION = 1002;

    private PublishStepFragment fragment;
    private int currentStep = 0;
    private boolean publishFinished = false;
    private int callingActivity;

    private TargetTranslationViewModel viewModel;
    private ActivityPublishBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityPublishBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        viewModel = new ViewModelProvider(this).get(TargetTranslationViewModel.class);

        // validate parameters
        Bundle args = getIntent().getExtras();
        assert args != null;

        String targetTranslationId = args.getString(Translator.EXTRA_TARGET_TRANSLATION_ID, null);
        TargetTranslation translation = viewModel.getTargetTranslation(targetTranslationId);
        if (translation == null) {
            Logger.e(
                    PublishActivity.class.getSimpleName(),
                    "A valid target translation id is required. Received " + targetTranslationId + " but the translation could not be found"
            );
            finish();
            return;
        }

        // identify calling activity
        callingActivity = args.getInt(EXTRA_CALLING_ACTIVITY, 0);
        if(callingActivity == 0) {
            throw new InvalidParameterException("you must specify the calling activity");
        }

        // stage indicators

        if(savedInstanceState != null) {
            currentStep = savedInstanceState.getInt(STATE_STEP, 0);
            publishFinished = savedInstanceState.getBoolean(STATE_PUBLISH_FINISHED, false);
        }

        // inject fragments
        if(findViewById(R.id.fragment_container) != null) {
            if(savedInstanceState != null) {
                fragment = (PublishStepFragment)getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            } else {
                fragment = new ValidationFragment();
                String sourceTranslationId = viewModel.getSelectedSourceTranslationId();

                if(sourceTranslationId == null) {
                    // use the default target translation if they have not chosen one.
                    sourceTranslationId = viewModel.getDefaultSourceTranslation();
                }
                if(sourceTranslationId != null) {
                    args.putSerializable(PublishStepFragment.ARG_SOURCE_TRANSLATION_ID, sourceTranslationId);
                    fragment.setArguments(args);
                    getSupportFragmentManager().beginTransaction().add(R.id.fragment_container, fragment).commit();
                    // TODO: animate
                } else {
                    // the user must choose a source translation before they can publish
                    Snackbar snack = Snackbar.make(findViewById(android.R.id.content), R.string.choose_source_translations, Snackbar.LENGTH_LONG);
                    ViewUtil.setSnackBarTextColor(snack, getResources().getColor(R.color.light_primary_text));
                    snack.show();
                    finish();
                }
            }
        }

        selectButtonForCurrentStep();

        // add step button listeners

        binding.validationButton.setOnClickListener(v -> goToStep(STEP_VALIDATE, false));

        binding.profileButton.setOnClickListener(v -> goToStep(STEP_PROFILE, false));

        binding.uploadButton.setOnClickListener(v -> showBackupDialog());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                onBackPressedHandler();
            }
        });
    }

    /**
     * display Backup dialog
     */
    private void showBackupDialog() {
        FragmentTransaction backupFt = getSupportFragmentManager().beginTransaction();
        Fragment backupPrev = getSupportFragmentManager().findFragmentByTag(BackupDialog.TAG);
        if (backupPrev != null) {
            backupFt.remove(backupPrev);
        }
        backupFt.addToBackStack(null);

        BackupDialog backupDialog = new BackupDialog();
        Bundle args = new Bundle();
        args.putString(BackupDialog.ARG_TARGET_TRANSLATION_ID, viewModel.getTargetTranslation().getId());
        backupDialog.setArguments(args);
        backupDialog.show(backupFt, BackupDialog.TAG);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId() == android.R.id.home) {
            if(callingActivity == ACTIVITY_TRANSLATION) {
                // TRICKY: the translation activity is finished after opening the publish activity
                // because we may have to go back and forth and don't want to fill up the stack
                Intent intent = new Intent(this, TargetTranslationActivity.class);
                Bundle args = new Bundle();
                args.putString(Translator.EXTRA_TARGET_TRANSLATION_ID, viewModel.getTargetTranslation().getId());
                intent.putExtras(args);
                startActivity(intent);
            }
            finish();
        }
        return true;
    }

    public void onBackPressedHandler() {
        // TRICKY: the translation activity is finished after opening the publish activity
        // because we may have to go back and forth and don't want to fill up the stack
        if(callingActivity == ACTIVITY_TRANSLATION) {
            Intent intent = new Intent(this, TargetTranslationActivity.class);
            Bundle args = new Bundle();
            args.putString(Translator.EXTRA_TARGET_TRANSLATION_ID, viewModel.getTargetTranslation().getId());
            intent.putExtras(args);
            startActivity(intent);
        }
        finish();
    }


    @Override
    public void nextStep() {
        goToStep(currentStep + 1, true);
    }

    @Override
    public void finishPublishing() {
        publishFinished = true;
    }

    /**
     * Moves to the a stage in the publish process
     * @param step
     * @param force forces the step to be opened even if it has never been opened before
     */
    private void goToStep(int step, boolean force) {
        if( step == currentStep) {
            return;
        }

        if(step > STEP_PROFILE) { // if we are ready to upload
            currentStep = STEP_PROFILE;
            showBackupDialog();
            return;
        } else {
            currentStep = step;
        }

        switch(currentStep) {
            case STEP_PROFILE:
                fragment = new TranslatorsFragment();
                break;
            case STEP_VALIDATE:
            default:
                currentStep = STEP_VALIDATE;
                fragment = new ValidationFragment();
                break;
        }

        selectButtonForCurrentStep();

        Bundle args = getIntent().getExtras();
        assert args != null;

        String sourceTranslationId = viewModel.getSelectedSourceTranslationId();
        // TRICKY: if the user has not chosen a source translation (this is an empty translation) the id will be null
        if(sourceTranslationId == null) {
            sourceTranslationId = viewModel.getDefaultSourceTranslation();
        }
        if (sourceTranslationId != null) {
            args.putSerializable(PublishStepFragment.ARG_SOURCE_TRANSLATION_ID, sourceTranslationId);
            args.putBoolean(PublishStepFragment.ARG_PUBLISH_FINISHED, publishFinished);
            fragment.setArguments(args);
            getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, fragment).commit();
            // TODO: animate
        }
    }

    /**
     * select the button for current state
     */
    private void selectButtonForCurrentStep() {
        setButtonSelectionState(binding.validationButton, currentStep == STEP_VALIDATE);
        setButtonSelectionState(binding.profileButton, currentStep == STEP_PROFILE);
    }

    /**
     * show which fragment is selected by changeing background color
     * @param button
     * @param selected
     */
    private void setButtonSelectionState(Button button, boolean selected) {
        if(button != null) {
            int newResource = R.color.accent;
            if (selected) {
                newResource = R.color.accent_dark;
            }
            button.setBackgroundResource(newResource);
        }
    }

    public void onSaveInstanceState(Bundle out) {
        out.putInt(STATE_STEP, currentStep);
        out.putBoolean(STATE_PUBLISH_FINISHED, publishFinished);

        super.onSaveInstanceState(out);
    }
}
