package com.door43.translationstudio.ui.home;


import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.door43.translationstudio.core.BibleCodes;
import com.door43.translationstudio.core.TargetTranslation;
import com.door43.translationstudio.core.TranslationType;
import com.door43.translationstudio.core.Typography;

import org.unfoldingword.door43client.models.TargetLanguage;
import org.unfoldingword.tools.taskmanager.ManagedTask;
import org.unfoldingword.tools.taskmanager.TaskManager;

import com.door43.translationstudio.databinding.FragmentTargetTranslationListItemBinding;
import com.door43.translationstudio.tasks.TranslationProgressTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by joel on 9/3/2015.
 */
public class TargetTranslationAdapter extends BaseAdapter implements ManagedTask.OnFinishedListener {
    private List<TranslationItem> translations;
    private OnInfoClickListener infoClickListener = null;
    private Map<String, Integer> translationProgress = new HashMap<>();
    private List<String> translationProgressCalculated = new ArrayList<>();
    private SortProjectColumnType sortProjectColumn = SortProjectColumnType.bibleOrder;
    private SortByColumnType sortByColumn = SortByColumnType.projectThenLanguage;;

    private static final List<String> bookList = Arrays.asList(BibleCodes.getBibleBooks());


    public TargetTranslationAdapter() {
        translations = new ArrayList<>();
    }

    /**
     * Adds a listener to be called when the info button is called
     * @param listener the listener to be added
     */
    public void setOnInfoClickListener(OnInfoClickListener listener) {
        infoClickListener = listener;
    }

    @Override
    public int getCount() {
        if(translations != null) {
            return translations.size();
        } else {
            return 0;
        }
    }

    public void sort() {
        sort(sortByColumn, sortProjectColumn);
    }

    public void sort(final SortByColumnType sortByColumn, final SortProjectColumnType sortProjectColumn) {
        this.sortByColumn = sortByColumn;
        this.sortProjectColumn = sortProjectColumn;
        Collections.sort(translations, (lhs, rhs) -> {
            int compare;
            switch (sortByColumn) {
                case projectThenLanguage:
                    compare = compareProject(lhs, rhs, sortProjectColumn);
                    if(compare == 0) {
                        compare = lhs.getTranslation().getTargetLanguageName()
                                .compareToIgnoreCase(rhs.getTranslation().getTargetLanguageName());
                    }
                    return compare;
                case languageThenProject:
                    compare = lhs.getTranslation().getTargetLanguageName()
                            .compareToIgnoreCase(rhs.getTranslation().getTargetLanguageName());
                    if(compare == 0) {
                        compare = compareProject(lhs, rhs, sortProjectColumn);
                    }
                    return compare;
                case progressThenProject:
                default:
                    compare = getProgress(rhs) - getProgress(lhs);
                    if(compare == 0) {
                        compare = compareProject(lhs, rhs, sortProjectColumn);
                    }
                    return compare;
            }
        });

        Handler hand = new Handler(Looper.getMainLooper());
        hand.post(this::notifyDataSetChanged);
    }

    /**
     * compare projects (for use in sorting)
     * @param lhs
     * @param rhs
     * @return
     */
    private int compareProject(TranslationItem lhs, TranslationItem rhs, SortProjectColumnType sortProjectColumn) {
        if(sortProjectColumn == SortProjectColumnType.bibleOrder) {
            int lhsIndex = bookList.indexOf(lhs.getTranslation().getProjectId());
            int rhsIndex = bookList.indexOf(rhs.getTranslation().getProjectId());
            if((lhsIndex == rhsIndex) && (lhsIndex < 0)) { // if not bible books, then compare by name
                return lhs.getFormattedProjectName().compareToIgnoreCase(rhs.getFormattedProjectName());
            }
            return lhsIndex - rhsIndex;
        }

        // compare project names
        return lhs.getFormattedProjectName().compareToIgnoreCase(rhs.getFormattedProjectName());
    }

    @Override
    public TranslationItem getItem(int position) {
        return translations.get(position);
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(final int position, View convertView, final ViewGroup parent) {
        Context context = parent.getContext();
        final ViewHolder holder;

        if(convertView == null) {
            FragmentTargetTranslationListItemBinding binding =
                    FragmentTargetTranslationListItemBinding.inflate(
                            LayoutInflater.from(parent.getContext()),
                            parent,
                            false
                    );
            holder = new ViewHolder(binding);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        final TranslationItem targetTranslation = getItem(position);
        holder.currentTargetTranslation = targetTranslation;
        holder.binding.translationProgress.setVisibility(View.INVISIBLE);

        // calculate translation progress
        if(!translationProgressCalculated.contains(targetTranslation.getTranslation().getId())) {
            String taskId = TranslationProgressTask.TASK_ID + targetTranslation.getTranslation().getId();
            TranslationProgressTask progressTask = (TranslationProgressTask) TaskManager.getTask(taskId);
            if(progressTask != null) {
                // attach listener
                progressTask.removeAllOnFinishedListener();
                progressTask.addOnFinishedListener(this);
            } else {
                progressTask = new TranslationProgressTask(targetTranslation.getTranslation());
                progressTask.addOnFinishedListener(this);
                TaskManager.addTask(progressTask, TranslationProgressTask.TASK_ID + targetTranslation.getTranslation().getId());
                TaskManager.groupTask(progressTask, "calc-translation-progress");
            }
        } else {
            holder.setProgress(getProgress(targetTranslation));
        }

        // render view
        holder.binding.projectTitle.setText(targetTranslation.getFormattedProjectName());
        holder.binding.targetLanguage.setText(targetTranslation.getTranslation().getTargetLanguageName());

        // set typeface for language
        TargetLanguage targetLanguage = targetTranslation.getTranslation().getTargetLanguage();
        Typeface typeface = Typography.getBestFontForLanguage(
                context,
                TranslationType.SOURCE,
                targetLanguage.slug,
                targetLanguage.direction
        );
        holder.binding.targetLanguage.setTypeface(typeface, Typeface.NORMAL);

        // TODO: finish rendering project icon
        holder.binding.infoButton.setOnClickListener(v1 -> {
            if(infoClickListener != null) {
                infoClickListener.onClick(getItem(position));
            }
        });
        return holder.binding.getRoot();
    }

    /**
     * get calculated project
     * @param targetTranslation
     * @return
     */
    private Integer getProgress(TranslationItem targetTranslation) {
        if(translationProgressCalculated.contains(targetTranslation.getTranslation().getId())) {
            Integer value =  translationProgress.get(targetTranslation.getTranslation().getId());
            if(value != null) return value;
        }
        return -1;
    }

    public void setData(List<TranslationItem> targetTranslations) {
        translations = targetTranslations;
        translationProgress = new HashMap<>();
        translationProgressCalculated = new ArrayList<>();
        sort();
    }

    @Override
    public void onTaskFinished(ManagedTask task) {
        TaskManager.clearTask(task);

        if(task instanceof TranslationProgressTask) {
            // save progress
            double progressLong = ((TranslationProgressTask) task).getProgress();
            final int progress = Math.round((float)progressLong * 100);
            final TargetTranslation targetTranslation = ((TranslationProgressTask) task).targetTranslation;
            translationProgress.put(targetTranslation.getId(), progress);
            translationProgressCalculated.add(targetTranslation.getId());

            Handler hand = new Handler(Looper.getMainLooper());
            hand.post(new Runnable() {
                @Override
                public void run() {
                    sort();
                }
            });
        }
    }

    public interface OnInfoClickListener {
        void onClick(TranslationItem item);
    }

    public static class ViewHolder {
        public TranslationItem currentTargetTranslation;
        public FragmentTargetTranslationListItemBinding binding;

        public ViewHolder(FragmentTargetTranslationListItemBinding binding) {
            this.binding = binding;
            binding.translationProgress.setMax(100);
            binding.getRoot().setTag(this);
        }

        public void setProgress(int progress) {
            if(progress < 0) progress = 0;
            if(progress > 100) progress = 100;
            binding.translationProgress.setProgress(progress);
            binding.translationProgress.setVisibility(View.VISIBLE);
        }
    }


    /**
     * enum that keeps track of current state of USFM import
     */
    public enum SortByColumnType {
        projectThenLanguage(0),
        languageThenProject(1),
        progressThenProject(2);

        private final int _value;

        SortByColumnType(int Value) {
            this._value = Value;
        }

        public int getValue() {
            return _value;
        }

        public static SortByColumnType fromString(String value, SortByColumnType defaultValue ) {
            Integer returnValue = null;
            if(value != null) {
                try {
                    returnValue = Integer.valueOf(value);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            if(returnValue == null) {
                return defaultValue;
            }

            return fromInt(returnValue);
        }

        public static SortByColumnType fromInt(int i) {
            for (SortByColumnType b : SortByColumnType.values()) {
                if (b.getValue() == i) {
                    return b;
                }
            }
            return null;
        }
    }

    /**
     * enum that keeps track of current state of USFM import
     */
    public enum SortProjectColumnType {
        bibleOrder(0),
        alphabetical(1);

        private final int _value;

        SortProjectColumnType(int Value) {
            this._value = Value;
        }

        public int getValue() {
            return _value;
        }

        public static SortProjectColumnType fromString(String value, SortProjectColumnType defaultValue ) {
            Integer returnValue = null;
            if(value != null) {
                try {
                    returnValue = Integer.valueOf(value);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            if(returnValue == null) {
                return defaultValue;
            }

            return fromInt(returnValue);
        }

        public static SortProjectColumnType fromInt(int i) {
            for (SortProjectColumnType b : SortProjectColumnType.values()) {
                if (b.getValue() == i) {
                    return b;
                }
            }
            return null;
        }
    }
}