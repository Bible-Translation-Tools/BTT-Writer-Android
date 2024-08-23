package com.door43.translationstudio.tasks;

import android.content.Context;
import android.net.Uri;
import android.os.Process;

import com.door43.translationstudio.App;
import com.door43.translationstudio.R;
import com.door43.translationstudio.core.TargetTranslation;

import org.eclipse.jgit.api.errors.NoHeadException;
import org.unfoldingword.tools.logger.Logger;
import org.unfoldingword.tools.taskmanager.ManagedTask;

import java.io.File;

/**
 * Created by blm on 2/22/17.
 */

public class ExportProjectTask extends ManagedTask {

    public static final String TASK_ID = "export_project_task";
    public static final String TAG = ExportProjectTask.class.getSimpleName();
    final private Uri fileUri;
    private String message = "";
    final private TargetTranslation targetTranslation;

    public ExportProjectTask(Context context, Uri fileUri, TargetTranslation targetTranslation) {
        setThreadPriority(Process.THREAD_PRIORITY_DEFAULT);
        this.fileUri = fileUri;
        this.targetTranslation = targetTranslation;
        message = context.getString(R.string.please_wait);
    }

    public ExportProjectTask(Context context, File file, TargetTranslation targetTranslation) {
        setThreadPriority(Process.THREAD_PRIORITY_DEFAULT);
        this.fileUri = Uri.fromFile(file);
        this.targetTranslation = targetTranslation;
        message = context.getString(R.string.please_wait);
    }

    @Override
    public void start() {
        boolean success = false;
        publishProgress(-1, message);

        try {
            try {
                App.getTranslator().exportArchive(targetTranslation, fileUri);
                success = true;
            } catch (NoHeadException e) {
                // fix corrupt repo and try again
                App.recoverRepo(targetTranslation);
                App.getTranslator().exportArchive(targetTranslation, fileUri);
                success = true;
            }
        } catch (Exception e) {
            Logger.e(TAG, "Failed to export the target translation " + targetTranslation.getId(), e);
        }

        setResult(new ExportResults(fileUri, success));
    }

    /**
     * returns the import results which includes:
     *   the human readable filePath
     *   the success flag
     */
    public static class ExportResults {
        public final Uri fileUri;
        public final boolean success;

        ExportResults(Uri fileUri, boolean success) {
            this.fileUri = fileUri;
            this.success = success;
        }
    }
}
