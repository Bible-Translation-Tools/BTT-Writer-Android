package com.door43.translationstudio.ui.translate.review;

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;

import com.door43.translationstudio.databinding.FragmentMergeCardBinding;
import com.door43.translationstudio.databinding.FragmentResourcesListItemBinding;
import com.door43.translationstudio.ui.translate.IReviewListItemBinding;
import com.door43.translationstudio.ui.translate.ReviewListItem;
import com.google.android.material.tabs.TabLayout;
import androidx.recyclerview.widget.RecyclerView;

import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.door43.translationstudio.App;
import com.door43.translationstudio.R;
import com.door43.translationstudio.core.ContainerCache;
import com.door43.translationstudio.core.FileHistory;
import com.door43.translationstudio.core.TranslationFormat;
import com.door43.translationstudio.core.TranslationType;
import com.door43.translationstudio.core.Typography;
import com.door43.translationstudio.tasks.MergeConflictsParseTask;
import com.door43.translationstudio.ui.translate.TranslationHelp;
import com.door43.translationstudio.ui.translate.ViewModeAdapter;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.unfoldingword.resourcecontainer.Language;
import org.unfoldingword.resourcecontainer.Link;
import org.unfoldingword.resourcecontainer.ResourceContainer;
import org.unfoldingword.tools.taskmanager.ThreadableUI;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a review mode view
 */
public class ReviewHolder extends RecyclerView.ViewHolder {
    private static final int TAB_NOTES = 0;
    private static final int TAB_WORDS = 1;
    private static final int TAB_QUESTIONS = 2;

    private final Context context;
    private final LayoutInflater inflater;
    private final TabLayout.OnTabSelectedListener resourceTabClickListener;
    public ReviewListItem currentItem = null;
    public int layoutBuildNumber = -1;
    public TextWatcher editableTextWatcher;
    private List<TextView> mergeText;
    private OnResourceClickListener listener;
    private List<TranslationHelp> notes = new ArrayList<>();
    private List<TranslationHelp> questions = new ArrayList<>();
    private List<Link> words = new ArrayList<>();
    private float initialTextSize = 0;
    private int marginInitialLeft = 0;

    public IReviewListItemBinding binding;

    private enum MergeConflictDisplayState {
        NORMAL,
        SELECTED,
        DESELECTED
    }

    @SuppressLint("ClickableViewAccessibility")
    public ReviewHolder(IReviewListItemBinding binding) {
        super(binding.getRoot());
        this.binding = binding;

        context = binding.getRoot().getContext();
        inflater = LayoutInflater.from(context);
        
        resourceTabClickListener = new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int tag = (int) tab.getTag();
                if(listener == null) return;

                if (tag == TAB_NOTES) {
                    listener.onResourceTabNotesSelected(ReviewHolder.this, currentItem);
                } else if (tag == TAB_WORDS) {
                    listener.onResourceTabWordsSelected(ReviewHolder.this, currentItem);
                } else if (tag == TAB_QUESTIONS) {
                    listener.onResourceTabQuestionsSelected(ReviewHolder.this, currentItem);
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                clearHelps();
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        };
        final GestureDetector resourceCardDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                if(listener != null) listener.onTapResourceCard();
                return true;
            }
        });
        binding.getResourceCard().setOnTouchListener((v, event) -> resourceCardDetector.onTouchEvent(event));
    }

    /**
     * Returns the full width of the resource card
     * @return
     */
    public int getResourceCardWidth() {
        int rightMargin = ((ViewGroup.MarginLayoutParams)binding.getResourceCard().getLayoutParams()).rightMargin;
        return binding.getResourceCard().getWidth() + rightMargin;
    }

    public void showLoadingResources() {
        clearHelps();
        binding.getResourceTabs().removeAllTabs();

        RelativeLayout layout = new RelativeLayout(context);
        ProgressBar progressBar = new ProgressBar(context,null,android.R.attr.progressBarStyleLarge);
        progressBar.setIndeterminate(true);
        progressBar.setVisibility(View.VISIBLE);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(100,100);
        params.addRule(RelativeLayout.CENTER_IN_PARENT);
        layout.addView(progressBar, params);
        binding.getResourceList().addView(layout);
    }

    public void showLoadingSource() {
        binding.getSourceBody().setText("");
        binding.getSourceBody().setVisibility(View.GONE);
        binding.getSourceLoader().setVisibility(View.VISIBLE);
    }

    public void setSource(CharSequence sourceText) {
        binding.getSourceBody().setText(sourceText);
        binding.getSourceBody().setVisibility(View.VISIBLE);
        binding.getSourceLoader().setVisibility(View.GONE);
    }

    public void setResources(Language language, List<TranslationHelp> notes, List<TranslationHelp> questions, List<Link> words) {
        this.notes = notes;
        this.questions = questions;
        this.words = words;
        clearHelps();
        binding.getResourceTabs().removeOnTabSelectedListener(resourceTabClickListener);

        // rebuild tabs
        binding.getResourceTabs().removeAllTabs();
        if(!notes.isEmpty()) {
            TabLayout.Tab tab = binding.getResourceTabs().newTab();
            tab.setText(R.string.label_translation_notes);
            tab.setTag(TAB_NOTES);
            binding.getResourceTabs().addTab(tab);
        }
        if(!words.isEmpty()) {
            TabLayout.Tab tab = binding.getResourceTabs().newTab();
            tab.setText(R.string.translation_words);
            tab.setTag(TAB_WORDS);
            binding.getResourceTabs().addTab(tab);
        }
        if(!questions.isEmpty()) {
            TabLayout.Tab tab = binding.getResourceTabs().newTab();
            tab.setText(R.string.questions);
            tab.setTag(TAB_QUESTIONS);
            binding.getResourceTabs().addTab(tab);
        }

        // select default tab
        if(binding.getResourceTabs().getTabCount() > 0 ) {
            TabLayout.Tab tab = binding.getResourceTabs().getTabAt(0);
            if(tab != null) {
                tab.select();
                Object tag = tab.getTag();
                // show the contents
                switch((int)tab.getTag()) {
                    case TAB_NOTES:
                        showNotes(language);
                        break;
                    case TAB_WORDS:
                        showWords(language);
                        break;
                    case TAB_QUESTIONS:
                        showQuestions(language);
                        break;
                }
            }
        }
        binding.getResourceTabs().addOnTabSelectedListener(resourceTabClickListener);
    }

    /**
     * Removes the tabs and all the loaded resources from the resource tab
     */
    public void clearResourceCard() {
        clearHelps();
        binding.getResourceTabs().removeOnTabSelectedListener(resourceTabClickListener);
        binding.getResourceTabs().removeAllTabs();
        notes = new ArrayList<>();
        questions = new ArrayList<>();
        words = new ArrayList<>();
    }

    private void clearHelps() {
        if(binding.getResourceList().getChildCount() > 0) binding.getResourceList().removeAllViews();
    }

    /**
     * Displays the notes
     * @param language
     */
    public void showNotes(Language language) {
        clearHelps();
        for(final TranslationHelp note: notes) {
            // TODO: 2/28/17 it would be better if we could build this in code
            FragmentResourcesListItemBinding notesBinding = FragmentResourcesListItemBinding.inflate(inflater);
            notesBinding.getRoot().setText(note.title);
            notesBinding.getRoot().setOnClickListener(v -> {
                if (listener != null) {
                    listener.onNoteClick(note, getResourceCardWidth());
                }
            });
            Typography.formatSub(
                    context,
                    TranslationType.SOURCE,
                    notesBinding.getRoot(),
                    language.slug,
                    language.direction
            );
            binding.getResourceList().addView(notesBinding.getRoot());
        }
    }

    /**
     * Displays the words
     * @param language
     */
    public void showWords(final Language language) {
        clearHelps();
        for(final Link word: words) {
            ResourceContainer rc = ContainerCache.cacheClosest(App.getLibrary(), language.slug, word.project, word.resource);
            FragmentResourcesListItemBinding wordsBinding = FragmentResourcesListItemBinding.inflate(inflater);
            wordsBinding.getRoot().setText(word.title);
            wordsBinding.getRoot().setOnClickListener(v -> {
                if (listener != null && rc != null) {
                    listener.onWordClick(rc.slug, word, getResourceCardWidth());
                }
            });
            Typography.formatSub(context, TranslationType.SOURCE, wordsBinding.getRoot(), language.slug, language.direction);
            binding.getResourceList().addView(wordsBinding.getRoot());
        }
    }

    /**
     * Displays the questions
     * @param language
     */
    public void showQuestions(Language language) {
        clearHelps();
        for(final TranslationHelp question: questions) {
            FragmentResourcesListItemBinding questionsBinding = FragmentResourcesListItemBinding.inflate(inflater);
            questionsBinding.getRoot().setText(question.title);
            questionsBinding.getRoot().setOnClickListener(v -> {
                if (listener != null) {
                    listener.onQuestionClick(question, getResourceCardWidth());
                }
            });
            Typography.formatSub(context, TranslationType.SOURCE, questionsBinding.getRoot(), language.slug, language.direction);
            binding.getResourceList().addView(questionsBinding.getRoot());
        }
    }

    /**
     * set up the merge conflicts on the card
     * @param task
     * @param item
     */
    public void displayMergeConflictsOnTargetCard(Language language, MergeConflictsParseTask task, final ReviewListItem item) {
        item.mergeItems = task.getMergeConflictItems();

        if(mergeText != null) { // if previously rendered (could be recycled view)
            while (mergeText.size() > item.mergeItems.size()) { // if too many items, remove extras
                int lastPosition = mergeText.size() - 1;
                TextView v = mergeText.get(lastPosition);
                if (binding.getMergeConflictLayout() != null) {
                    binding.getMergeConflictLayout().removeView(v);
                }
                mergeText.remove(lastPosition);
            }
        } else {
            mergeText = new ArrayList<>();
        }

        int tailColor = context.getResources().getColor(R.color.accent_light);

        for(int i = 0; i < item.mergeItems.size(); i++) {
            boolean createNewCard = (i >= mergeText.size());
            TextView textView = null;

            if(createNewCard) {
                // create new card
                if (binding.getMergeConflictLayout() != null) {
                    FragmentMergeCardBinding mergeBinding = FragmentMergeCardBinding.inflate(inflater);
                    textView = mergeBinding.getRoot();

                    binding.getMergeConflictLayout().addView(textView);
                    mergeText.add(textView);

                    if (i % 2 == 1) { //every other card is different color
                        textView.setBackgroundColor(tailColor);
                    }
                }
            } else {
                textView = mergeText.get(i); // get previously created card
            }

            if(initialTextSize == 0 && textView != null) { // see if we need to initialize values
                initialTextSize = Typography.getFontSize(context, TranslationType.SOURCE);
                marginInitialLeft = getLeftMargin(textView);
            }

            Typography.format(context, TranslationType.SOURCE, textView, language.slug, language.direction);

            final int pos = i;
            if (textView != null) {
                textView.setOnClickListener(v -> {
                    item.mergeItemSelected = pos;
                    displayMergeConflictSelectionState(item);
                });
            }
        }

        displayMergeConflictSelectionState(item);
    }

    /**
     * set merge conflict selection state
     * @param item
     */
    public void displayMergeConflictSelectionState(ReviewListItem item) {
        for(int i = 0; i < item.mergeItems.size(); i++ ) {
            CharSequence mergeConflictCard = item.mergeItems.get(i);
            TextView textView = mergeText.get(i);

            if (item.mergeItemSelected >= 0) {
                if (item.mergeItemSelected == i) {
                    displayMergeSelectionState(MergeConflictDisplayState.SELECTED, textView, mergeConflictCard);
                    if (binding.getConflictText() != null) binding.getConflictText().setVisibility(View.GONE);
                    if (binding.getButtonBar() != null) binding.getButtonBar().setVisibility(View.VISIBLE);
                } else {
                    displayMergeSelectionState(MergeConflictDisplayState.DESELECTED, textView, mergeConflictCard);
                    if (binding.getConflictText() != null) binding.getConflictText().setVisibility(View.GONE);
                    if (binding.getButtonBar() != null) binding.getButtonBar().setVisibility(View.VISIBLE);
                }

            } else {
                displayMergeSelectionState(MergeConflictDisplayState.NORMAL, textView, mergeConflictCard);
                if (binding.getConflictText() != null) binding.getConflictText().setVisibility(View.VISIBLE);
                if (binding.getButtonBar() != null) binding.getButtonBar().setVisibility(View.GONE);
            }
        }
    }

    /**
     * display the selection state for card
     * @param state
     */
    private void displayMergeSelectionState(MergeConflictDisplayState state, TextView view, CharSequence text) {

        SpannableStringBuilder span;

        switch (state) {
            case SELECTED:
                setHorizontalMargin( view, marginInitialLeft); // shrink margins to emphasize
                span = new SpannableStringBuilder(text);
                // bold text to emphasize
                view.setTextSize(TypedValue.COMPLEX_UNIT_SP, initialTextSize); // grow text to emphasize
                span.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, span.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                view.setText(span);
                break;

            case DESELECTED:
                setHorizontalMargin( view, 2 * marginInitialLeft); // grow margins to de-emphasize
                span = new SpannableStringBuilder(text);
                // set text gray to de-emphasize
                span.setSpan(new ForegroundColorSpan(context.getResources().getColor(R.color.dark_disabled_text)), 0, span.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                view.setTextSize(TypedValue.COMPLEX_UNIT_SP, initialTextSize * 0.8f); // shrink text to de-emphasize
                view.setText(span);
                break;

            case NORMAL:
            default:
                setHorizontalMargin( view, marginInitialLeft); // restore original margins
                view.setTextSize(TypedValue.COMPLEX_UNIT_SP, initialTextSize); // restore initial test size
                view.setText(text); // remove text emphasis
                break;
        }
    }

    /**
     * Sets the left and right margins on a view
     *
     * @param view the view to receive the margin
     * @param margin the new margin
     */
    private void setHorizontalMargin(TextView view, int margin) {
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        params.leftMargin = margin;
        params.rightMargin = margin;
        view.requestLayout();
    }

    /**
     * get the left margin for view
     * @param v
     * @return
     */
    private int getLeftMargin(View v) {
        ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
        return p.leftMargin;
    }

    /**
     * Shows/hides the resource card
     * @param show
     */
    public void showResourceCard(boolean show) {
        showResourceCard(show, false);
    }

    /**
     * Shows/hides the resource card
     * @param show will be shown if true
     * @param animate animates the change
     */
    public void showResourceCard(final boolean show, boolean animate) {
        float openWeight = 1f;
        float closedWeight = 0.765f;
        if(animate) {
            int duration = 400;
            if(binding.getMainContent().getAnimation() != null) binding.getMainContent().getAnimation().cancel();
            binding.getMainContent().clearAnimation();
            ObjectAnimator anim;
            if(show) {
                binding.getResourceLayout().setVisibility(View.VISIBLE);
                anim = ObjectAnimator.ofFloat(binding.getMainContent(), "weightSum", openWeight, closedWeight);
            } else {
                binding.getResourceLayout().setVisibility(View.INVISIBLE);
                anim = ObjectAnimator.ofFloat(binding.getMainContent(), "weightSum", closedWeight, openWeight);
            }
            anim.setDuration(duration);
            anim.addUpdateListener(animation -> binding.getMainContent().requestLayout());
            anim.start();
        } else {
            if(show) {
                binding.getResourceLayout().setVisibility(View.VISIBLE);
                binding.getMainContent().setWeightSum(closedWeight);
            } else {
                binding.getResourceLayout().setVisibility(View.INVISIBLE);
                binding.getMainContent().setWeightSum(openWeight);
            }
        }
    }

    public void renderSourceTabs(List<ContentValues> tabs) {
        binding.getTranslationTabs().setOnTabSelectedListener(null);
        binding.getTranslationTabs().removeAllTabs();
        for(ContentValues values:tabs) {
            String tag = values.getAsString("tag");
            String title = values.getAsString("title");
            View tabLayout = ViewModeAdapter.createRemovableTabLayout(context, listener, tag, title);

            TabLayout.Tab tab = binding.getTranslationTabs().newTab();
            tab.setTag(tag);
            tab.setCustomView(tabLayout);
            binding.getTranslationTabs().addTab(tab);

            ViewModeAdapter.applyLanguageTypefaceToTab(context, binding.getTranslationTabs(), values, title);
        }

        // open selected tab
        for(int i = 0; i < binding.getTranslationTabs().getTabCount(); i ++) {
            TabLayout.Tab tab = binding.getTranslationTabs().getTabAt(i);
            if(tab.getTag().equals(currentItem.source.slug)) {
                tab.select();
                break;
            }
        }

        // tabs listener
        binding.getTranslationTabs().setOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                final String sourceTranslationId = (String) tab.getTag();
                if (listener != null) {
                    listener.onSourceTranslationTabClick(sourceTranslationId);
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        // change tabs listener
        binding.getNewTabButton().setOnClickListener(v -> {
            if (listener != null) {
                listener.onNewSourceTranslationTabClick();
            }
        });
    }

    /**
     * get appropriate edit text - it is different when editing versus viewing
     * @return
     */
    public EditText getEditText() {
        if (!currentItem.isEditing) {
            return binding.getTargetBody();
        } else {
            return binding.getTargetEditableBody();
        }
    }

    /**
     * Sets the correct ui state for translation controls
     */
    public void rebuildControls() {
        if(currentItem.isEditing) {
            prepareUndoRedoUI();

            boolean allowFootnote =
                    currentItem.getTargetTranslationFormat() == TranslationFormat.USFM &&
                            currentItem.isChunk();
            if(binding.getEditButton() != null) {
                binding.getEditButton().setImageResource(R.drawable.ic_done_secondary_24dp);
            }
            if(binding.getAddNoteButton() != null) {
                binding.getAddNoteButton().setVisibility(allowFootnote ? View.VISIBLE : View.GONE);
            }
            if(binding.getUndoButton() != null) binding.getUndoButton().setVisibility(View.GONE);
            if(binding.getRedoButton() != null) binding.getRedoButton().setVisibility(View.GONE);
            if(binding.getTargetBody() != null) binding.getTargetBody().setVisibility(View.GONE);
            if(binding.getTargetEditableBody() != null) {
                binding.getTargetEditableBody().setVisibility(View.VISIBLE);
                binding.getTargetEditableBody().setEnableLines(true);
            }
        } else {
            if(binding.getEditButton() != null) {
                binding.getEditButton().setImageResource(R.drawable.ic_mode_edit_secondary_24dp);
            }
            if(binding.getUndoButton() != null) binding.getUndoButton().setVisibility(View.GONE);
            if(binding.getRedoButton() != null) binding.getRedoButton().setVisibility(View.GONE);
            if(binding.getAddNoteButton() != null) binding.getAddNoteButton().setVisibility(View.GONE);
            if(binding.getTargetBody() != null) binding.getTargetBody().setVisibility(View.VISIBLE);
            if(binding.getTargetEditableBody() != null) {
                binding.getTargetEditableBody().setVisibility(View.GONE);
                binding.getTargetEditableBody().setEnableLines(false);
            }
        }
    }

    /**
     * check history to see if we should show undo/redo buttons
     */
    private void prepareUndoRedoUI() {
        final FileHistory history = currentItem.getFileHistory();
        ThreadableUI thread = new ThreadableUI(context) {
            @Override
            public void onStop() {
            }

            @Override
            public void run() {
                try {
                    if (history != null) history.loadCommits();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (GitAPIException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onPostExecute() {
                if (binding.getRedoButton() != null) {
                    if (history != null) {
                        if(history.hasNext()) {
                            binding.getRedoButton().setVisibility(View.VISIBLE);
                        } else {
                            binding.getRedoButton().setVisibility(View.GONE);
                        }
                    }
                }

                if (binding.getUndoButton() != null) {
                    if (history != null) {
                        if(history.hasPrevious()) {
                            binding.getUndoButton().setVisibility(View.VISIBLE);
                        } else {
                            binding.getUndoButton().setVisibility(View.GONE);
                        }
                    }
                }
            }
        };
        thread.start();
    }

    public void setOnClickListener(OnResourceClickListener listener) {
        this.listener = listener;
    }
}
