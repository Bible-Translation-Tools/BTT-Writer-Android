package com.door43.translationstudio.ui.translate;

import static com.door43.translationstudio.ui.translate.ChooseSourceTranslationAdapter.MAX_SOURCE_ITEMS;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
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

import com.door43.translationstudio.R;
import com.door43.translationstudio.core.Frame;
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
import com.door43.translationstudio.ui.spannables.NoteSpan;
import com.door43.translationstudio.ui.spannables.Span;
import com.door43.widget.ViewUtil;
import com.google.android.material.tabs.TabLayout;

import java.io.IOException;
import java.util.List;

/**
 * Created by joel on 9/9/2015.
 */
public class ChunkModeAdapter extends ViewModeAdapter<ChunkModeAdapter.ViewHolder> {
    private static final int BOTTOM_ELEVATION = 2;
    private static final int TOP_ELEVATION = 3;

    public ChunkModeAdapter(Typography typography) {
        this.typography = typography;
    }

    @Override
    public void initializeListItems(
            List<ListItem> listItems,
            String startingChapter,
            String startingChunk
    ) {
        super.initializeListItems(listItems, startingChapter, startingChunk);

        triggerNotifyDataSetChanged();
        updateMergeConflict();
    }

    @Override
    public ChunkListItem createListItem(ListItem item) {
        return item.toType(ChunkListItem::new);
    }

    /**
     * Check all cards for merge conflicts to see if we should show warning.
     * Runs as background task.
     */
    private void updateMergeConflict() {
        doCheckForMergeConflict();
    }

    @Override
    public String getFocusedChunkSlug(int position) {
        if (position >= 0 && position < filteredItems.size()) {
            return filteredItems.get(position).chunkSlug;
        }
        return null;
    }

    @Override
    public String getFocusedChapterSlug(int position) {
        return filteredItems.get(position).chapterSlug;
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
        int cardMargin = context.getResources().getDimensionPixelSize(R.dimen.card_margin);
        int stackedCardMargin = context.getResources().getDimensionPixelSize(R.dimen.stacked_card_margin);
        final ListItem item = filteredItems.get(position);
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
                return checkForPromptToEditDoneTargetCard(holder, filteredItems.get(position));
            }
            return false;
        });

        //for touches on edit area
        holder.binding.targetTranslationBody.setOnTouchListener((v, event) -> {
            if (MotionEvent.ACTION_UP == event.getAction()) {
                return checkForPromptToEditDoneTargetCard(holder, filteredItems.get(position));
            }
            return false;
        });

        holder.binding.targetTranslationCard.setOnClickListener(v -> {
            boolean targetCardOpened = openTargetTranslationCard(holder, position);

            // Accept clicks anywhere on card as if they were on the text box --
            // but only if the text is actually editable (i.e., not yet done).
            if (!targetCardOpened && holder.binding.targetTranslationBody.isEnabled()) {
                editTarget(holder.binding.targetTranslationBody, filteredItems.get(position));
            } else {
                // if marked as done (disabled for edit), enable to allow capture of click events, but do not make it focusable so they can't edit
                enableClicksIfChunkIsDone(holder);

            }
        });
        holder.binding.sourceTranslationCard.setOnClickListener(v -> closeTargetTranslationCard(holder, position));

        // load tabs
        holder.binding.sourceTranslationTabs.setOnTabSelectedListener(null);
        holder.binding.sourceTranslationTabs.removeAllTabs();

        List<ContentValues> tabs = item.getTabs();
        for (ContentValues values : tabs) {
            String tag = values.getAsString("tag");
            String title = values.getAsString("title");
            View tabLayout = createRemovableTabLayout(getListener(), tag, title);

            TabLayout.Tab tab = holder.binding.sourceTranslationTabs.newTab();
            tab.setTag(tag);
            tab.setCustomView(tabLayout);
            holder.binding.sourceTranslationTabs.addTab(tab);

            applyLanguageTypefaceToTab(holder.binding.sourceTranslationTabs, values, title);
        }

        // select correct tab
        for (int i = 0; i < holder.binding.sourceTranslationTabs.getTabCount(); i++) {
            TabLayout.Tab tab = holder.binding.sourceTranslationTabs.getTabAt(i);
            if (tab.getTag().equals(item.source.slug)) {
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

        renderChunk(holder, position);

        // set up fonts
        if (holder.mLayoutBuildNumber != layoutBuildNumber) {
            holder.mLayoutBuildNumber = layoutBuildNumber;

            typography.formatSub(
                    TranslationType.SOURCE,
                    holder.binding.sourceTranslationTitle,
                    item.source.language.slug,
                    item.source.language.direction
            );
            typography.format(
                    TranslationType.SOURCE,
                    holder.binding.sourceTranslationBody,
                    item.source.language.slug,
                    item.source.language.direction
            );
            typography.formatSub(
                    TranslationType.TARGET,
                    holder.binding.targetTranslationTitle,
                    item.target.getTargetLanguage().slug,
                    item.target.getTargetLanguage().direction
            );
            typography.format(
                    TranslationType.TARGET,
                    holder.binding.targetTranslationBody,
                    item.target.getTargetLanguage().slug,
                    item.target.getTargetLanguage().direction
            );
        }

        //////
        // set up card UI for merge conflicts
        if (item.getHasMergeConflicts()) {
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

        if (tabs.size() >= MAX_SOURCE_ITEMS) {
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
            item.target.reopenChapterReference(item.chapterSlug);
        } else if (item.isChapterTitle()) {
            item.target.reopenChapterTitle(item.chapterSlug);
        } else if (item.isProjectTitle()) {
            item.target.openProjectTitle();
        } else {
            item.target.reopenFrame(item.chapterSlug, item.chunkSlug);
        }

        // set focus on edit text
        target.requestFocus();
        InputMethodManager mgr = (InputMethodManager)
                context.getSystemService(Context.INPUT_METHOD_SERVICE);
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
        new AlertDialog.Builder(context, R.style.AppTheme_Dialog)
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
        final ListItem item = filteredItems.get(position);

        // render source text
        if (item.renderedSourceText == null) {
            item.renderedSourceText = renderText(
                    item.getSourceText(),
                    item.getSourceTranslationFormat()
            );
        }
        holder.binding.sourceTranslationBody.setText(item.renderedSourceText);

        // render target text
        if (item.renderedTargetText == null) {
            item.renderedTargetText = renderText(item.getTargetText(), item.getTargetTranslationFormat());
        }
        if (holder.mTextWatcher != null)
            holder.binding.targetTranslationBody.removeTextChangedListener(holder.mTextWatcher);
        holder.binding.targetTranslationBody.setText(TextUtils.concat(item.renderedTargetText, "\n"));

        // render source title
        if (item.isProjectTitle()) {
            holder.binding.sourceTranslationTitle.setText("");
        } else if (item.isChapter()) {
            holder.binding.sourceTranslationTitle.setText(item.source.project.name.trim());
        } else {
            // TODO: we should read the title from a cache instead of doing file io again
            String title = item.source.readChunk(item.chapterSlug, "title").trim();
            if (title.isEmpty()) {
                title = item.source.project.name.trim() + " " + Integer.parseInt(item.chapterSlug);
            }
            String verseSpan = Frame.parseVerseTitle(item.getSourceText(), item.getSourceTranslationFormat());
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
        setCardStatus(item.isComplete(), true, holder);

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
                        item.target.applyProjectTitleTranslation(translation);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } else if (item.isChapterTitle()) {
                    item.target.applyChapterTitleTranslation(
                            item.target.getChapterTranslation(item.chapterSlug),
                            translation
                    );
                } else if (item.isChapterReference()) {
                    item.target.applyChapterReferenceTranslation(
                            item.target.getChapterTranslation(item.chapterSlug),
                            translation
                    );
                } else {
                    item.target.applyFrameTranslation(
                            item.target.getFrameTranslation(
                                    item.chapterSlug,
                                    item.chunkSlug,
                                    item.getTargetTranslationFormat()
                            ),
                            translation
                    );
                }

                item.renderedTargetText = renderText(
                        translation,
                        item.getTargetTranslationFormat()
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

    private CharSequence renderText(String text, TranslationFormat format) {
        RenderingGroup renderingGroup = new RenderingGroup();

        if (Clickables.isClickableFormat(format)) {
            // TODO: add click listeners for verses and notes
            Span.OnClickListener noteClickListener = new Span.OnClickListener() {
                @Override
                public void onClick(View view, Span span, int start, int end) {
                    if (span instanceof NoteSpan) {
                        new AlertDialog.Builder(context, R.style.AppTheme_Dialog)
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

        renderingGroup.init(text);
        return renderingGroup.start();
    }

    @Override
    public int getItemCount() {
        return filteredItems.size();
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
        final ListItem item = filteredItems.get(position);
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
        final ListItem item = filteredItems.get(position);
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
                            setCardStatus(item.isComplete(), true, holder);
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
        final ListItem item = filteredItems.get(position);
        if (!((ChunkListItem) item).isTargetCardOpen) {
            ViewUtil.animateSwapCards(
                    holder.binding.sourceTranslationCard,
                    holder.binding.targetTranslationCard,
                    TOP_ELEVATION, BOTTOM_ELEVATION,
                    leftToRight,
                    new Animation.AnimationListener() {
                        @Override
                        public void onAnimationStart(Animation animation) {
                            setCardStatus(item.isComplete(), false, holder);
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
        return filteredChapters.toArray();
    }

    @Override
    public int getPositionForSection(int sectionIndex) {
        // not used
        return sectionIndex;
    }

    @Override
    public int getSectionForPosition(int position) {
        if (position >= 0 && position < filteredItems.size()) {
            ListItem item = filteredItems.get(position);
            return filteredChapters.indexOf(item.chapterSlug);
        } else {
            return -1;
        }
    }

    @Override
    public void markAllChunksDone() {
    }

    @Override
    public void setResourcesOpened(boolean status) {
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
}
