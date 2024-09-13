package com.door43.translationstudio.core;

import android.os.Handler;
import android.os.Looper;

import com.door43.translationstudio.App;
import com.door43.usecases.ParseMergeConflicts;

import org.unfoldingword.tools.taskmanager.ManagedTask;
import org.unfoldingword.tools.taskmanager.TaskManager;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by blm on 11/22/16.
 */

public class MergeConflictsHandler {
    public static final String mergeConflictHead = "<<<<<<< HEAD.*\\n";
    public static Pattern mergeConflictPatternHead = Pattern.compile(mergeConflictHead);


    /**
     * Split the merge conflict into a list of the options
     *
     * @param text
     * @return
     */
    static public List<CharSequence> getMergeConflictItems(String text) {
        return ParseMergeConflicts.INSTANCE.execute(text);
    }

    /**
     * Split the merge conflict into a list of the options
     *
     * @param text
     * @return
     */
    static public CharSequence getMergeConflictItemsHead(String text) {
        List<CharSequence> items = getMergeConflictItems(text);
        if(items.isEmpty()) {
            return null;
        }
        return items.get(0);
    }

    /**
     * Detects merge conflict tags
     *
     * @param text
     * @return
     */
    static public boolean isMergeConflicted(CharSequence text) {
        if ((text != null) && (text.length() > 0)) {
            Matcher matcher = mergeConflictPatternHead.matcher(text);
            return matcher.find();
        }
        return false;
    }

    /**
     * search for first merge conflict - We need this to double check that there is a conflict in any chunks
     *
     * @param targetTranslationId
     * @return
     */
    static public boolean isTranslationMergeConflicted(String targetTranslationId) {
        if(targetTranslationId == null) {
            return false;
        }

        Translator translator = App.getTranslator();

        TargetTranslation targetTranslation = translator.getTargetTranslation(targetTranslationId);
        if(targetTranslation == null) {
            return false;
        }

        ProjectTranslation pt = targetTranslation.getProjectTranslation();
        if(pt == null) {
            return false;
        }

        if(isMergeConflicted(pt.getTitle())) {
            return true;
        }

        ChapterTranslation[] chapters = targetTranslation.getChapterTranslations();
        for(ChapterTranslation ct:chapters) {
            if(isMergeConflicted(ct.title)) {
                return true;
            }

            if(isMergeConflicted(ct.reference)) {
                return true;
            }

            FrameTranslation[] frames = targetTranslation.getFrameTranslations(ct.getId(), TranslationFormat.DEFAULT);
            for (FrameTranslation frame : frames) {
                if (isMergeConflicted(frame.body)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * check the whole project to see if there is actually a chunk conflict
     * @param targetTranslationId
     * @param listener
     */
    public static void backgroundTestForConflictedChunks(final String targetTranslationId, final OnMergeConflictListener listener) {
        ManagedTask task = new ManagedTask() {
            @Override
            public void start() {
                try {
                    if(interrupted()) return;
                    boolean conflicted = MergeConflictsHandler.isTranslationMergeConflicted(targetTranslationId);
                    setResult(conflicted);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        task.addOnFinishedListener(task1 -> {
            TaskManager.clearTask(task1);
            boolean conflicted = false;
            if(task1.getResult() != null) conflicted = (boolean) task1.getResult();
            if(!task1.isCanceled()) {
                Handler hand = new Handler(Looper.getMainLooper());
                final boolean finalConflicted = conflicted;
                hand.post(() -> {
                    if(finalConflicted) {
                        listener.onMergeConflict(targetTranslationId);
                    } else {
                        listener.onNoMergeConflict(targetTranslationId);
                    }
                });
            }
        });
        TaskManager.addTask(task);
    }

    public interface OnMergeConflictListener {
        void onNoMergeConflict(String targetTranslationId);
        void onMergeConflict(String targetTranslationId);
    }
}
