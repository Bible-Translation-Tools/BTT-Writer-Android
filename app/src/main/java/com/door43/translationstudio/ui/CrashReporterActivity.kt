package com.door43.translationstudio.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.ViewModelProvider;

import com.door43.translationstudio.App;
import com.door43.translationstudio.R;
import com.door43.translationstudio.databinding.ActivityCrashReporterBinding;
import com.door43.translationstudio.ui.dialogs.ProgressHelper;
import com.door43.translationstudio.ui.viewmodels.CrashReporterViewModel;
import com.door43.usecases.CheckForLatestRelease;

import org.unfoldingword.tools.logger.Logger;

public class CrashReporterActivity extends BaseActivity {
    private static final String STATE_LATEST_RELEASE = "state_latest_release";
    private static final String STATE_NOTES = "state_notes";

    private ProgressHelper.ProgressDialog progressDialog;
    private String mNotes = "";
    private CheckForLatestRelease.Release latestRelease = null;

    private ActivityCrashReporterBinding binding;
    private CrashReporterViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCrashReporterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(CrashReporterViewModel.class);

        progressDialog = ProgressHelper.newInstance(this, R.string.loading, false);

        binding.okButton.setOnClickListener(view -> {
            mNotes = binding.crashDescription.getText().toString().trim();

            new AlertDialog.Builder(CrashReporterActivity.this, R.style.AppTheme_Dialog)
                    .setTitle(R.string.title_upload)
                    .setMessage(R.string.use_internet_confirmation)
                    .setPositiveButton(R.string.label_continue, (dialog, which) -> {
                        progressDialog.setMessage(getResources().getString(R.string.loading));
                        progressDialog.show();

                        viewModel.checkForLatestRelease();
                    })
                    .setNegativeButton(R.string.label_close, (dialog, which) -> {
                        Logger.flush();
                        openSplash();
                    })
                    .show();
        });

        binding.cancelButton.setOnClickListener(view -> {
            Logger.flush();
            openSplash();
        });

        setupObservers();
    }

    private void setupObservers() {
        viewModel.getProgress().observe(this, progress -> {
            if (progress != null) {
                progressDialog.show();
                progressDialog.setProgress(progress.getProgress());
                progressDialog.setMessage(progress.getMessage());
                progressDialog.setMax(progress.getMax());
            } else {
                progressDialog.dismiss();
            }
        });
        viewModel.getLatestRelease().observe(this, result -> {
            if (result != null) {
                Handler hand = new Handler(Looper.getMainLooper());
                if(result.getRelease() != null) {
                    // ask user if they would like to download updates
                    latestRelease = result.getRelease();
                    hand.post(this::notifyLatestRelease);
                } else {
                    // upload crash report
                    hand.post(() -> {
                        progressDialog.setMessage(getResources().getString(R.string.uploading));
                        progressDialog.show();
                    });

                    String report = binding.crashDescription.getText().toString().trim();
                    viewModel.uploadCrashReport(report);
                }
            }
        });
        viewModel.getCrashReportUploaded().observe(this, uploaded -> {
           if (uploaded != null) {
               Logger.i(this.getClass().getSimpleName(),"UploadCrashReportTask success:" + uploaded);
               if(uploaded) {
                   openSplash();
               } else { // upload failed
                   final boolean networkAvailable = App.isNetworkAvailable();

                   Handler hand = new Handler(Looper.getMainLooper());
                   hand.post(() -> {
                       int messageId = networkAvailable ? R.string.upload_crash_report_failed : R.string.internet_not_available;
                       new AlertDialog.Builder(CrashReporterActivity.this, R.style.AppTheme_Dialog)
                               .setTitle(R.string.upload_failed)
                               .setMessage(messageId)
                               .setPositiveButton(R.string.label_ok, null)
                               .show();
                   });
               }
           }
        });
    }

    public void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mNotes = savedInstanceState.getString(STATE_NOTES, "");
        latestRelease = (CheckForLatestRelease.Release)savedInstanceState.getSerializable(STATE_LATEST_RELEASE);
        binding.crashDescription.setText(mNotes);
        super.onRestoreInstanceState(savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        if(latestRelease != null) {
            notifyLatestRelease();
        }
    }

    /**
     * Displays a dialog to the user telling them there is an apk update.
     */
    private void notifyLatestRelease() {
        new AlertDialog.Builder(this, R.style.AppTheme_Dialog)
                .setTitle(R.string.apk_update_available)
                .setMessage(R.string.upload_report_or_download_latest_apk)
                .setNegativeButton(R.string.title_cancel, (dialog, which) -> {
                    latestRelease = null;
                    Logger.flush();
                    openSplash();
                })
                .setNeutralButton(R.string.download_update, (dialog, which) -> {
                    Logger.flush();
                    viewModel.downloadLatestRelease(latestRelease);
                    finish();
                })
                .setPositiveButton(R.string.label_continue, (dialog, which) -> {
                    progressDialog.setMessage(getResources().getString(R.string.uploading));
                    progressDialog.show();
                    viewModel.uploadCrashReport(mNotes);
                })
                .show();
    }

    private void openSplash() {
        Intent intent = new Intent(CrashReporterActivity.this, SplashScreenActivity.class);
        startActivity(intent);
        finish();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        if(latestRelease != null) {
            outState.putSerializable(STATE_LATEST_RELEASE, latestRelease);
        } else {
            outState.remove(STATE_LATEST_RELEASE);
        }
        outState.putString(STATE_NOTES, binding.crashDescription.getText().toString().trim());
        super.onSaveInstanceState(outState);
    }
}
