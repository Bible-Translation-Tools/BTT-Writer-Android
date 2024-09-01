package com.door43.translationstudio.ui.dialogs;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import com.door43.translationstudio.App;
import com.door43.translationstudio.R;
import com.door43.translationstudio.core.ContainerCache;
import com.door43.translationstudio.core.TargetTranslation;
import com.door43.translationstudio.core.TranslationType;
import com.door43.translationstudio.core.Translator;
import com.door43.translationstudio.core.Typography;
import com.door43.translationstudio.tasks.DownloadImagesTask;
import com.door43.translationstudio.tasks.PrintPDFTask;
import com.door43.util.FileUtilities;

import org.unfoldingword.door43client.Door43Client;
import org.unfoldingword.door43client.models.TargetLanguage;
import org.unfoldingword.resourcecontainer.ResourceContainer;
import org.unfoldingword.tools.logger.Logger;
import org.unfoldingword.tools.taskmanager.SimpleTaskWatcher;
import org.unfoldingword.tools.taskmanager.ManagedTask;
import org.unfoldingword.tools.taskmanager.TaskManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidParameterException;

/**
 * Created by joel on 11/16/2015.
 */
public class PrintDialog extends DialogFragment implements SimpleTaskWatcher.OnFinishedListener, SimpleTaskWatcher.OnCanceledListener {

    public static final String TAG = "printDialog";
    public static final String ARG_TARGET_TRANSLATION_ID = "arg_target_translation_id";
    public static final String STATE_INCLUDE_IMAGES = "include_images";
    public static final String STATE_INCLUDE_INCOMPLETE = "include_incomplete";
    public static final String STATE_DIALOG_SHOWN = "state_dialog_shown";
    public static final String DOWNLOAD_IMAGES_TASK_KEY = "download_images_task";
    public static final String DOWNLOAD_IMAGES_TASK_GROUP = "download_images_task";
    public static final int INVALID = -1;
    public static final String STATE_OUTPUT_FILENAME = "state_output_filename";
    private Translator translator;
    private TargetTranslation mTargetTranslation;
    private Door43Client library;
    private boolean includeImages = false;
    private boolean includeIncompleteFrames = true;
    private Button printButton;
    private CheckBox includeImagesCheckBox;
    private CheckBox includeIncompleteCheckBox;
    private SimpleTaskWatcher taskWatcher;
    private File mExportFile;
    private Uri mDestinationFilename;
    private DialogShown mAlertShown = DialogShown.NONE;
    private AlertDialog mPrompt;
    private File mImagesDir;

    private ActivityResultLauncher<Intent> activityResultLauncher;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        activityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent data = result.getData();
                        mDestinationFilename = data.getData();
                        startPdfPrinting();
                    }
                }
        );

        return super.onCreateDialog(savedInstanceState);
    }

    @Override
    public void onDestroyView() {
        taskWatcher.stop();
        taskWatcher.setOnCanceledListener(null);
        taskWatcher.setOnFinishedListener(null);
        if(mPrompt != null) {
            mPrompt.dismiss();
        }
        super.onDestroyView();
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        getDialog().requestWindowFeature(Window.FEATURE_NO_TITLE);
        View v = inflater.inflate(R.layout.dialog_print, container, false);

        translator = App.getTranslator();
        library = App.getLibrary();

        Bundle args = getArguments();
        if(args == null || !args.containsKey(ARG_TARGET_TRANSLATION_ID)) {
            throw new InvalidParameterException("The target translation id was not specified");
        } else {
            String targetTranslationId = args.getString(ARG_TARGET_TRANSLATION_ID, null);
            mTargetTranslation = translator.getTargetTranslation(targetTranslationId);
            if(mTargetTranslation == null) {
                throw new InvalidParameterException("The target translation '" + targetTranslationId + "' is invalid");
            }
        }

        taskWatcher = new SimpleTaskWatcher(getActivity(), R.string.loading);
        taskWatcher.setOnFinishedListener(this);
        taskWatcher.setOnCanceledListener(this);

        if(savedInstanceState != null) {
            includeImages = savedInstanceState.getBoolean(STATE_INCLUDE_IMAGES, includeImages);
            includeIncompleteFrames = savedInstanceState.getBoolean(STATE_INCLUDE_INCOMPLETE, includeIncompleteFrames);
            mAlertShown = DialogShown.fromInt(savedInstanceState.getInt(STATE_DIALOG_SHOWN, INVALID), DialogShown.NONE);
            mDestinationFilename = savedInstanceState.getParcelable(STATE_OUTPUT_FILENAME);
        }

        // load the project title
        TextView projectTitleView = (TextView)v.findViewById(R.id.project_title);

        // set typeface for language
        TargetLanguage targetLanguage = mTargetTranslation.getTargetLanguage();
        Typeface typeface = Typography.getBestFontForLanguage(getActivity(), TranslationType.SOURCE, targetLanguage.slug, targetLanguage.direction);
        projectTitleView.setTypeface(typeface, Typeface.NORMAL);

        String title = mTargetTranslation.getProjectTranslation().getTitle().replaceAll("\n+$", "");
        if(title.isEmpty()) {
            ResourceContainer sourceContainer = ContainerCache.cacheClosest(library, null, mTargetTranslation.getProjectId(), mTargetTranslation.getResourceSlug());
            if(sourceContainer != null) {
                title = sourceContainer.readChunk("front", "title").replaceAll("\n+$", "");
            }
        }
        if(title.isEmpty()) {
            title = mTargetTranslation.getProjectId();
        }
        projectTitleView.setText(title + " - " + mTargetTranslation.getTargetLanguageName());

        boolean isObsProject = mTargetTranslation.isObsProject();

        this.includeImagesCheckBox = (CheckBox)v.findViewById(R.id.print_images);
        this.includeIncompleteCheckBox = (CheckBox)v.findViewById(R.id.print_incomplete_frames);

        if(isObsProject) {
            includeImagesCheckBox.setEnabled(true);
            includeImagesCheckBox.setChecked(includeImages);
        } else { // no images in bible stories
            includeImagesCheckBox.setVisibility(View.GONE);
            includeImagesCheckBox.setChecked(false);
        }

        includeIncompleteCheckBox.setEnabled(true);
        includeIncompleteCheckBox.setChecked(includeIncompleteFrames);

        mExportFile = new File(App.getSharingDir(), mTargetTranslation.getId() + ".pdf");

        Button cancelButton  = (Button)v.findViewById(R.id.cancel_button);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });
        printButton  = (Button)v.findViewById(R.id.print_button);
        printButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                includeImages = includeImagesCheckBox.isChecked();
                includeIncompleteFrames = includeIncompleteCheckBox.isChecked();
                selectDestinationFile();
            }
        });

        // re-attach to tasks
        ManagedTask downloadTask = TaskManager.getTask(DownloadImagesTask.TASK_ID);
        ManagedTask printTask = TaskManager.getTask(PrintPDFTask.TASK_ID);
        if(downloadTask != null) {
            taskWatcher.watch(downloadTask);
        } else if(printTask != null) {
            taskWatcher.watch(printTask);
        }

        restoreDialogs();
        return v;
    }

    /**
     * Restores dialogs
     */
    private void restoreDialogs() {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {

                // restore alert dialogs
                switch(mAlertShown) {
                    case INTERNET_PROMPT:
                        showInternetUsePrompt();
                        break;

                    case NONE:
                        break;

                    default:
                        Logger.e(TAG,"Unsupported restore dialog: " + mAlertShown.toString());
                        break;
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();

        // widen dialog to accommodate more text
        int desiredWidth = 750;
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        int width = displayMetrics.widthPixels;
        float density = displayMetrics.density;
        float correctedWidth = width / density;
        float screenWidthFactor = desiredWidth /correctedWidth;
        screenWidthFactor = Math.min(screenWidthFactor, 1f); // sanity check
        getDialog().getWindow().setLayout((int) (width * screenWidthFactor), WindowManager.LayoutParams.WRAP_CONTENT);
    }

    /**
     * starts activity to let user select output folder
     */
    private void selectDestinationFile() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        intent.putExtra(Intent.EXTRA_TITLE, mExportFile.getName());
        activityResultLauncher.launch(intent);
    }

    /**
     * start PDF printing.  If image printing is selected, will prompt to warn for internet usage
     * @return void
     */
    private void startPdfPrinting() {
        if(includeImages && !App.hasImages()) {
            showInternetUsePrompt();
        } else {
            PrintPDFTask task = new PrintPDFTask(mTargetTranslation.getId(), mExportFile, includeImages, includeIncompleteFrames, mImagesDir);
            taskWatcher.watch(task);
            TaskManager.addTask(task, PrintPDFTask.TASK_ID);
        }
    }

    /**
     * prompt user to show internet
     */
    private void showInternetUsePrompt() {
        mAlertShown = DialogShown.INTERNET_PROMPT;

        new AlertDialog.Builder(getActivity(), R.style.AppTheme_Dialog)
                .setTitle(R.string.use_internet_confirmation)
                .setMessage(R.string.image_large_download)
                .setNegativeButton(R.string.title_cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mAlertShown = DialogShown.NONE;
                    }
                })
                .setPositiveButton(R.string.label_ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mAlertShown = DialogShown.NONE;
                        DownloadImagesTask task = new DownloadImagesTask();
                        taskWatcher.watch(task);
                        TaskManager.addTask(task, DownloadImagesTask.TASK_ID);
                    }
                })
                .show();
    }

    @Override
    public void onSaveInstanceState(Bundle out) {
        out.putBoolean(STATE_INCLUDE_IMAGES, includeImages);
        out.putBoolean(STATE_INCLUDE_INCOMPLETE, includeIncompleteFrames);
        out.putInt(STATE_DIALOG_SHOWN, mAlertShown.getValue());
        out.putParcelable(STATE_OUTPUT_FILENAME, mDestinationFilename);

        super.onSaveInstanceState(out);
    }

    @Override
    public void onFinished(ManagedTask task) {
        taskWatcher.stop();
        TaskManager.clearTask(task);

        if(task instanceof DownloadImagesTask) {
            DownloadImagesTask downloadImagesTask = (DownloadImagesTask) task;
            if (downloadImagesTask.getSuccess()) {
                mImagesDir = downloadImagesTask.getImagesDir();
                final PrintPDFTask printTask = new PrintPDFTask(mTargetTranslation.getId(), mExportFile, includeImages, includeIncompleteFrames, mImagesDir);
                Handler hand = new Handler(Looper.getMainLooper());
                hand.post(new Runnable() {
                    @Override
                    public void run() {
                        taskWatcher.watch(printTask);
                    }
                });
                TaskManager.addTask(printTask, PrintPDFTask.TASK_ID);
            } else {
                // download failed
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        new AlertDialog.Builder(getActivity(), R.style.AppTheme_Dialog)
                                .setTitle(R.string.download_failed)
                                .setMessage(R.string.downloading_images_for_print_failed)
                                .setPositiveButton(R.string.label_ok, null)
                                .show();
                    }
                });
            }
        } else if(task instanceof PrintPDFTask) {
            if(((PrintPDFTask)task).isSuccess()) {
                boolean success = false;
                String filename = FileUtilities.getUriDisplayName(getContext(), mDestinationFilename);

                // copy PDF to location the user selected
                try (OutputStream out = getContext().getContentResolver().openOutputStream(mDestinationFilename)) {
                    try (InputStream in = new FileInputStream(mExportFile)) {
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = in.read(buffer)) > 0) {
                            out.write(buffer, 0, length);
                        }
                        success = true;
                    }
                } catch (IOException e) {
                    Logger.e(TAG, "Failed to copy the PDF file to: " + mDestinationFilename, e);
                }

                if(success) {
                    String message = getActivity().getResources().getString(R.string.print_success, filename);
                    new AlertDialog.Builder(getActivity(), R.style.AppTheme_Dialog)
                            .setTitle(R.string.success)
                            .setMessage(message)
                            .setPositiveButton(R.string.dismiss, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                    PrintDialog.this.dismiss();
                                }
                            })
                            .show();
                    return;
                }
            }

            new AlertDialog.Builder(getActivity(), R.style.AppTheme_Dialog)
                    .setTitle(R.string.error)
                    .setMessage(R.string.print_failed)
                    .setPositiveButton(R.string.dismiss, null)
                    .show();
        }
    }

    @Override
    public void onCanceled(ManagedTask task) {
        // try to stop downloading if the user cancels the download
        task.stop();
    }

    /**
     * for keeping track if dialog is being shown for orientation changes
     */
    public enum DialogShown {
        NONE,
        INTERNET_PROMPT,
        FILENAME_PROMPT,
        OVERWRITE_PROMPT;

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
