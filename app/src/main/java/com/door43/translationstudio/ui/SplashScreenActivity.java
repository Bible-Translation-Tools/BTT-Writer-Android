package com.door43.translationstudio.ui;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import android.widget.ProgressBar;
import android.widget.TextView;

import com.door43.translationstudio.App;
import com.door43.translationstudio.R;
import com.door43.translationstudio.tasks.UpdateAppTask;

import org.unfoldingword.tools.logger.Logger;
import org.unfoldingword.tools.taskmanager.ManagedTask;
import org.unfoldingword.tools.taskmanager.TaskManager;

import java.io.File;
import java.util.List;

/**
 * This activity initializes the app
 */
public class SplashScreenActivity extends BaseActivity implements ManagedTask.OnFinishedListener, ManagedTask.OnStartListener {
    private static final String STATE_STARTED = "started";
    private static final String LOGGING_TAG = "SplashScreenActivity";
    private static final int DIRTREE_REQUEST_CODE = 777;
    private TextView mProgressTextView;
    private ProgressBar mProgressBar;
    private boolean silentStart = true;
    private boolean started = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        mProgressTextView = (TextView)findViewById(R.id.loadingText);
        mProgressBar = (ProgressBar)findViewById(R.id.loadingBar);
        mProgressBar.setMax(100);
        mProgressBar.setIndeterminate(true);

        if(savedInstanceState != null) {
            mProgressTextView.setText(savedInstanceState.getString("message"));
            started = savedInstanceState.getBoolean(STATE_STARTED);
        }

//        // check minimum requirements
//        boolean checkHardware = App.getUserPreferences().getBoolean(SettingsActivity.KEY_PREF_CHECK_HARDWARE, true);
//        if(checkHardware && !started) {
//            int numProcessors = Runtime.getRuntime().availableProcessors();
//            long maxMem = Runtime.getRuntime().maxMemory();
//
//            int screenMask = getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK;
//            boolean smallScreen = (screenMask == Configuration.SCREENLAYOUT_SIZE_SMALL
//                                    || screenMask == Configuration.SCREENLAYOUT_SIZE_NORMAL
//                                    || screenMask == Configuration.SCREENLAYOUT_SIZE_UNDEFINED);
//
//            if (numProcessors < App.minimumNumberOfProcessors || maxMem < App.minimumRequiredRAM || smallScreen) {
//                silentStart = false;
//                new AlertDialog.Builder(this, R.style.AppTheme_Dialog)
//                        .setTitle(R.string.slow_device)
//                        .setMessage(R.string.min_hardware_req_not_met)
//                        .setCancelable(false)
//                        .setNegativeButton(R.string.do_not_show_again, new DialogInterface.OnClickListener() {
//                            @Override
//                            public void onClick(DialogInterface dialog, int which) {
//                                SharedPreferences.Editor editor = App.getUserPreferences().edit();
//                                editor.putBoolean(SettingsActivity.KEY_PREF_CHECK_HARDWARE, false);
//                                editor.apply();
//                                start();
//                            }
//                        })
//                        .setPositiveButton(R.string.label_continue, new DialogInterface.OnClickListener() {
//                            @Override
//                            public void onClick(DialogInterface dialog, int which) {
//                                start();
//                            }
//                        })
//                        .show();
//            }
//        }

        // Check to see if we have an approved data dir.
        // If not, request one.
        List<UriPermission> uriPermissionList = getContentResolver().getPersistedUriPermissions();
        if (uriPermissionList.size() > 0) {
            Uri savedPublicDataDir = uriPermissionList.get(0).getUri();
            Logger.i(LOGGING_TAG, "Found persisted access to public data dir:" + savedPublicDataDir);
            App.publicDataDir = savedPublicDataDir;
            App.setupLogger();
            App.setupCrashDir();
        } else {
            // Request access to folder
            silentStart = false;
            new AlertDialog.Builder(this, R.style.AppTheme_Dialog)
                    // TODO: Replace with R strings
                    .setTitle("Select Directory")
                    .setMessage("On the next screen, please select the directory in which BTT-Writer should put its files.")
                    .setCancelable(false)
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Intent intent = new Intent("android.intent.action.OPEN_DOCUMENT_TREE");
                            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                            intent.putExtra("android.provider.extra.INITIAL_URI", Uri.encode("BTT-Writer")); // android.net.Uri
                            if (intent.resolveActivity(getPackageManager()) != null) {
                                startActivityForResult(intent, DIRTREE_REQUEST_CODE);
                            }
                        }
                    })
                    .show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == DIRTREE_REQUEST_CODE) {
            if (resultCode != RESULT_OK) {
                //TODO: Handle failure gracefully
                Logger.e(LOGGING_TAG, "Couldn't acquire public data dir.");
                finish();
                return;
            }
            Uri uri = data.getData();
            Logger.i(LOGGING_TAG, "Granted access to public data dir:" + uri);
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            App.publicDataDir = uri;
            App.setupLogger();
            App.setupCrashDir();
        }

    }

    @Override
    public void onResume() {
        super.onResume();
        if(silentStart) {
            start();
        }
    }

    /**
     * Begins running tasks
     */
    private void start() {
        started = true;
        if(!waitingForPermissions()) {
            // check if we crashed
            File[] files = Logger.listStacktraces();
            if (files.length > 0) {
                Intent intent = new Intent(this, CrashReporterActivity.class);
                startActivity(intent);
                finish();
                return;
            }

            // connect to tasks
            boolean isWorking = connectToTask(UpdateAppTask.TASK_ID);

            // start new task
            if (!isWorking) {
                UpdateAppTask updateTask = new UpdateAppTask(App.context());
                updateTask.addOnFinishedListener(this);
                updateTask.addOnStartListener(this);
                TaskManager.addTask(updateTask, UpdateAppTask.TASK_ID);
            }
        }
    }

    /**
     * Connect to an existing task
     * @param id
     * @return true if the task exists
     */
    private boolean connectToTask(String id) {
        ManagedTask t = TaskManager.getTask(id);
        if(t != null) {
            t.addOnFinishedListener(this);
            t.addOnStartListener(this);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Disconnects this activity from a task
     * @param t
     */
    private void disconnectTask(ManagedTask t) {
        if(t != null) {
            t.removeOnStartListener(this);
            t.removeOnFinishedListener(this);
        }
    }

    /**
     * Disconnects this activity from a task
     * @param id
     */
    private void disconnectTask(String id) {
        ManagedTask t = TaskManager.getTask(id);
        disconnectTask(t);
    }

    @Override
    public void onTaskFinished(final ManagedTask task) {
        TaskManager.clearTask(task);
        disconnectTask(task);
        openMainActivity();
    }

    private void openMainActivity() {
        Intent intent = new Intent(this, ProfileActivity.class);
        startActivity(intent);
        finish();
    }

    @Override
    public void onTaskStart(final ManagedTask task) {
        Handler hand = new Handler(Looper.getMainLooper());
        hand.post(new Runnable() {
            @Override
            public void run() {
                mProgressTextView.setText(R.string.updating_app);
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString("message", mProgressTextView.getText().toString());
        outState.putInt("progress", mProgressBar.getProgress());
        outState.putBoolean(STATE_STARTED, started);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onDestroy() {
        disconnectTask(UpdateAppTask.TASK_ID);
        super.onDestroy();
    }
}
