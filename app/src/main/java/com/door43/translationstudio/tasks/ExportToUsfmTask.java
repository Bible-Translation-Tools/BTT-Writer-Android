package com.door43.translationstudio.tasks;

import android.content.Context;
import android.net.Uri;
import android.os.Process;

import com.door43.translationstudio.R;
import com.door43.translationstudio.core.ExportUsfm;
import com.door43.translationstudio.core.TargetTranslation;
import org.unfoldingword.tools.taskmanager.ManagedTask;

/**
 * Created by blm on 2/22/17.
 */

public class ExportToUsfmTask extends ManagedTask {

    public static final String TASK_ID = "export_to_usfm_task";
    public static final String TAG = ExportProjectTask.class.getSimpleName();
    final private Uri fileUri;
    private String message = "";
    final private TargetTranslation targetTranslation;

    public ExportToUsfmTask(Context context, TargetTranslation targetTranslation, Uri fileUri) {
        setThreadPriority(Process.THREAD_PRIORITY_DEFAULT);
        this.fileUri = fileUri;
        this.targetTranslation = targetTranslation;
        message = context.getString(R.string.please_wait);
    }

    @Override
    public void start() {
        publishProgress(-1, message);
        Uri exportFile = ExportUsfm.saveToUSFM(targetTranslation, fileUri);
        setResult(exportFile);
    }
}