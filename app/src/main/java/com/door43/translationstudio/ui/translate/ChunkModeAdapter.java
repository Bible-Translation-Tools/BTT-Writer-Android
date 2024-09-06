package com.door43.translationstudio.ui.translate;

import static com.door43.translationstudio.ui.translate.ChooseSourceTranslationAdapter.MAX_SOURCE_ITEMS;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.door43.translationstudio.App;
import com.door43.translationstudio.R;
import com.door43.translationstudio.core.Frame;
import com.door43.translationstudio.core.TargetTranslation;
import com.door43.translationstudio.core.TranslationFormat;
import com.door43.translationstudio.core.TranslationType;
import com.door43.translationstudio.core.TranslationViewMode;
import com.door43.translationstudio.core.Translator;
import com.door43.translationstudio.core.Typography;
import com.door43.translationstudio.databinding.FragmentChunkListItemBinding;
import com.door43.translationstudio.rendering.ClickableRenderingEngine;
import com.door43.translationstudio.rendering.Clickables;
import com.door43.translationstudio.rendering.DefaultRenderer;
import com.door43.translationstudio.rendering.RenderingGroup;
import com.door43.translationstudio.tasks.CheckForMergeConflictsTask;
import com.door43.translationstudio.ui.spannables.NoteSpan;
import com.door43.translationstudio.ui.spannables.Span;
import com.door43.translationstudio.ui.translate.review.SearchSubject;
import com.door43.widget.ViewUtil;
import com.google.android.material.tabs.TabLayout;

import org.unfoldingword.door43client.Door43Client;
import org.unfoldingword.door43client.models.TargetLanguage;
import org.unfoldingword.door43client.models.Translation;
import org.unfoldingword.resourcecontainer.ResourceContainer;
import org.unfoldingword.tools.taskmanager.ManagedTask;
import org.unfoldingword.tools.taskmanager.TaskManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by joel on 9/9/2015.
 */
public class ChunkModeAdapter extends ViewModeAdapter<ChunkModeAdapter.ViewHolder> {
    public static final int HIGHLIGHT_COLOR = Color.YELLOW;
    private static final int BOTTOM_ELEVATION = 2;
    private static final int TOP_ELEVATION = 3;
    private final Activity mContext;
    private final TargetTranslation mTargetTranslation;
    private final Door43Client mLibrary;
    private final Translator mTranslator;
    private final TargetLanguage mTargetLanguage;
    private ResourceContainer mSourceContainer;
    private List<ListItem> mItems = new ArrayList<>();
    private List<ListItem> mFilteredItems = new ArrayList<>();
    private int mLayoutBuildNumber = 0;
    private ContentValues[] mTabs = new ContentValues[0];
    private List<String> mChapters = new ArrayList();
    private List<String> mFilteredChapters = new ArrayList<>();
    private final CharSequence filterConstraint = null;
    private final SearchSubject filterSubject = null;

    public ChunkModeAdapter(Activity context, String targetTranslationId, String startingChapterSlug, String startingChunkSlug, boolean openSelectedTarget) {
        this.startingChapterSlug = startingChapterSlug;
        this.startingChunkSlug = startingChunkSlug;

        mLibrary = App.getLibrary();
        mTranslator = App.getTranslator();
        mContext = context;
        mTargetTranslation = mTranslator.getTargetTranslation(targetTranslationId);
        mTargetLanguage = mTranslator.languageFromTargetTranslation(mTargetTranslation);
    }

    @Override
    public void setSourceContainer(ResourceContainer sourceContainer) {
        mSourceContainer = sourceContainer;
        mLayoutBuildNumber++; // force resetting of fonts

        mChapters = new ArrayList<>();
        mItems = new ArrayList<>();
        initializeListItems(mItems, mChapters, mSourceContainer);

        mFilteredItems = mItems;
        mFilteredChapters = mChapters;

        loadTabInfo();

        filter(filterConstraint, filterSubject, 0);

        triggerNotifyDataSetChanged();
        updateMergeConflict();
    }

    @Override
    public ListItem createListItem(String chapterSlug, String chunkSlug) {
        return new ChunkListItem(chapterSlug, chunkSlug);
    }

    /**
     * check all cards for merge conflicts to see if we should show warning.  Runs as background task.
     */
    private void updateMergeConflict() {
        doCheckForMergeConflictTask(mItems, mSourceContainer, mTargetTranslation);
    }

    @Override
    public void onTaskFinished(ManagedTask task) {
        TaskManager.clearTask(task);

        if (task instanceof CheckForMergeConflictsTask mergeConflictsTask) {

            final boolean mergeConflictFound = mergeConflictsTask.hasMergeConflict();
            Handler hand = new Handler(Looper.getMainLooper());
            hand.post(() -> {
                OnEventListener listener = getListener();
                if (listener != null) {
                    listener.onEnableMergeConflict(mergeConflictFound, false);
                }
            });
        }
    }

    /**
     * Rebuilds the card tabs
     */
    private void loadTabInfo() {
        List<ContentValues> tabContents = new ArrayList<>();
        String[] sourceTranslationIds = mTranslator.getOpenSourceTranslations(mTargetTranslation.getId());
        for (String slug : sourceTranslationIds) {
            Translation st = mLibrary.index().getTranslation(slug);
            if (st != null) {
                ContentValues values = new ContentValues();
                // include the resource id if there are more than one
                if (mLibrary.index().getResources(st.language.slug, st.project.slug).size() > 1) {
                    values.put("title", st.language.name + " " + st.resource.slug.toUpperCase());
                } else {
                    values.put("title", st.language.name);
                }
                values.put("tag", st.resourceContainerSlug);

                getFontForLanguageTab(mContext, st, values);
                tabContents.add(values);
            }
        }
        mTabs = tabContents.toArray(new ContentValues[0]);
    }

    @Override
    void onCoordinate(ViewHolder holder) {

    }

    @Override
    public String getFocusedChunkSlug(int position) {
        if (position >= 0 && position < mFilteredItems.size()) {
            return mFilteredItems.get(position).chunkSlug;
        }
        return null;
    }

    @Override
    public String getFocusedChapterSlug(int position) {
        if (position >= 0 && position < mFilteredItems.size()) {
            return mFilteredItems.get(position).chapterSlug;
        }
        return null;
    }

    @Override
    public int getItemPosition(String chapterSlug, String chunkSlug) {
        for (int i = 0; i < mFilteredItems.size(); i++) {
            ListItem item = mFilteredItems.get(i);
            if (item.isChunk() && item.chapterSlug.equals(chapterSlug) && item.chunkSlug.equals(chunkSlug)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public ViewHolder onCreateManagedViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        FragmentChunkListItemBinding binding = FragmentChunkListItemBinding.inflate(inflater, parent, false);
        return new ViewHolder(binding);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onBindManagedViewHolder(final ViewHolder holder, final int position) {
        int cardMargin = mContext.getResources().getDimensionPixelSize(R.dimen.card_margin);
        int stackedCardMargin = mContext.getResources().getDimensionPixelSize(R.dimen.stacked_card_margin);
        final ListItem item = mFilteredItems.get(position);
        if (((ChunkListItem) item).isTargetCardOpen) {
            // target on top
            holder.binding.sourceTranslationCard.setElevation(BOTTOM_ELEVATION);
            holder.binding.targetTranslationCard.setElevation(TOP_ELEVATION);
            holder.binding.targetTranslationCard.bringToFront();
            CardView.LayoutParams targetParams = (CardView.LayoutParams) holder.binding.targetTranslationCard.getLayoutParams();
            targetParams.setMargins(cardMargin, cardMargin, stackedCardMargin, stackedCardMargin);
            holder.binding.targetTranslationCard.setLayoutParams(targetParams);
            CardView.LayoutParams sourceParams = (CardView.LayoutParams) holder.binding.sourceTranslationCard.getLayoutParams();
            sourceParams.setMargins(stackedCardMargin, stackedCardMargin, cardMargin, cardMargin);
            holder.binding.sourceTranslationCard.setLayoutParams(sourceParams);
            ((View) holder.binding.targetTranslationCard.getParent()).requestLayout();
            ((View) holder.binding.targetTranslationCard.getParent()).invalidate();

            // disable new tab button so we don't accidentally open it
            holder.binding.newTabButton.setEnabled(false);
        } else {
            // source on top
            holder.binding.targetTranslationCard.setElevation(BOTTOM_ELEVATION);
            holder.binding.sourceTranslationCard.setElevation(TOP_ELEVATION);
            holder.binding.sourceTranslationCard.bringToFront();
            CardView.LayoutParams sourceParams = (CardView.LayoutParams) holder.binding.sourceTranslationCard.getLayoutParams();
            sourceParams.setMargins(cardMargin, cardMargin, stackedCardMargin, stackedCardMargin);
            holder.binding.sourceTranslationCard.setLayoutParams(sourceParams);
            CardView.LayoutParams targetParams = (CardView.LayoutParams) holder.binding.targetTranslationCard.getLayoutParams();
            targetParams.setMargins(stackedCardMargin, stackedCardMargin, cardMargin, cardMargin);
            holder.binding.targetTranslationCard.setLayoutParams(targetParams);
            ((View) holder.binding.sourceTranslationCard.getParent()).requestLayout();
            ((View) holder.binding.sourceTranslationCard.getParent()).invalidate();

            // re-enable new tab button
            holder.binding.newTabButton.setEnabled(true);
        }

        holder.binding.targetTranslationCard.setOnTouchListener((v, event) -> { // for touches on card other than edit area
            if (MotionEvent.ACTION_UP == event.getAction()) {
                return checkForPromptToEditDoneTargetCard(holder, mFilteredItems.get(position));
            }
            return false;
        });

        //for touches on edit area
        holder.binding.targetTranslationBody.setOnTouchListener((v, event) -> {
            if (MotionEvent.ACTION_UP == event.getAction()) {
                return checkForPromptToEditDoneTargetCard(holder, mFilteredItems.get(position));
            }
            return false;
        });

        holder.binding.targetTranslationCard.setOnClickListener(v -> {
            boolean targetCardOpened = openTargetTranslationCard(holder, position);

            // Accept clicks anywhere on card as if they were on the text box --
            // but only if the text is actually editable (i.e., not yet done).
            if (!targetCardOpened && holder.binding.targetTranslationBody.isEnabled()) {
                editTarget(holder.binding.targetTranslationBody, mFilteredItems.get(position));
            } else {
                // if marked as done (disabled for edit), enable to allow capture of click events, but do not make it focusable so they can't edit
                enableClicksIfChunkIsDone(holder);

            }
        });
        holder.binding.sourceTranslationCard.setOnClickListener(v -> closeTargetTranslationCard(holder, position));

        // load tabs
        holder.binding.sourceTranslationTabs.setOnTabSelectedListener(null);
        holder.binding.sourceTranslationTabs.removeAllTabs();
        for (ContentValues values : mTabs) {
            String tag = values.getAsString("tag");
            String title = values.getAsString("title");
            View tabLayout = createRemovableTabLayout(mContext, getListener(), tag, title);

            TabLayout.Tab tab = holder.binding.sourceTranslationTabs.newTab();
            tab.setTag(tag);
            tab.setCustomView(tabLayout);
            holder.binding.sourceTranslationTabs.addTab(tab);

            ViewModeAdapter.applyLanguageTypefaceToTab(mContext, holder.binding.sourceTranslationTabs, values, title);
        }

        // select correct tab
        for (int i = 0; i < holder.binding.sourceTranslationTabs.getTabCount(); i++) {
            TabLayout.Tab tab = holder.binding.sourceTranslationTabs.getTabAt(i);
            if (tab.getTag().equals(mSourceContainer.slug)) {
                tab.select();
                break;
            }
        }

        // hook up listener
        holder.binding.sourceTranslationTabs.setOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                final String sourceTranslationId = (String) tab.getTag();
                if (getListener() != null) {
                    Handler hand = new Handler(Looper.getMainLooper());
                    hand.post(() -> getListener().onSourceTranslationTabClick(sourceTranslationId));
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        holder.binding.newTabButton.setOnClickListener(v -> {
            if (getListener() != null) {
                getListener().onNewSourceTranslationTabClick();
            }
        });

        item.load(mSourceContainer, mTargetTranslation);

        renderChunk(holder, position);

        // set up fonts
        if (holder.mLayoutBuildNumber != mLayoutBuildNumber) {
            holder.mLayoutBuildNumber = mLayoutBuildNumber;

            Typography.formatSub(mContext, TranslationType.SOURCE, holder.binding.sourceTranslationTitle, mSourceContainer.language.slug, mSourceContainer.language.direction);
            Typography.format(mContext, TranslationType.SOURCE, holder.binding.sourceTranslationBody, mSourceContainer.language.slug, mSourceContainer.language.direction);
            Typography.formatSub(mContext, TranslationType.TARGET, holder.binding.targetTranslationTitle, mTargetLanguage.slug, mTargetLanguage.direction);
            Typography.format(mContext, TranslationType.TARGET, holder.binding.targetTranslationBody, mTargetLanguage.slug, mTargetLanguage.direction);
        }

        //////
        // set up card UI for merge conflicts
        if (item.hasMergeConflicts) {
            holder.binding.conflictButton.setVisibility(View.VISIBLE);
            holder.binding.conflictFrame.setVisibility(View.VISIBLE);
            holder.binding.targetTranslationBody.setVisibility(View.GONE);
            holder.binding.conflictButton.setOnClickListener(v -> {
                        Bundle args = new Bundle();
                        args.putBoolean(ChunkModeFragment.EXTRA_TARGET_OPEN, true);
                        args.putString(Translator.EXTRA_CHAPTER_ID, item.chapterSlug);
                        args.putString(Translator.EXTRA_FRAME_ID, item.chunkSlug);
                        getListener().openTranslationMode(TranslationViewMode.REVIEW, args);
                    }
            );
        } else {
            holder.binding.conflictFrame.setVisibility(View.GONE);
            holder.binding.targetTranslationBody.setVisibility(View.VISIBLE);
        }

        ViewUtil.makeLinksClickable(holder.binding.sourceTranslationBody);

        if (mTabs.length >= MAX_SOURCE_ITEMS) {
            holder.binding.newTabButton.setVisibility(View.GONE);
        } else {
            holder.binding.newTabButton.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Renders the chapter title card
     * begin edit of target card
     *
     * @param target
     */
    public void editTarget(final EditText target, final ListItem item) {
        // flag that chunk is open for edit
        if (item.isChapterReference()) {
            mTargetTranslation.reopenChapterReference(item.chapterSlug);
        } else if (item.isChapterTitle()) {
            mTargetTranslation.reopenChapterTitle(item.chapterSlug);
        } else if (item.isProjectTitle()) {
            mTargetTranslation.openProjectTitle();
        } else {
            mTargetTranslation.reopenFrame(item.chapterSlug, item.chunkSlug);
        }

        // set focus on edit text
        target.requestFocus();
        InputMethodManager mgr = (InputMethodManager)
                mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
        mgr.showSoftInput(target, InputMethodManager.SHOW_IMPLICIT);
    }

    /**
     * if chunk that is marked done, then enable click event
     *
     * @param holder
     */
    public void enableClicksIfChunkIsDone(final ViewHolder holder) {

        if (!holder.binding.targetTranslationBody.isEnabled()) {
            holder.binding.targetTranslationBody.setEnabled(true);
            holder.binding.targetTranslationBody.setFocusable(false);
        }
    }

    /**
     * prompt to edit chunk that is marked done
     *
     * @param holder
     * @param item
     */
    public boolean checkForPromptToEditDoneTargetCard(final ViewHolder holder, final ListItem item) {
        // if page is already in front and they are tapping on it, then see if they want to open for edit
        if (((ChunkListItem) item).isTargetCardOpen) {

            boolean enabled = holder.binding.targetTranslationBody.isEnabled();
            boolean focusable = holder.binding.targetTranslationBody.isFocusable();

            //if we have enabled for touch events but not focusable for edit then prompt to enable editing
            if (enabled && !focusable) {
                promptToEditDoneChunk(holder, item);
                return true;
            }
        }

        return false;
    }

    /**
     * prompt to edit chunk that is marked done
     *
     * @param holder
     */
    public void promptToEditDoneChunk(final ViewHolder holder, final ListItem item) {
        new AlertDialog.Builder(mContext, R.style.AppTheme_Dialog)
                .setTitle(R.string.chunk_done_title)
//                                .setIcon(R.drawable.ic_local_library_black_24dp)
                .setMessage(R.string.chunk_done_prompt)
                .setPositiveButton(R.string.edit, (dialog, which) -> {
                    holder.binding.targetTranslationBody.setEnabled(true);
                    holder.binding.targetTranslationBody.setFocusable(true);
                    holder.binding.targetTranslationBody.setFocusableInTouchMode(true);
                    holder.binding.targetTranslationBody.setEnableLines(true);
                    editTarget(holder.binding.targetTranslationBody, item);
                })
                .setNegativeButton(R.string.dismiss, null)
                .show();
    }

    /**
     * Renders the frame cards
     *
     * @param holder
     * @param position
     */
    private void renderChunk(final ViewHolder holder, final int position) {
        final ListItem item = mFilteredItems.get(position);

        // render source text
        if (item.renderedSourceText == null) {
            boolean enableSearch = filterConstraint != null &&
                    filterSubject != null &&
                    filterSubject == SearchSubject.SOURCE;
            item.renderedSourceText = renderText(
                    item.sourceText,
                    item.sourceTranslationFormat,
                    enableSearch
            );
        }
        holder.binding.sourceTranslationBody.setText(item.renderedSourceText);

        // render target text
        if (item.renderedTargetText == null) {
            boolean enableSearch = filterConstraint != null && filterSubject != null && filterSubject == SearchSubject.TARGET;
            item.renderedTargetText = renderText(item.targetText, item.targetTranslationFormat, enableSearch);
        }
        if (holder.mTextWatcher != null)
            holder.binding.targetTranslationBody.removeTextChangedListener(holder.mTextWatcher);
        holder.binding.targetTranslationBody.setText(TextUtils.concat(item.renderedTargetText, "\n"));

        // render source title
        if (item.isProjectTitle()) {
            holder.binding.sourceTranslationTitle.setText("");
        } else if (item.isChapter()) {
            holder.binding.sourceTranslationTitle.setText(mSourceContainer.project.name.trim());
        } else {
            // TODO: we should read the title from a cache instead of doing file io again
            String title = mSourceContainer.readChunk(item.chapterSlug, "title").trim();
            if (title.isEmpty()) {
                title = mSourceContainer.project.name.trim() + " " + Integer.parseInt(item.chapterSlug);
            }
            String verseSpan = Frame.parseVerseTitle(item.sourceText, item.sourceTranslationFormat);
            if (verseSpan.isEmpty()) {
                title += ":" + Integer.parseInt(item.chunkSlug);
            } else {
                title += ":" + verseSpan;
            }
            holder.binding.sourceTranslationTitle.setText(title);
        }

        // render target title
        holder.binding.targetTranslationTitle.setText(item.getTargetTitle());

        // indicate complete
        setCardStatus(item.isComplete, true, holder);

        holder.mTextWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // save
                String translation = Translator.compileTranslation((Editable) s);
                if (item.isProjectTitle()) {
                    try {
                        mTargetTranslation.applyProjectTitleTranslation(translation);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else if (item.isChapterTitle()) {
                    mTargetTranslation.applyChapterTitleTranslation(
                            mTargetTranslation.getChapterTranslation(item.chapterSlug),
                            translation
                    );
                } else if (item.isChapterReference()) {
                    mTargetTranslation.applyChapterReferenceTranslation(
                            mTargetTranslation.getChapterTranslation(item.chapterSlug),
                            translation
                    );
                } else {
                    mTargetTranslation.applyFrameTranslation(
                            mTargetTranslation.getFrameTranslation(
                                    item.chapterSlug,
                                    item.chunkSlug,
                                    item.targetTranslationFormat
                            ),
                            translation
                    );
                }

                boolean enableSearch = filterConstraint != null &&
                        filterSubject != null &&
                        filterSubject == SearchSubject.TARGET;
                item.renderedTargetText = renderText(
                        translation,
                        item.targetTranslationFormat,
                        enableSearch
                );
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        };
        holder.binding.targetTranslationBody.addTextChangedListener(holder.mTextWatcher);
    }

    private void setCardStatus(boolean finished, boolean closed, ViewHolder holder) {
        if (closed) {
            holder.binding.targetTranslationBody.setEnableLines(false);
            if (finished) {
                holder.binding.targetTranslationInnerCard.setBackgroundResource(R.color.card_background_color);
            } else {
                holder.binding.targetTranslationInnerCard.setBackgroundResource(R.drawable.paper_repeating);
            }
        } else {
            holder.binding.targetTranslationBody.setEnableLines(true);
            holder.binding.targetTranslationInnerCard.setBackgroundResource(R.color.card_background_color);
        }
    }

    private CharSequence renderText(String text, TranslationFormat format, boolean enableSearch) {
        RenderingGroup renderingGroup = new RenderingGroup();

        if (Clickables.isClickableFormat(format)) {
            // TODO: add click listeners for verses and notes
            Span.OnClickListener noteClickListener = new Span.OnClickListener() {
                @Override
                public void onClick(View view, Span span, int start, int end) {
                    if (span instanceof NoteSpan) {
                        new AlertDialog.Builder(mContext, R.style.AppTheme_Dialog)
                                .setTitle(R.string.title_footnote)
                                .setMessage(((NoteSpan) span).getNotes())
                                .setPositiveButton(R.string.dismiss, null)
                                .show();
                    }
                }

                @Override
                public void onLongClick(View view, Span span, int start, int end) {

                }
            };
            ClickableRenderingEngine renderer = Clickables.setupRenderingGroup(
                    format,
                    renderingGroup,
                    null,
                    noteClickListener,
                    true
            );
            renderer.setVersesEnabled(false);
            renderer.setParagraphsEnabled(false);
        } else {
            // TODO: add note click listener
            renderingGroup.addEngine(new DefaultRenderer(null));
        }

        if (enableSearch) {
            renderingGroup.setSearchString(filterConstraint, HIGHLIGHT_COLOR);
        }

        renderingGroup.init(text);
        return renderingGroup.start();
    }

    @Override
    public int getItemCount() {
        return mFilteredItems.size();
    }

    /**
     * removes text selection from the target card
     *
     * @param holder
     */
    public void clearSelectionFromTarget(ViewHolder holder) {
        holder.binding.targetTranslationBody.clearFocus();
    }

    /**
     * Toggle the target translation card between front and back
     *
     * @param holder
     * @param position
     * @param swipeLeft
     * @return true if action was taken, else false
     */
    public boolean toggleTargetTranslationCard(final ViewHolder holder, final int position, final boolean swipeLeft) {
        final ListItem item = mFilteredItems.get(position);
        if (((ChunkListItem) item).isTargetCardOpen) {
            return closeTargetTranslationCard(holder, position, !swipeLeft);
        }

        boolean success = openTargetTranslationCard(holder, position, !swipeLeft);
        enableClicksIfChunkIsDone(holder);
        return success;
    }

    /**
     * Moves the target translation card to the back
     *
     * @param holder
     * @param position
     * @param leftToRight
     * @return true if action was taken, else false
     */
    public boolean closeTargetTranslationCard(final ViewHolder holder, final int position, final boolean leftToRight) {
        final ListItem item = mFilteredItems.get(position);
        if (((ChunkListItem) item).isTargetCardOpen) {

            clearSelectionFromTarget(holder);

            ViewUtil.animateSwapCards(
                    holder.binding.targetTranslationCard,
                    holder.binding.sourceTranslationCard,
                    TOP_ELEVATION, BOTTOM_ELEVATION,
                    leftToRight,
                    new Animation.AnimationListener() {
                        @Override
                        public void onAnimationStart(Animation animation) {
                        }

                        @Override
                        public void onAnimationEnd(Animation animation) {
                            ((ChunkListItem) item).isTargetCardOpen = false;
                            if (getListener() != null) {
                                getListener().closeKeyboard();
                            }
                            setCardStatus(item.isComplete, true, holder);
                        }

                        @Override
                        public void onAnimationRepeat(Animation animation) {

                        }
                    }
            );
            if (getListener() != null) {
                getListener().closeKeyboard();
            }
            // re-enable new tab button
            holder.binding.newTabButton.setEnabled(true);

            return true;
        } else {
            return false;
        }
    }

    /**
     * Moves the target translation card to the back - left to right
     *
     * @param holder
     * @param position
     * @return true if action was taken, else false
     */
    public boolean closeTargetTranslationCard(final ViewHolder holder, final int position) {
        return closeTargetTranslationCard(holder, position, true);
    }

    /**
     * Moves the target translation to the top
     *
     * @param holder
     * @param position
     * @param leftToRight
     * @return true if action was taken, else false
     */
    public boolean openTargetTranslationCard(final ViewHolder holder, final int position, final boolean leftToRight) {
        final ListItem item = mFilteredItems.get(position);
        if (!((ChunkListItem) item).isTargetCardOpen) {
            ViewUtil.animateSwapCards(
                    holder.binding.sourceTranslationCard,
                    holder.binding.targetTranslationCard,
                    TOP_ELEVATION, BOTTOM_ELEVATION,
                    leftToRight,
                    new Animation.AnimationListener() {
                        @Override
                        public void onAnimationStart(Animation animation) {
                            setCardStatus(item.isComplete, false, holder);
                        }

                        @Override
                        public void onAnimationEnd(Animation animation) {
                            ((ChunkListItem) item).isTargetCardOpen = true;
                            if (getListener() != null) {
                                getListener().closeKeyboard();
                            }
                        }

                        @Override
                        public void onAnimationRepeat(Animation animation) {

                        }
                    }
            );
            if (getListener() != null) {
                getListener().closeKeyboard();
            }
            // disable new tab button so we don't accidentally open it
            holder.binding.newTabButton.setEnabled(false);

            return true;
        } else {
            return false;
        }
    }

    /**
     * Moves the target translation to the top
     *
     * @param holder
     * @param position
     * @return true if action was taken, else false
     */
    public boolean openTargetTranslationCard(final ViewHolder holder, final int position) {
        return openTargetTranslationCard(holder, position, false);
    }

    @Override
    public Object[] getSections() {
        return mFilteredChapters.toArray();
    }

    @Override
    public int getPositionForSection(int sectionIndex) {
        // not used
        return sectionIndex;
    }

    @Override
    public int getSectionForPosition(int position) {
        if (position >= 0 && position < mFilteredItems.size()) {
            ListItem item = mFilteredItems.get(position);
            return mFilteredChapters.indexOf(item.chapterSlug);
        } else {
            return -1;
        }
    }

    @Override
    public void markAllChunksDone() {
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public FragmentChunkListItemBinding binding;
        public int mLayoutBuildNumber = -1;
        public TextWatcher mTextWatcher;

        public ViewHolder(FragmentChunkListItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    /**
     * A simple container for list items
     */
    private static class ChunkListItem extends ListItem {
        private boolean isTargetCardOpen = false;

        public ChunkListItem(String chapterSlug, String chunkSlug) {
            super(chapterSlug, chunkSlug);
        }
    }
}
