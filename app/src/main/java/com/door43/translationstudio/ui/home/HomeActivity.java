package com.door43.translationstudio.ui.home;

import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Nullable;

import com.door43.translationstudio.tasks.DownloadIndexTask;
import com.door43.translationstudio.tasks.LogoutTask;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import android.os.Bundle;
import androidx.core.content.FileProvider;
import androidx.appcompat.app.AlertDialog;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;

import org.eclipse.jgit.merge.MergeStrategy;
import org.unfoldingword.door43client.Door43Client;
import org.unfoldingword.door43client.models.TargetLanguage;
import org.unfoldingword.door43client.models.Translation;
import org.unfoldingword.gogsclient.User;
import org.unfoldingword.resourcecontainer.Project;
import org.unfoldingword.tools.eventbuffer.EventBuffer;
import org.unfoldingword.tools.logger.Logger;

import com.door43.translationstudio.App;
import com.door43.translationstudio.core.MergeConflictsHandler;
import com.door43.translationstudio.tasks.CheckForLatestReleaseTask;
import com.door43.translationstudio.tasks.GetAvailableSourcesTask;
import com.door43.translationstudio.ui.dialogs.DownloadSourcesDialog;
import com.door43.translationstudio.ui.ProfileActivity;
import com.door43.translationstudio.R;
import com.door43.translationstudio.ui.SettingsActivity;

import com.door43.translationstudio.core.TargetTranslation;
import com.door43.translationstudio.core.TranslationViewMode;
import com.door43.translationstudio.core.Translator;
import com.door43.translationstudio.ui.dialogs.Door43LoginDialog;
import com.door43.translationstudio.ui.BaseActivity;
import com.door43.translationstudio.ui.newtranslation.NewTargetTranslationActivity;
import com.door43.translationstudio.ui.dialogs.FeedbackDialog;
import com.door43.translationstudio.ui.translate.TargetTranslationActivity;
import com.door43.translationstudio.tasks.ExamineImportsForCollisionsTask;
import com.door43.translationstudio.tasks.ImportProjectsTask;
import org.unfoldingword.tools.taskmanager.SimpleTaskWatcher;
import org.unfoldingword.tools.taskmanager.ManagedTask;
import org.unfoldingword.tools.taskmanager.TaskManager;

import com.door43.translationstudio.tasks.PullTargetTranslationTask;
import com.door43.translationstudio.tasks.RegisterSSHKeysTask;
import com.door43.translationstudio.tasks.UpdateAllTask;
import com.door43.translationstudio.tasks.UpdateCatalogsTask;
import com.door43.translationstudio.tasks.UpdateSourceTask;
import com.door43.util.FileUtilities;
import com.door43.widget.ViewUtil;


import java.io.File;
import java.text.NumberFormat;
import java.util.List;

public class HomeActivity extends BaseActivity implements SimpleTaskWatcher.OnFinishedListener, WelcomeFragment.OnCreateNewTargetTranslation, TargetTranslationListFragment.OnItemClickListener, EventBuffer.OnEventListener, ManagedTask.OnProgressListener, ManagedTask.OnFinishedListener, DialogInterface.OnCancelListener {
    private static final int NEW_TARGET_TRANSLATION_REQUEST = 1;
    private static final int TARGET_TRANSLATION_VIEW_REQUEST = 101;
    public static final String STATE_DIALOG_SHOWN = "state_dialog_shown";
    public static final String STATE_DIALOG_TRANSLATION_ID = "state_dialog_translationID";
    public static final String TAG = HomeActivity.class.getSimpleName();
    public static final int INVALID = -1;
    private Door43Client mLibrary;
    private Translator mTranslator;
    private Fragment mFragment;
    private SimpleTaskWatcher taskWatcher;
    private ExamineImportsForCollisionsTask mExamineTask;
    private DialogShown mAlertShown = DialogShown.NONE;
    private String mTargetTranslationWithUpdates;
    private String mTargetTranslationID;
    private ProgressDialog progressDialog = null;
    private UpdateLibraryDialog mUpdateDialog = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        App.startBackupService();

        taskWatcher = new SimpleTaskWatcher(this, R.string.loading);
        taskWatcher.setOnFinishedListener(this);

        FloatingActionButton addTranslationButton = (FloatingActionButton) findViewById(R.id.addTargetTranslationButton);
        addTranslationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onCreateNewTargetTranslation();
            }
        });

        mLibrary = App.getLibrary();
        mTranslator = App.getTranslator();

        if(findViewById(R.id.fragment_container) != null) {
            if(savedInstanceState != null) {
                // use current fragment
                mFragment = getFragmentManager().findFragmentById(R.id.fragment_container);
            } else {
                if (mTranslator.getTargetTranslationIDs().length > 0) {
                    mFragment = new TargetTranslationListFragment();
                    mFragment.setArguments(getIntent().getExtras());
                } else {
                    mFragment = new WelcomeFragment();
                    mFragment.setArguments(getIntent().getExtras());
                }

                getFragmentManager().beginTransaction().add(R.id.fragment_container, mFragment).commit();
            }
        }

        Button logout = (Button) findViewById(R.id.logout_button);
        logout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doLogout();
            }
        });

        ImageButton moreButton = (ImageButton)findViewById(R.id.action_more);
        moreButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PopupMenu moreMenu = new PopupMenu(HomeActivity.this, v);
                ViewUtil.forcePopupMenuIcons(moreMenu);
                moreMenu.getMenuInflater().inflate(R.menu.menu_home, moreMenu.getMenu());
                moreMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        switch (item.getItemId()) {
                            case R.id.action_update:
                                mUpdateDialog = new UpdateLibraryDialog();
                                showDialogFragment(mUpdateDialog, UpdateLibraryDialog.TAG);
                                return true;
                            case R.id.action_import:
                                ImportDialog importDialog = new ImportDialog();
                                showDialogFragment(importDialog, ImportDialog.TAG);
                                return true;
                            case R.id.action_feedback:
                                FeedbackDialog dialog = new FeedbackDialog();
                                showDialogFragment(dialog, "feedback-dialog");
                                return true;
                            case R.id.action_share_apk:
                                try {
                                    PackageInfo pinfo = getPackageManager().getPackageInfo(getPackageName(), 0);
                                    File apkFile = new File(pinfo.applicationInfo.publicSourceDir);
                                    File exportFile = new File(App.getSharingDir(), pinfo.applicationInfo.loadLabel(getPackageManager()) + "_" + pinfo.versionName + ".apk");
                                    FileUtilities.copyFile(apkFile, exportFile);
                                    if (exportFile.exists()) {
                                        Uri u = FileProvider.getUriForFile(HomeActivity.this, "com.door43.translationstudio.fileprovider", exportFile);
                                        Intent i = new Intent(Intent.ACTION_SEND);
                                        i.setType("application/zip");
                                        i.putExtra(Intent.EXTRA_STREAM, u);
                                        startActivity(Intent.createChooser(i, getResources().getString(R.string.send_to)));
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    // todo notify user app could not be shared
                                }
                                return true;
                            case R.id.action_log_out:
                                doLogout();
                                return true;
                            case R.id.action_settings:
                                Intent intent = new Intent(HomeActivity.this, SettingsActivity.class);
                                startActivity(intent);
                                return true;
                        }
                        return false;
                    }
                });
                moreMenu.show();
            }
        });

        Intent intent = getIntent(); // check if user is trying to open a tstudio file
        if(intent != null) {
            String action = intent.getAction();
            if(action != null) {
                if (action.compareTo(Intent.ACTION_VIEW) == 0 || action.compareTo(Intent.ACTION_DEFAULT) == 0) {
                    String scheme = intent.getScheme();
                    ContentResolver resolver = getContentResolver();
                    Uri contentUri = intent.getData();
                    Logger.i(TAG,"Opening: " + contentUri.toString());
                    if (scheme.compareTo(ContentResolver.SCHEME_CONTENT) == 0) {
                        importFromUri(resolver, contentUri);
                        return;
                    } else if (scheme.compareTo(ContentResolver.SCHEME_FILE) == 0) {
                        importFromUri(resolver, contentUri);
                        return;
                    }
                }
            }
        }

        // open last project when starting the first time
        if (savedInstanceState == null) {
            TargetTranslation targetTranslation = getLastOpened();
            if (targetTranslation != null) {
                onItemClick(targetTranslation);
                return;
            }
        } else {
            mAlertShown = DialogShown.fromInt(savedInstanceState.getInt(STATE_DIALOG_SHOWN, INVALID), DialogShown.NONE);
            mTargetTranslationID = savedInstanceState.getString(STATE_DIALOG_TRANSLATION_ID, null);
        }
    }

    /**
     * do logout activitity
     */
    private void doLogout() {
        User user = App.getProfile().gogsUser;
        LogoutTask task = new LogoutTask(user);
        TaskManager.addTask(task, LogoutTask.TASK_ID);
        task.addOnFinishedListener(this);

        App.setProfile(null);
        Intent logoutIntent = new Intent(HomeActivity.this, ProfileActivity.class);
        startActivity(logoutIntent);
        finish();
    }

    @Override
    public void onResume() {
        super.onResume();
        App.setLastFocusTargetTranslation(null);

        TextView currentUser = (TextView) findViewById(R.id.current_user);
        String userText = getResources().getString(R.string.current_user, ProfileActivity.getCurrentUser());
        currentUser.setText(userText);

        int numTranslations = mTranslator.getTargetTranslationIDs().length;
        if(numTranslations > 0 && mFragment instanceof WelcomeFragment) {
            // display target translations list
            mFragment = new TargetTranslationListFragment();
            mFragment.setArguments(getIntent().getExtras());
            getFragmentManager().beginTransaction().replace(R.id.fragment_container, mFragment).commit();

            Handler hand = new Handler(Looper.getMainLooper());
            hand.post(new Runnable() {
                @Override
                public void run() {
                    // delay to load list after fragment initializes
                    ((TargetTranslationListFragment) mFragment).reloadList();
                }
            });

        } else if(numTranslations == 0 && mFragment instanceof TargetTranslationListFragment) {
            // display welcome screen
            mFragment = new WelcomeFragment();
            mFragment.setArguments(getIntent().getExtras());
            getFragmentManager().beginTransaction().replace(R.id.fragment_container, mFragment).commit();
        } else if(numTranslations > 0 && mFragment instanceof TargetTranslationListFragment) {
            // reload list
            ((TargetTranslationListFragment)mFragment).reloadList();
        }

        // re-connect to tasks
        ManagedTask task = TaskManager.getTask(PullTargetTranslationTask.TASK_ID);
        if(task != null) {
            taskWatcher.watch(task);
        }
        task = TaskManager.getTask(UpdateAllTask.TASK_ID);
        if(task != null) {
            task.addOnProgressListener(this);
            task.addOnFinishedListener(this);
        }
        task = TaskManager.getTask(UpdateSourceTask.TASK_ID);
        if(task != null) {
            task.addOnProgressListener(this);
            task.addOnFinishedListener(this);
        }
        task = TaskManager.getTask(DownloadIndexTask.TASK_ID);
        if(task != null) {
            task.addOnProgressListener(this);
            task.addOnFinishedListener(this);
        }
        task = TaskManager.getTask(UpdateCatalogsTask.TASK_ID);
        if(task != null) {
            task.addOnProgressListener(this);
            task.addOnFinishedListener(this);
        }

        mTargetTranslationWithUpdates = App.getNotifyTargetTranslationWithUpdates();
        if(mTargetTranslationWithUpdates != null && task == null) {
            showTranslationUpdatePrompt(mTargetTranslationWithUpdates);
        }

//        ActivityManager am = (ActivityManager) this.getSystemService(Context.ACTIVITY_SERVICE);
//        if(am != null) {
//            int memoryLimit = am.getMemoryClass();
//            Logger.i(TAG, "application memory limit: " + memoryLimit);
//            ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
//            am.getMemoryInfo(info);
//            Logger.i(TAG, "available memory on the system: " + info.availMem);
//            Logger.i(TAG, "low memory state on the system: " + info.lowMemory);
//            Logger.i(TAG, "low memory threshold on the system: " + info.threshold);
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
//                Logger.i(TAG, "total memory on the system: " + info.totalMem);
//            }
//        }

        restoreDialogs();
    }

    /**
     * Restores dialogs
     */
    private void restoreDialogs() {
        // restore alert dialogs
        switch(mAlertShown) {
            case IMPORT_VERIFICATION:
                displayImportVerification();
                break;

            case MERGE_CONFLICT:
                showMergeConflict(mTargetTranslationID);
                break;

            case NONE:
                break;

            default:
                Logger.e(TAG,"Unsupported restore dialog: " + mAlertShown.toString());
                break;
        }
        // re-connect to dialog fragments
        Fragment dialog = getFragmentManager().findFragmentByTag(UpdateLibraryDialog.TAG);
        if(dialog != null && dialog instanceof EventBuffer.OnEventTalker) {
            ((EventBuffer.OnEventTalker)dialog).getEventBuffer().addOnEventListener(this);
        }
    }

    /**
     * Displays a dialog while replacing any duplicate dialog
     *
     * @param dialog
     * @param tag
     */
    private void showDialogFragment(DialogFragment dialog, String tag) {
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        Fragment prev = getFragmentManager().findFragmentByTag(tag);
        if (prev != null) {
            ft.remove(prev);
            // TODO: 10/7/16 I don't think we need this
            ft.commit();
            ft = getFragmentManager().beginTransaction();
        }
        ft.addToBackStack(null);
        // attach to any available event buffers
        if(dialog != null && dialog instanceof EventBuffer.OnEventTalker) {
            ((EventBuffer.OnEventTalker)dialog).getEventBuffer().addOnEventListener(this);
        }
        dialog.show(ft, tag);
    }

    @Override
    public void onFinished(final ManagedTask task) {
        taskWatcher.stop();
        if (task instanceof ExamineImportsForCollisionsTask) {
            final ExamineImportsForCollisionsTask examineTask = (ExamineImportsForCollisionsTask) task;
            Handler hand = new Handler(Looper.getMainLooper());
            hand.post(new Runnable() {
                @Override
                public void run() {
                    if (examineTask.mSuccess) {
                        displayImportVerification();
                    } else {
                        Logger.e(TAG, "Could not process content URI: " + examineTask.mContentUri.toString());
                        showImportResults(mExamineTask.mContentUri.toString(), null, false);
                        examineTask.cleanup();
                    }
                }
            });


        } else if (task instanceof ImportProjectsTask) {

            final ImportProjectsTask importTask = (ImportProjectsTask) task;
            Handler hand = new Handler(Looper.getMainLooper());
            hand.post(new Runnable() {
                @Override
                public void run() {
                    Translator.ImportResults importResults = importTask.getImportResults();
                    final boolean success = importResults.isSuccess();
                    if(success && importResults.mergeConflict) {
                        MergeConflictsHandler.backgroundTestForConflictedChunks(importResults.importedSlug, new MergeConflictsHandler.OnMergeConflictListener() {
                            @Override
                            public void onNoMergeConflict(String targetTranslationId) {
                                showImportResults(mExamineTask.mContentUri.toString(), mExamineTask.mProjectsFound, success);
                            }

                            @Override
                            public void onMergeConflict(String targetTranslationId) {
                                showMergeConflict(targetTranslationId);
                            }
                        });

                    } else {
                        showImportResults(mExamineTask.mContentUri.toString(), mExamineTask.mProjectsFound, success);
                    }
                }
            });
            mExamineTask.cleanup();
        } else if (task instanceof PullTargetTranslationTask) {
            PullTargetTranslationTask.Status status = ((PullTargetTranslationTask)task).getStatus();
            if(status == PullTargetTranslationTask.Status.UP_TO_DATE || status == PullTargetTranslationTask.Status.UNKNOWN) {
                new AlertDialog.Builder(this, R.style.AppTheme_Dialog)
                        .setTitle(R.string.success)
                        .setMessage(R.string.success_translation_update)
                        .setPositiveButton(R.string.dismiss, null)
                        .show();
            } else if (status == PullTargetTranslationTask.Status.AUTH_FAILURE) {
                // regenerate ssh keys
                // if we have already tried ask the user if they would like to try again
                if(App.context().hasSSHKeys()) {
                    showAuthFailure();
                    return;
                }

                RegisterSSHKeysTask keyTask = new RegisterSSHKeysTask(false);
                taskWatcher.watch(keyTask);
                TaskManager.addTask(keyTask, RegisterSSHKeysTask.TASK_ID);
            } else if (status == PullTargetTranslationTask.Status.MERGE_CONFLICTS) {
                new AlertDialog.Builder(this, R.style.AppTheme_Dialog)
                        .setTitle(R.string.success)
                        .setMessage(R.string.success_translation_update_with_conflicts)
                        .setNeutralButton(R.string.review, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Intent intent = new Intent(HomeActivity.this, TargetTranslationActivity.class);
                                intent.putExtra(App.EXTRA_TARGET_TRANSLATION_ID, ((PullTargetTranslationTask)task).targetTranslation.getId());
                                startActivityForResult(intent, TARGET_TRANSLATION_VIEW_REQUEST);
                            }
                        })
                        .show();
            } else {
                notifyTranslationUpdateFailed();
            }
            App.setNotifyTargetTranslationWithUpdates(null);
        } else if (task instanceof RegisterSSHKeysTask) {
            if (((RegisterSSHKeysTask) task).isSuccess()) {
                Logger.i(this.getClass().getName(), "SSH keys were registered with the server");
                // try to pull again
                downloadTargetTranslationUpdates(mTargetTranslationWithUpdates);
            } else {
                notifyTranslationUpdateFailed();
            }
        }
    }

    /**
     * let user know there was a merge conflict
     * @param targetTranslationID
     */
    public void showMergeConflict(String targetTranslationID) {
        mAlertShown = DialogShown.MERGE_CONFLICT;
        mTargetTranslationID = targetTranslationID;
        new AlertDialog.Builder(this, R.style.AppTheme_Dialog)
                .setTitle(R.string.merge_conflict_title).setMessage(R.string.import_merge_conflict)
                .setPositiveButton(R.string.label_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mAlertShown = DialogShown.NONE;
                        doManualMerge(mTargetTranslationID);
                    }
                }).show();
    }

    /**
     * open review mode to let user resolve conflict
     */
    public void doManualMerge(String mTargetTranslationID) {
        // ask parent activity to navigate to a new activity
        Intent intent = new Intent(this, TargetTranslationActivity.class);
        Bundle args = new Bundle();
        args.putString(App.EXTRA_TARGET_TRANSLATION_ID, mTargetTranslationID);
        args.putBoolean(App.EXTRA_START_WITH_MERGE_FILTER, true);
        args.putInt(App.EXTRA_VIEW_MODE, TranslationViewMode.REVIEW.ordinal());
        intent.putExtras(args);
        startActivityForResult(intent, TARGET_TRANSLATION_VIEW_REQUEST);
    }

    public void showAuthFailure() {
        new AlertDialog.Builder(this, R.style.AppTheme_Dialog)
                .setTitle(R.string.error).setMessage(R.string.auth_failure_retry)
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mAlertShown = DialogShown.NONE;
                        RegisterSSHKeysTask keyTask = new RegisterSSHKeysTask(true);
                        taskWatcher.watch(keyTask);
                        TaskManager.addTask(keyTask, RegisterSSHKeysTask.TASK_ID);
                    }
                })
                .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mAlertShown = DialogShown.NONE;
                        notifyTranslationUpdateFailed();
                    }
                }).show();
    }

    private void notifyTranslationUpdateFailed() {
        new AlertDialog.Builder(this, R.style.AppTheme_Dialog)
                .setTitle(R.string.error)
                .setMessage(R.string.update_failed)
                .setNeutralButton(R.string.dismiss, null)
                .show();
    }

    /**
     * display the final import Results.
     */
    private void showImportResults(String projectPath, String projectNames, boolean success) {
        mAlertShown = DialogShown.IMPORT_RESULTS;
        String message;
        if(success) {
            String format = App.context().getResources().getString(R.string.import_project_success);
            message = String.format(format, projectNames, projectPath);
        } else {
            String format = App.context().getResources().getString(R.string.import_failed);
            message = format + "\n" + projectPath;
        }

        new AlertDialog.Builder(this, R.style.AppTheme_Dialog)
                .setTitle(success ? R.string.title_import_success : R.string.title_import_failed)
                .setMessage(message)
                .setPositiveButton(R.string.label_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mAlertShown = DialogShown.NONE;
                        mExamineTask.cleanup();
                        HomeActivity.this.finish();
                    }
                })
                .show();
    }

    /**
     * begin the uri import
     * @param resolver
     * @param contentUri
     * @return
     * @throws Exception
     */
    private void importFromUri(ContentResolver resolver, Uri contentUri) {
        mExamineTask = new ExamineImportsForCollisionsTask(resolver, contentUri);
        taskWatcher.watch(mExamineTask);
        TaskManager.addTask(mExamineTask, ExamineImportsForCollisionsTask.TASK_ID);
    }

    /**
     * show dialog to verify that we want to import, restore or cancel.
     */
    private void displayImportVerification() {
        mAlertShown = DialogShown.IMPORT_VERIFICATION;
        AlertDialog.Builder dlg = new AlertDialog.Builder(this,R.style.AppTheme_Dialog);
            dlg.setTitle(R.string.label_import)
                .setMessage(String.format(getResources().getString(R.string.confirm_import_target_translation), mExamineTask.mProjectsFound))
                .setNegativeButton(R.string.title_cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mAlertShown = DialogShown.NONE;
                        mExamineTask.cleanup();
                        HomeActivity.this.finish();
                    }
                })
                .setPositiveButton(R.string.label_restore, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mAlertShown = DialogShown.NONE;
                        doArchiveImport(true);
                    }
                });

        if(mExamineTask.mAlreadyPresent) { // add merge option
            dlg.setNeutralButton(R.string.label_import, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    mAlertShown = DialogShown.NONE;
                    doArchiveImport(false);
                    dialog.dismiss();
                }
            });
        }
        dlg.show();
    }

    /**
     * import specified file
     * @param overwrite
     */
    private void doArchiveImport(boolean overwrite) {
        ImportProjectsTask importTask = new ImportProjectsTask(mExamineTask.mProjectsFolder, overwrite);
        taskWatcher.watch(importTask);
        TaskManager.addTask(importTask, ImportProjectsTask.TASK_ID);
    }

    /**
     * get last project opened and make sure it is still present
     * @return
     */
    @Nullable
    private TargetTranslation getLastOpened() {
        String lastTarget = App.getLastFocusTargetTranslation();
        if (lastTarget != null) {
            TargetTranslation targetTranslation = mTranslator.getTargetTranslation(lastTarget);
            if (targetTranslation != null) {
                return targetTranslation;
            }
        }
        return null;
    }

    @Override
    public void onBackPressed() {
        // display confirmation before closing the app
        new AlertDialog.Builder(this, R.style.AppTheme_Dialog)
                .setMessage(R.string.exit_confirmation)
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        HomeActivity.super.onBackPressed();
                    }
                })
                .setNegativeButton(R.string.no, null)
                .show();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(NEW_TARGET_TRANSLATION_REQUEST == requestCode ) {
            if(RESULT_OK == resultCode ) {
                if(mFragment instanceof WelcomeFragment) {
                    // display target translations list
                    mFragment = new TargetTranslationListFragment();
                    mFragment.setArguments(getIntent().getExtras());
                    getFragmentManager().beginTransaction().replace(R.id.fragment_container, mFragment).commit();
                } else {
                    ((TargetTranslationListFragment) mFragment).reloadList();
                }

                Intent intent = new Intent(HomeActivity.this, TargetTranslationActivity.class);
                intent.putExtra(App.EXTRA_TARGET_TRANSLATION_ID, data.getStringExtra(NewTargetTranslationActivity.EXTRA_TARGET_TRANSLATION_ID));
                startActivityForResult(intent, TARGET_TRANSLATION_VIEW_REQUEST);
            } else if( NewTargetTranslationActivity.RESULT_DUPLICATE == resultCode ) {
                // display duplicate notice to user
                String targetTranslationId = data.getStringExtra(NewTargetTranslationActivity.EXTRA_TARGET_TRANSLATION_ID);
                TargetTranslation existingTranslation = mTranslator.getTargetTranslation(targetTranslationId);
                if(existingTranslation != null) {
                    Project project = mLibrary.index().getProject(App.getDeviceLanguageCode(), existingTranslation.getProjectId(), true);
                    Snackbar snack = Snackbar.make(findViewById(android.R.id.content), String.format(getResources().getString(R.string.duplicate_target_translation), project.name, existingTranslation.getTargetLanguageName()), Snackbar.LENGTH_LONG);
                    ViewUtil.setSnackBarTextColor(snack, getResources().getColor(R.color.light_primary_text));
                    snack.show();
                }
            } else if( NewTargetTranslationActivity.RESULT_ERROR == resultCode) {
                Snackbar snack = Snackbar.make(findViewById(android.R.id.content), getResources().getString(R.string.error), Snackbar.LENGTH_LONG);
                ViewUtil.setSnackBarTextColor(snack, getResources().getColor(R.color.light_primary_text));
                snack.show();
            }
        } else if(TARGET_TRANSLATION_VIEW_REQUEST == requestCode ) {
            if(TargetTranslationActivity.RESULT_DO_UPDATE == resultCode ) {
                ManagedTask task = new UpdateSourceTask();
                ((UpdateSourceTask)task).setPrefix(this.getResources().getString(R.string.updating_languages));
                String taskId = UpdateSourceTask.TASK_ID;
                task.addOnProgressListener(this);
                task.addOnFinishedListener(this);
                TaskManager.addTask(task, taskId);
            }
        }
    }

    /**
     * prompt user that project has changed
     * @param targetTranslationId
     */
    public void showTranslationUpdatePrompt(final String targetTranslationId) {
        TargetTranslation targetTranslation = mTranslator.getTargetTranslation(targetTranslationId);
        if(targetTranslation == null) {
            Logger.e(TAG, "invalid target translation id:" + targetTranslationId);
            return;
        }

        String projectID = targetTranslation.getProjectId();
        Project project = App.getLibrary().index().getProject(targetTranslation.getTargetLanguageName(), projectID, true);
        if(project == null) {
            Logger.e(TAG, "invalid project id:" + projectID);
            return;
        }

        String message = String.format(getResources().getString(R.string.merge_request),
                project.name, targetTranslation.getTargetLanguageName());

        new AlertDialog.Builder(this, R.style.AppTheme_Dialog)
                .setTitle(R.string.change_detected)
                .setMessage(message)
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mAlertShown = DialogShown.NONE;
                        downloadTargetTranslationUpdates(targetTranslationId);
                    }
                })
                .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        App.setNotifyTargetTranslationWithUpdates(null);
                        mAlertShown = DialogShown.NONE;
                    }
                })
                .show();
    }

    /**
     * Updates a single target translation
     * @param targetTranslationId
     */
    private void downloadTargetTranslationUpdates(String targetTranslationId) {
        if(App.isNetworkAvailable()) {
            if (App.getProfile().gogsUser == null) {
                Door43LoginDialog dialog = new Door43LoginDialog();
                showDialogFragment(dialog, Door43LoginDialog.TAG);
                return;
            }

            PullTargetTranslationTask task = new PullTargetTranslationTask(
                    mTranslator.getTargetTranslation(targetTranslationId),
                    MergeStrategy.RECURSIVE,
                    null);
            taskWatcher.watch(task);
            TaskManager.addTask(task, PullTargetTranslationTask.TASK_ID);
        } else {
            Snackbar snack = Snackbar.make(findViewById(android.R.id.content), R.string.internet_not_available, Snackbar.LENGTH_LONG);
            ViewUtil.setSnackBarTextColor(snack, getResources().getColor(R.color.light_primary_text));
            snack.show();
        }
    }

    @Override
    public void onCreateNewTargetTranslation() {
        Intent intent = new Intent(HomeActivity.this, NewTargetTranslationActivity.class);
        startActivityForResult(intent, NEW_TARGET_TRANSLATION_REQUEST);
    }

    @Override
    public void onItemDeleted(String targetTranslationId) {
        if(mTranslator.getTargetTranslationIDs().length > 0) {
            ((TargetTranslationListFragment) mFragment).reloadList();
        } else {
            // display welcome screen
            mFragment = new WelcomeFragment();
            mFragment.setArguments(getIntent().getExtras());
            getFragmentManager().beginTransaction().replace(R.id.fragment_container, mFragment).commit();
        }
    }

    @Override
    public void onItemClick(TargetTranslation targetTranslation) {
        // validate project and target language

        Project project = App.getLibrary().index().getProject("en", targetTranslation.getProjectId(), true);
        TargetLanguage language = App.languageFromTargetTranslation(targetTranslation);
        if(project == null || language == null) {
            Snackbar snack = Snackbar.make(findViewById(android.R.id.content), R.string.missing_source, Snackbar.LENGTH_LONG);
            snack.setAction(R.string.check_for_updates, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mUpdateDialog = new UpdateLibraryDialog();
                    showDialogFragment(mUpdateDialog, mUpdateDialog.TAG);
                }
            });
            snack.setActionTextColor(getResources().getColor(R.color.light_primary_text));
            ViewUtil.setSnackBarTextColor(snack, getResources().getColor(R.color.light_primary_text));
            snack.show();
        } else {
            Intent intent = new Intent(HomeActivity.this, TargetTranslationActivity.class);
            intent.putExtra(App.EXTRA_TARGET_TRANSLATION_ID, targetTranslation.getId());
            startActivityForResult(intent, TARGET_TRANSLATION_VIEW_REQUEST);
        }
    }

    public void notifyDatasetChanged() {
        onResume();
    }

    @Override
    public void onSaveInstanceState(Bundle out) {
        // disconnect from tasks
        ManagedTask task = TaskManager.getTask(UpdateAllTask.TASK_ID);
        if(task != null) {
            task.removeOnProgressListener(this);
            task.removeOnFinishedListener(this);
        }
        task = TaskManager.getTask(UpdateSourceTask.TASK_ID);
        if(task != null) {
            task.removeOnProgressListener(this);
            task.removeOnFinishedListener(this);
        }
        task = TaskManager.getTask(DownloadIndexTask.TASK_ID);
        if(task != null) {
            task.removeOnProgressListener(this);
            task.removeOnFinishedListener(this);
        }
        task = TaskManager.getTask(UpdateCatalogsTask.TASK_ID);
        if(task != null) {
            task.removeOnProgressListener(this);
            task.removeOnFinishedListener(this);
        }

        // dismiss progress
        Handler hand = new Handler(Looper.getMainLooper());
        hand.post(new Runnable() {
            @Override
            public void run() {
                if(progressDialog != null) progressDialog.dismiss();
            }
        });

        out.putInt(STATE_DIALOG_SHOWN, mAlertShown.getValue());
        out.putString(STATE_DIALOG_TRANSLATION_ID, mTargetTranslationID);
        super.onSaveInstanceState(out);
    }

    @Override
    public void onDestroy() {
        if(progressDialog != null) progressDialog.dismiss();
        Fragment dialog = getFragmentManager().findFragmentByTag(UpdateLibraryDialog.TAG);
        if(dialog instanceof EventBuffer.OnEventTalker) {
            ((EventBuffer.OnEventTalker)dialog).getEventBuffer().removeOnEventListener(this);
        }
        super.onDestroy();
    }

    @Override
    public void onEventBufferEvent(EventBuffer.OnEventTalker talker, int tag, Bundle args) {
        if(talker instanceof UpdateLibraryDialog) {
            if(mUpdateDialog != null) {
                mUpdateDialog.dismiss();
            }

            if(!App.isNetworkAvailable()) {
                new AlertDialog.Builder(HomeActivity.this, R.style.AppTheme_Dialog)
                        .setTitle(R.string.internet_not_available)
                        .setMessage(R.string.check_network_connection)
                        .setPositiveButton(R.string.dismiss, null)
                        .show();
                return;
            }

            if(tag == UpdateLibraryDialog.EVENT_SELECT_DOWNLOAD_SOURCES) {
                selectDownloadSources();
                return;
            }

            ManagedTask task;
            String taskId;
            switch (tag) {
                case UpdateLibraryDialog.EVENT_UPDATE_LANGUAGES:
                    task = new UpdateCatalogsTask();
                    ((UpdateCatalogsTask)task).setPrefix(this.getResources().getString(R.string.updating_languages));
                    taskId = UpdateCatalogsTask.TASK_ID;
                    break;
                case UpdateLibraryDialog.EVENT_UPDATE_SOURCE:
                    task = new UpdateSourceTask();
                    ((UpdateSourceTask)task).setPrefix(this.getResources().getString(R.string.updating_sources));
                    taskId = UpdateSourceTask.TASK_ID;
                    break;
                case UpdateLibraryDialog.EVENT_DOWNLOAD_INDEX:
                    task = new DownloadIndexTask();
                    ((DownloadIndexTask)task).setPrefix(this.getResources().getString(R.string.downloading_index));
                    taskId = DownloadIndexTask.TASK_ID;
                    break;
                case UpdateLibraryDialog.EVENT_UPDATE_APP:
                default:
                    task = new CheckForLatestReleaseTask();
                    taskId = CheckForLatestReleaseTask.TASK_ID;
                    break;
            }
            task.addOnProgressListener(this);
            task.addOnFinishedListener(this);
            TaskManager.addTask(task, taskId);

        }
    }

    /**
     * bring up UI to select and download sources
     */
    private void selectDownloadSources() {
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        Fragment prev = getFragmentManager().findFragmentByTag(DownloadSourcesDialog.TAG);
        if (prev != null) {
            ft.remove(prev);
        }
        ft.addToBackStack(null);

        DownloadSourcesDialog dialog = new DownloadSourcesDialog();
        dialog.show(ft, DownloadSourcesDialog.TAG);
        return;
    }

    @Override
    public void onTaskProgress(final ManagedTask task, final double progress, final String message, boolean secondary) {
        Handler hand = new Handler(Looper.getMainLooper());
        hand.post(new Runnable() {
            @Override
            public void run() {
                // init dialog
                if(progressDialog == null) {
                    progressDialog = new ProgressDialog(HomeActivity.this);
                    progressDialog.setCancelable(true);
                    progressDialog.setCanceledOnTouchOutside(false);
                    progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                    progressDialog.setOnCancelListener(HomeActivity.this);
                    progressDialog.setIcon(R.drawable.ic_cloud_download_secondary_24dp);
                    progressDialog.setTitle(R.string.updating);
                    progressDialog.setMessage("");

                    progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            TaskManager.cancelTask(task);
                        }
                    });
                }

                // dismiss if finished or cancelled
                if(task.isFinished() || task.isCanceled()) {
                    progressDialog.dismiss();
                    return;
                }

                // progress
                progressDialog.setMax(task.maxProgress());
                progressDialog.setMessage(message);
                if(progress > 0) {
                    progressDialog.setIndeterminate(false);
                    progressDialog.setProgress((int)(progress * progressDialog.getMax()));
                    progressDialog.setProgressNumberFormat("%1d/%2d");
                    progressDialog.setProgressPercentFormat(NumberFormat.getPercentInstance());
                } else {
                    progressDialog.setIndeterminate(true);
                    progressDialog.setProgress(progressDialog.getMax());
                    progressDialog.setProgressNumberFormat(null);
                    progressDialog.setProgressPercentFormat(null);
                }

                // show
                if(task.isFinished()) {
                    progressDialog.dismiss();
                } else if(!progressDialog.isShowing()) {
                    progressDialog.show();
                }
            }
        });
    }

    @Override
    public void onTaskFinished(final ManagedTask task) {
        TaskManager.clearTask(task);

        Handler hand = new Handler(Looper.getMainLooper());
        hand.post(new Runnable() {
            @Override
            public void run() {
                if(task instanceof GetAvailableSourcesTask) {
                    GetAvailableSourcesTask availableSourcesTask = (GetAvailableSourcesTask) task;
                    if (progressDialog != null) {
                        progressDialog.dismiss();
                        progressDialog = null;
                    }

                    List<Translation> availableSources = availableSourcesTask.getSources();
                    Logger.i(TAG, "Found " + availableSources.size() + " sources");

                } else if(task instanceof CheckForLatestReleaseTask) {
                    CheckForLatestReleaseTask checkForLatestReleaseTask = (CheckForLatestReleaseTask) task;
                    if (progressDialog != null) {
                        progressDialog.dismiss();
                        progressDialog = null;
                    }

                    CheckForLatestReleaseTask.Release release = checkForLatestReleaseTask.getLatestRelease();
                    if (release == null) {
                        new AlertDialog.Builder(HomeActivity.this, R.style.AppTheme_Dialog)
                                .setTitle(R.string.check_for_updates)
                                .setMessage(R.string.have_latest_app_update)
                                .setPositiveButton(R.string.label_ok, null)
                                .show();
                    } else { // have newer
                        SettingsActivity.promptUserToDownloadLatestVersion(HomeActivity.this, checkForLatestReleaseTask.getLatestRelease());
                    }

                } else if(task instanceof LogoutTask) {
                    if (progressDialog != null) {
                        progressDialog.dismiss();
                        progressDialog = null;
                    }
                } else {
                    if (progressDialog != null) {
                        progressDialog.dismiss();
                        progressDialog = null;
                    }

                    int titleID = R.string.success;
                    int msgID = R.string.update_success;
                    String message = null;
                    boolean failed = true;

                    if (task.isCanceled()) {
                        titleID = R.string.error;
                        msgID = R.string.update_cancelled;
                    } else if (!isTaskSuccess(task)) {
                        titleID = R.string.error;
                        msgID = R.string.options_update_failed;
                    } else { // success
                        failed = false;
                        if (task instanceof UpdateSourceTask) {
                            UpdateSourceTask updTask = (UpdateSourceTask) task;
                            message = String.format(getResources().getString(R.string.update_sources_success), updTask.getAddedCnt(), updTask.getUpdatedCnt());
                        }
                        if (task instanceof UpdateCatalogsTask) {
                            UpdateCatalogsTask updTask = (UpdateCatalogsTask) task;
                            message = String.format(getResources().getString(R.string.update_languages_success), updTask.getAddedCnt());
                        }
                        if (task instanceof DownloadIndexTask) {
                            message = String.format(getResources().getString(R.string.download_index_success));
                        }
                    }
                    final boolean finalFailed = failed;
                    final boolean showDownloadDialog = task instanceof UpdateSourceTask;

                    // notify update is done
                    AlertDialog.Builder dlg =
                            new AlertDialog.Builder(HomeActivity.this, R.style.AppTheme_Dialog)
                                    .setTitle(titleID)
                                    .setPositiveButton(R.string.dismiss, new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            if(!finalFailed && showDownloadDialog) {
                                                selectDownloadSources(); // if not failed, immediately go to select downloads
                                            }
                                            if (task instanceof DownloadIndexTask) {
                                                App.restart();
                                            }
                                        }
                                    });

                    if (message == null) {
                        dlg.setMessage(msgID);
                    } else {
                        dlg.setMessage(message);
                    }

                    dlg.show();
                }
            }
        });
    }

    private boolean isTaskSuccess(ManagedTask task) {
        boolean success = false;
        if(task instanceof UpdateAllTask) {
            success = ((UpdateAllTask) task).isSuccess();
        } else if(task instanceof UpdateCatalogsTask) {
            success = ((UpdateCatalogsTask) task).isSuccess();
        } else if(task instanceof UpdateSourceTask) {
            success = ((UpdateSourceTask) task).isSuccess();
        } else if(task instanceof DownloadIndexTask) {
            success = ((DownloadIndexTask) task).isSuccess();
        }
        return success;
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        // cancel the running update tasks
        ManagedTask task = TaskManager.getTask(UpdateAllTask.TASK_ID);
        if(task != null) TaskManager.cancelTask(task);
        task = TaskManager.getTask(UpdateSourceTask.TASK_ID);
        if(task != null) TaskManager.cancelTask(task);
        task = TaskManager.getTask(DownloadIndexTask.TASK_ID);
        if(task != null) TaskManager.cancelTask(task);
        task = TaskManager.getTask(UpdateCatalogsTask.TASK_ID);
        if(task != null) TaskManager.cancelTask(task);
    }

    /**
     * for keeping track if dialog is being shown for orientation changes
     */
    public enum DialogShown {
        NONE,
        IMPORT_VERIFICATION,
        OPEN_LIBRARY,
        IMPORT_RESULTS,
        MERGE_CONFLICT;

        public int getValue() {
            return this.ordinal();
        }

        public static DialogShown fromInt(int ordinal, DialogShown defaultValue) {
            if (ordinal > 0 && ordinal < DialogShown.values().length) {
                return DialogShown.values()[ordinal];
            }
            return defaultValue;
        }
    }
}
