package com.door43.translationstudio.ui.translate.review;

import static com.door43.translationstudio.ui.translate.ChooseSourceTranslationAdapter.MAX_SOURCE_ITEMS;

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.text.Editable;
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

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.door43.translationstudio.R;
import com.door43.translationstudio.core.FileHistory;
import com.door43.translationstudio.core.TranslationFormat;
import com.door43.translationstudio.core.TranslationType;
import com.door43.translationstudio.core.Typography;
import com.door43.translationstudio.databinding.FragmentMergeCardBinding;
import com.door43.translationstudio.databinding.FragmentResourcesListItemBinding;
import com.door43.translationstudio.ui.translate.IReviewListItemBinding;
import com.door43.translationstudio.ui.translate.ReviewListItem;
import com.door43.translationstudio.ui.translate.ReviewModeAdapter;
import com.door43.translationstudio.ui.translate.TranslationHelp;
import com.door43.usecases.ParseMergeConflicts;
import com.door43.widget.ViewUtil;
import com.google.android.material.tabs.TabLayout;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.unfoldingword.resourcecontainer.Language;
import org.unfoldingword.resourcecontainer.Link;
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
    private final TabLayout.OnTabSelectedListener tabSelectedListener;
    private List<TextView> mergeTexts;
    private final OnReviewModeListener reviewModeListener;
    private List<TranslationHelp> notes = new ArrayList<>();
    private List<TranslationHelp> questions = new ArrayList<>();
    private List<Link> words = new ArrayList<>();
    private float initialTextSize = 0;
    private int marginInitialLeft = 0;

    public IReviewListItemBinding binding;
    private final Typography typography;

    private final TextWatcher editableTextWatcher;

    private enum MergeConflictDisplayState {
        NORMAL,
        SELECTED,
        DESELECTED
    }

    @SuppressLint("ClickableViewAccessibility")
    public ReviewHolder(
            IReviewListItemBinding binding,
            Typography typography,
            OnReviewModeListener reviewModeListener
    ) {
        super(binding.getRoot());
        this.binding = binding;

        this.reviewModeListener = reviewModeListener;

        this.typography = typography;
        context = binding.getRoot().getContext();
        inflater = LayoutInflater.from(context);

        final GestureDetector editButtonDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(@NonNull MotionEvent e) {
                if (reviewModeListener != null) {
                    reviewModeListener.onEditorToggle(ReviewHolder.this);
                }
                return true;
            }
        });

        resourceTabClickListener = new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int tag = (int) tab.getTag();
                if(reviewModeListener == null) return;

                if (tag == TAB_NOTES) {
                    reviewModeListener.onResourceTabNotesSelected(ReviewHolder.this);
                } else if (tag == TAB_WORDS) {
                    reviewModeListener.onResourceTabWordsSelected(ReviewHolder.this);
                } else if (tag == TAB_QUESTIONS) {
                    reviewModeListener.onResourceTabQuestionsSelected(ReviewHolder.this);
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

        tabSelectedListener = new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                final String sourceTranslationId = (String) tab.getTag();
                if (reviewModeListener != null) {
                    reviewModeListener.onSourceTranslationTabClick(sourceTranslationId);
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        };

        final GestureDetector resourceCardDetector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                if(reviewModeListener != null) reviewModeListener.onTapResourceCard();
                return true;
            }
        });

        editableTextWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (binding.getTargetEditableBody() != null && binding.getTargetEditableBody().hasFocus()) {
                    if (reviewModeListener != null) {
                        reviewModeListener.onApplyChangedText(s, ReviewHolder.this);
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };

        // Attach listeners when view is created
        itemView.post(() -> {
            if (binding.getEditButton() != null) {
                binding.getEditButton().setOnTouchListener((v, event) -> editButtonDetector.onTouchEvent(event));
            }

            binding.getResourceCard().setOnTouchListener((v, event) -> resourceCardDetector.onTouchEvent(event));

            if (binding.getTargetBody() != null) {
                binding.getTargetBody().setOnTouchListener((v, event) -> {
                    v.onTouchEvent(event);
                    v.clearFocus();
                    return true;
                });
            }

            if (binding.getUndoButton() != null) {
                binding.getUndoButton().setOnClickListener(v -> {
                    if (reviewModeListener != null) {
                        reviewModeListener.onUndoTextInTarget(this);
                    }
                });
            }

            if (binding.getRedoButton() != null) {
                binding.getRedoButton().setOnClickListener(v -> {
                    if (reviewModeListener != null) {
                        reviewModeListener.onRedoTextInTarget(this);
                    }
                });
            }

            if (binding.getDoneSwitch() != null) {
                binding.getDoneSwitch().setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (reviewModeListener != null && buttonView.isPressed()) {
                        reviewModeListener.onDoneSwitchClicked(this, isChecked);
                    }
                });
            }

            if (binding.getAddNoteButton() != null) {
                binding.getAddNoteButton().setOnClickListener(v -> {
                    if (reviewModeListener != null) {
                        reviewModeListener.onCreateFootnoteAtSelection(this);
                    }
                });
            }

            if (binding.getCancelButton() != null) {
                binding.getCancelButton().setOnClickListener(v -> {
                    int position = getBindingAdapterPosition();
                    if (reviewModeListener != null && position != RecyclerView.NO_POSITION) {
                        reviewModeListener.onMergeConflictItemCancel(position);
                    }
                });
            }

            if (binding.getConfirmButton() != null) {
                binding.getConfirmButton().setOnClickListener(v -> {
                    int position = getBindingAdapterPosition();
                    if (reviewModeListener != null && position != RecyclerView.NO_POSITION) {
                        reviewModeListener.onMergeConflictItemConfirm(position);
                    }
                });
            }

            // change tabs listener
            binding.getNewTabButton().setOnClickListener(v -> {
                if (reviewModeListener != null) {
                    reviewModeListener.onNewSourceTranslationTabClick();
                }
            });
        });
    }

    public void bind(ReviewListItem item) {
        showResourceCard(item.resourcesOpened, false);
        ViewUtil.makeLinksClickable(binding.getSourceBody());

        // render the cards
        renderSourceCard(item);

        if (getItemViewType() == ReviewModeAdapter.VIEW_TYPE_CONFLICT) {
            renderConflictingTargetCard(item);
        } else {
            renderTargetCard(item);
        }

        renderResourceCard(item);

        // set up fonts
        typography.format(
                TranslationType.SOURCE,
                binding.getSourceBody(),
                item.source.language.slug,
                item.source.language.direction
        );
        if (!item.getHasMergeConflicts()) {
            typography.format(
                    TranslationType.TARGET,
                    binding.getTargetBody(),
                    item.target.getTargetLanguage().slug,
                    item.target.getTargetLanguage().direction
            );
            typography.format(
                    TranslationType.TARGET,
                    binding.getTargetEditableBody(),
                    item.target.getTargetLanguage().slug,
                    item.target.getTargetLanguage().direction
            );
        } else {
            typography.formatSub(
                    TranslationType.TARGET,
                    binding.getConflictText(),
                    item.target.getTargetLanguage().slug,
                    item.target.getTargetLanguage().direction
            );
        }
        typography.formatSub(
                TranslationType.TARGET,
                binding.getTargetTitle(),
                item.target.getTargetLanguage().slug,
                item.target.getTargetLanguage().direction
        );
    }

    public void attachTextChangeListener() {
        if (binding.getTargetEditableBody() != null) {
            binding.getTargetEditableBody().removeTextChangedListener(editableTextWatcher);
            binding.getTargetEditableBody().addTextChangedListener(editableTextWatcher);
        }
    }

    public void removeTextChangeListener() {
        if (binding.getTargetEditableBody() != null) {
            binding.getTargetEditableBody().removeTextChangedListener(editableTextWatcher);
        }
    }

    private void renderSourceCard(final ReviewListItem item) {
        if (item.renderedSourceText == null) {
            showLoadingSource();
        } else {
            setSource(item.renderedSourceText);
        }

        if (reviewModeListener != null) {
            CharSequence renderedText = reviewModeListener.onRenderSourceText(item);
            item.renderedSourceText = renderedText;
            setSource(renderedText);

            // update the search
            int position = getBindingAdapterPosition();
            if (position != RecyclerView.NO_POSITION) {
                reviewModeListener.onSearchItemUpdated(position, binding.getSourceBody(), false);
            }
        }

        List<ContentValues> tabs = item.getTabs();
        renderSourceTabs(tabs, item.source.slug);

        if (tabs.size() >= MAX_SOURCE_ITEMS) {
            binding.getNewTabButton().setVisibility(View.GONE);
        } else {
            binding.getNewTabButton().setVisibility(View.VISIBLE);
        }
    }

    /**
     * Renders a target card that has merge conflicts
     *
     * @param item the review list item
     */
    private void renderConflictingTargetCard(final ReviewListItem item) {
        // render title
        binding.getTargetTitle().setText(item.getTargetTitle());
        if (binding.getMergeConflictLayout() == null) { // sanity check
            return;
        }

        displayMergeConflictsOnTargetCard(item);
        rebuildControls(item);

        if (binding.getUndoButton() != null) {
            binding.getUndoButton().setVisibility(View.GONE);
        }
        if (binding.getRedoButton() != null) {
            binding.getRedoButton().setVisibility(View.GONE);
        }
    }

    /**
     * Renders a normal target card
     *
     * @param item the review list item
     */
    @SuppressLint("ClickableViewAccessibility")
    private void renderTargetCard(final ReviewListItem item) {
        // Remove text change listener before rendering
        removeTextChangeListener();
        rebuildControls(item);

        // insert rendered text
        if (item.isEditing) {
            // editing mode
            if (binding.getTargetEditableBody() != null) {
                binding.getTargetEditableBody().setText(item.renderedTargetText);
            }
        } else {
            // verse marker mode
            if (binding.getTargetBody() != null) {
                binding.getTargetBody().setText(item.renderedTargetText);
                ViewUtil.makeLinksClickable(binding.getTargetBody());
                binding.getTargetBody().setEnabled(!item.isDisabled);
            }
        }

        // title
        binding.getTargetTitle().setText(item.getTargetTitle());

        // render target body
        if (item.renderedTargetText == null) {
            if (binding.getTargetEditableBody() != null) {
                binding.getTargetEditableBody().setText(item.getTargetText());
            }
            if (binding.getTargetBody() != null) {
                binding.getTargetBody().setText(item.getTargetText());
            }

            if (reviewModeListener != null) {
                CharSequence text;
                if (item.isComplete() || item.isEditing) {
                    text = reviewModeListener.onRenderTargetText(this, item, true);
                } else {
                    text = reviewModeListener.onRenderTargetText(this, item);
                }
                item.renderedTargetText = text;
            }

            if (item.isEditing) {
                // edit mode
                if (binding.getTargetEditableBody() != null) {
                    binding.getTargetEditableBody().setText(item.renderedTargetText);
                    if (reviewModeListener != null) {
                        int position = getBindingAdapterPosition();
                        if (position != RecyclerView.NO_POSITION) {
                            reviewModeListener.onSearchItemUpdated(position, binding.getTargetEditableBody(), true);
                        }
                    }
                }
            } else {
                // verse marker mode
                if (binding.getTargetBody() != null) {
                    binding.getTargetBody().setText(item.renderedTargetText);
                    if (reviewModeListener != null) {
                        int position = getBindingAdapterPosition();
                        if (position != RecyclerView.NO_POSITION) {
                            reviewModeListener.onSearchItemUpdated(position, binding.getTargetBody(), true);
                        }
                    }
                    binding.getTargetBody().setOnTouchListener((v, event) -> {
                        v.onTouchEvent(event);
                        v.clearFocus();
                        return true;
                    });
                    setFinishedMode(item.isComplete());
                    ViewUtil.makeLinksClickable(binding.getTargetBody());
                }
            }

            if (reviewModeListener != null) {
                reviewModeListener.onAddMissingVerses(this);
            }
        } else if (item.isEditing) {
            // editing mode
            if (binding.getTargetEditableBody() != null) {
                if (reviewModeListener != null) {
                    item.renderedTargetText = reviewModeListener.onRenderTargetText(this, item, true);
                }
                binding.getTargetEditableBody().setText(item.renderedTargetText);
            }
            if (item.refreshSearchHighlightTarget && reviewModeListener != null) {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    reviewModeListener.onSearchItemUpdated(position, binding.getTargetEditableBody(), true);
                }
            }
        } else {
            // verse marker mode
            if (binding.getTargetBody() != null) {
                binding.getTargetBody().setText(item.renderedTargetText);
                ViewUtil.makeLinksClickable(binding.getTargetBody());
            }
            if (item.refreshSearchHighlightTarget && reviewModeListener != null) {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    reviewModeListener.onSearchItemUpdated(position, binding.getTargetBody(), true);
                }
            }
        }

        // Reattach text change listener
        attachTextChangeListener();

        // display as finished
        itemView.post(() -> setFinishedMode(item.isComplete()));
    }

    /**
     * Initiates rendering the resource card
     *
     * @param item the review list item
     */
    private void renderResourceCard(final ReviewListItem item) {
        clearResourceCard();

        // skip if chapter title/reference or udb
        if (!item.isChunk() || item.source.resource.slug.equals("udb")) {
            return;
        }

        showLoadingResources();

        if (reviewModeListener != null) {
            reviewModeListener.onRenderHelps(item);
        }
    }

    /**
     * Returns the full width of the resource card
     * @return resource card width
     */
    public int getResourceCardWidth() {
        int rightMargin = ((ViewGroup.MarginLayoutParams)binding.getResourceCard().getLayoutParams()).rightMargin;
        return binding.getResourceCard().getWidth() + rightMargin;
    }

    private void showLoadingResources() {
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

    private void showLoadingSource() {
        binding.getSourceBody().setText("");
        binding.getSourceBody().setVisibility(View.GONE);
        binding.getSourceLoader().setVisibility(View.VISIBLE);
    }

    private void setSource(CharSequence sourceText) {
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
                switch((int)tag) {
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
     * set the UI to reflect the finished mode
     *
     * @param isComplete if the item is complete
     */
    private void setFinishedMode(boolean isComplete) {
        if (isComplete) {
            if (binding.getEditButton() != null)
                binding.getEditButton().setVisibility(View.GONE);
            if (binding.getUndoButton() != null)
                binding.getUndoButton().setVisibility(View.GONE);
            if (binding.getRedoButton() != null)
                binding.getRedoButton().setVisibility(View.GONE);
            if (binding.getAddNoteButton() != null)
                binding.getAddNoteButton().setVisibility(View.GONE);
            if (binding.getDoneSwitch() != null)
                binding.getDoneSwitch().setChecked(true);
            binding.getTargetInnerCard().setBackgroundResource(R.color.card_background_color);
        } else {
            if (binding.getEditButton() != null)
                binding.getEditButton().setVisibility(View.VISIBLE);
            if (binding.getDoneSwitch() != null)
                binding.getDoneSwitch().setChecked(false);
        }
    }

    /**
     * Removes the tabs and all the loaded resources from the resource tab
     */
    private void clearResourceCard() {
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
     * @param language language
     */
    public void showNotes(Language language) {
        clearHelps();
        for(final TranslationHelp note: notes) {
            // TODO: 2/28/17 it would be better if we could build this in code
            FragmentResourcesListItemBinding notesBinding = FragmentResourcesListItemBinding.inflate(inflater);
            notesBinding.getRoot().setText(note.title);
            notesBinding.getRoot().setOnClickListener(v -> {
                if (reviewModeListener != null) {
                    reviewModeListener.onNoteClick(note, getResourceCardWidth());
                }
            });
            typography.formatSub(
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
     * @param language language
     */
    public void showWords(final Language language) {
        clearHelps();
        for(final Link word: words) {
            String rcSlug = language.slug + "_" + word.project + "_" + word.resource;
            FragmentResourcesListItemBinding wordsBinding = FragmentResourcesListItemBinding.inflate(inflater);
            wordsBinding.getRoot().setText(word.title);
            wordsBinding.getRoot().setOnClickListener(v -> {
                if (reviewModeListener != null) {
                    reviewModeListener.onWordClick(rcSlug, word, getResourceCardWidth());
                }
            });
            typography.formatSub(TranslationType.SOURCE, wordsBinding.getRoot(), language.slug, language.direction);
            binding.getResourceList().addView(wordsBinding.getRoot());
        }
    }

    /**
     * Displays the questions
     * @param language language
     */
    public void showQuestions(Language language) {
        clearHelps();
        for(final TranslationHelp question: questions) {
            FragmentResourcesListItemBinding questionsBinding = FragmentResourcesListItemBinding.inflate(inflater);
            questionsBinding.getRoot().setText(question.title);
            questionsBinding.getRoot().setOnClickListener(v -> {
                if (reviewModeListener != null) {
                    reviewModeListener.onQuestionClick(question, getResourceCardWidth());
                }
            });
            typography.formatSub(TranslationType.SOURCE, questionsBinding.getRoot(), language.slug, language.direction);
            binding.getResourceList().addView(questionsBinding.getRoot());
        }
    }

    /**
     * set up the merge conflicts on the card
     * @param item the review list item
     */
    private void displayMergeConflictsOnTargetCard(final ReviewListItem item) {
        Language language = item.source.language;
        item.mergeItems = ParseMergeConflicts.INSTANCE.execute(item.getTargetText());

        if(mergeTexts != null) { // if previously rendered (could be recycled view)
            while (mergeTexts.size() > item.mergeItems.size()) { // if too many items, remove extras
                int lastPosition = mergeTexts.size() - 1;
                TextView v = mergeTexts.get(lastPosition);
                if (binding.getMergeConflictLayout() != null) {
                    binding.getMergeConflictLayout().removeView(v);
                }
                mergeTexts.remove(lastPosition);
            }
        } else {
            mergeTexts = new ArrayList<>();
        }

        int tailColor = context.getResources().getColor(R.color.accent_light);

        for(int i = 0; i < item.mergeItems.size(); i++) {
            boolean createNewCard = (i >= mergeTexts.size());
            TextView textView = null;

            if(createNewCard) {
                // create new card
                if (binding.getMergeConflictLayout() != null) {
                    FragmentMergeCardBinding mergeBinding = FragmentMergeCardBinding.inflate(inflater);
                    textView = mergeBinding.getRoot();

                    binding.getMergeConflictLayout().addView(textView);
                    mergeTexts.add(textView);

                    if (i % 2 == 1) { //every other card is different color
                        textView.setBackgroundColor(tailColor);
                    }
                }
            } else {
                textView = mergeTexts.get(i); // get previously created card
            }

            if(initialTextSize == 0 && textView != null) { // see if we need to initialize values
                initialTextSize = typography.getFontSize(TranslationType.SOURCE);
                marginInitialLeft = getLeftMargin(textView);
            }

            typography.format(TranslationType.SOURCE, textView, language.slug, language.direction);

            final int selectedIndex = i;
            if (textView != null) {
                textView.setOnClickListener(v -> {
                    item.mergeItemSelected = selectedIndex;
                    if (reviewModeListener != null) {
                        int position = getBindingAdapterPosition();
                        if (position != RecyclerView.NO_POSITION) {
                            reviewModeListener.onNotifyItemChanged(position);
                        }
                    }
                });
            }
        }

        displayMergeConflictSelectionState(item);
    }

    /**
     * set merge conflict selection state
     //* @param item
     */
    private void displayMergeConflictSelectionState(ReviewListItem item) {
        for(int i = 0; i < item.mergeItems.size(); i++ ) {
            CharSequence mergeConflictCard = item.mergeItems.get(i);
            TextView textView = mergeTexts.get(i);

            if (item.mergeItemSelected >= 0) {
                if (item.mergeItemSelected == i) {
                    displayMergeSelectionState(MergeConflictDisplayState.SELECTED, textView, mergeConflictCard);
                } else {
                    displayMergeSelectionState(MergeConflictDisplayState.DESELECTED, textView, mergeConflictCard);
                }
                if (binding.getConflictText() != null) {
                    binding.getConflictText().setVisibility(View.GONE);
                }
                if (binding.getButtonBar() != null) {
                    binding.getButtonBar().setVisibility(View.VISIBLE);
                }
            } else {
                displayMergeSelectionState(MergeConflictDisplayState.NORMAL, textView, mergeConflictCard);
                if (binding.getConflictText() != null) {
                    binding.getConflictText().setVisibility(View.VISIBLE);
                }
                if (binding.getButtonBar() != null) {
                    binding.getButtonBar().setVisibility(View.GONE);
                }
            }
        }
    }

    /**
     * display the selection state for card
     * @param state the merge conflict display state
     * @param view the view to display the state on
     * @param text the text to display
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
     * @param v view
     * @return the left margin
     */
    private int getLeftMargin(View v) {
        ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
        return p.leftMargin;
    }

    /**
     * Shows/hides the resource card
     * @param show will be shown if true
     * @param animate animates the change
     */
    private void showResourceCard(final boolean show, boolean animate) {
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

    private void renderSourceTabs(List<ContentValues> tabs, String sourceSlug) {
        binding.getTranslationTabs().removeOnTabSelectedListener(tabSelectedListener);
        binding.getTranslationTabs().removeAllTabs();

        for(ContentValues values:tabs) {
            String tag = values.getAsString("tag");
            String title = values.getAsString("title");

            if (reviewModeListener != null) {
                View tabLayout = reviewModeListener.onCreateRemovableTabLayout(tag, title);

                if (tabLayout != null) {
                    TabLayout.Tab tab = binding.getTranslationTabs().newTab();
                    tab.setTag(tag);
                    tab.setCustomView(tabLayout);
                    binding.getTranslationTabs().addTab(tab);
                }

                reviewModeListener.onApplyLanguageTypefaceToTab(binding.getTranslationTabs(), values, title);
            }
        }

        // open selected tab
        for(int i = 0; i < binding.getTranslationTabs().getTabCount(); i ++) {
            TabLayout.Tab tab = binding.getTranslationTabs().getTabAt(i);
            if(sourceSlug.equals(tab.getTag())) {
                tab.select();
                break;
            }
        }

        // tabs listener
        binding.getTranslationTabs().addOnTabSelectedListener(tabSelectedListener);
    }

    /**
     * get appropriate edit text - it is different when editing versus viewing
     * @return the edit text
     */
    public EditText getEditText(boolean isEditing) {
        if (!isEditing) {
            return binding.getTargetBody();
        } else {
            return binding.getTargetEditableBody();
        }
    }

    /**
     * Sets the correct ui state for translation controls
     */
    public void rebuildControls(ReviewListItem item) {
        if(item.isEditing) {
            prepareUndoRedoUI(item);

            boolean allowFootnote = item.getTargetTranslationFormat() == TranslationFormat.USFM && item.isChunk();
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
    private void prepareUndoRedoUI(ReviewListItem item) {
        final FileHistory history = item.getFileHistory();
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
}
