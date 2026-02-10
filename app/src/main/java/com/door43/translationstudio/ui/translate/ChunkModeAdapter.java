package com.door43.translationstudio.ui.translate;

import static com.door43.translationstudio.ui.translate.ChooseSourceTranslationAdapter.MAX_SOURCE_ITEMS;

import android.content.ContentValues;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.door43.translationstudio.R;
import com.door43.translationstudio.core.Frame;
import com.door43.translationstudio.core.RenderingProvider;
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
public class ChunkModeAdapter extends ViewModeAdapter<ChunkModeAdapter.ViewHolder> implements OnChunkModeListener {
    private static final int BOTTOM_ELEVATION = 2;
    private static final int TOP_ELEVATION = 3;

    public ChunkModeAdapter(Typography typography, RenderingProvider renderingProvider) {
        this.typography = typography;
        this.renderingProvider = renderingProvider;
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
        if (position > 0 && position < filteredItems.size()) {
            return filteredItems.get(position).chapterSlug;
        }
        return null;
    }

    @Override
    public ViewHolder onCreateManagedViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        FragmentChunkListItemBinding binding = FragmentChunkListItemBinding.inflate(inflater, parent, false);
        return new ViewHolder(binding, typography, this);
    }

    @Override
    public void onBindManagedViewHolder(final ViewHolder holder, final int position) {
        final ChunkListItem item = (ChunkListItem) filteredItems.get(position);
        holder.bind(item);
    }

    @Override
    public boolean onCheckForPromptToEditDoneTargetCard(ViewHolder holder) {
        return checkForPromptToEditDoneTargetCard(holder);
    }

    @Override
    public void onOpenTargetTranslationCard(ViewHolder holder) {
        openTargetTranslationCard(holder, false);
    }

    @Override
    public void onCloseTargetTranslationCard(ViewHolder holder) {
        closeTargetTranslationCard(holder, true);
    }

    @Override
    public void onEditTarget(EditText target, int position) {
        final ListItem item = filteredItems.get(position);
        editTarget(target, item);
    }

    @Override
    public View onCreateRemovableTabLayout(String tag, String title) {
        if (getListener() != null) {
            return getListener().onCreateRemovableTabLayout(tag, title);
        }
        return null;
    }

    @Override
    public void onApplyLanguageTypefaceToTab(TabLayout layout, ContentValues values, String title) {
        if (getListener() != null) {
            getListener().onApplyLanguageTypefaceToTab(layout, values, title);
        }
    }

    @Override
    public void onSourceTranslationTabClick(String sourceId) {
        if (getListener() != null) {
            getListener().onSourceTranslationTabClick(sourceId);
        }
    }

    @Override
    public void onNewSourceTranslationTabClick() {
        if (getListener() != null) {
            getListener().onNewSourceTranslationTabClick();
        }
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count, int itemPosition) {
        ChunkListItem item = (ChunkListItem) filteredItems.get(itemPosition);
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
    public void onConflictButtonClicked(int position) {
        ChunkListItem item = (ChunkListItem) filteredItems.get(position);
        Bundle args = new Bundle();
        args.putBoolean(ChunkModeFragment.EXTRA_TARGET_OPEN, true);
        args.putString(Translator.EXTRA_CHAPTER_ID, item.chapterSlug);
        args.putString(Translator.EXTRA_FRAME_ID, item.chunkSlug);

        getListener().openTranslationMode(TranslationViewMode.REVIEW, args);
    }

    @Override
    public CharSequence onRenderText(String text, TranslationFormat format) {
        return renderText(text, format);
    }

    /**
     * Renders the chapter title card
     * begin edit of target card
     *
     * @param target target edit text
     */
    private void editTarget(final EditText target, final ListItem item) {
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
        if (getListener() != null) {
            getListener().showKeyboard(target);
        }
    }

    /**
     * prompt to edit chunk that is marked done
     *
     * @param holder chunk view holder
     */
    private boolean checkForPromptToEditDoneTargetCard(final ViewHolder holder) {
        // if page is already in front and they are tapping on it, then see if they want to open for edit
        int position = holder.getBindingAdapterPosition();
        if (position == RecyclerView.NO_POSITION) {
            return false;
        }
        ChunkListItem item = (ChunkListItem) filteredItems.get(position);

        if (item.isComplete()) {
            promptToEditDoneChunk(holder, item);
            return true;
        }

        return false;
    }

    /**
     * prompt to edit chunk that is marked done
     *
     * @param holder chunk view holder
     * @param item list item
     */
    public void promptToEditDoneChunk(final ViewHolder holder, final ListItem item) {
        new AlertDialog.Builder(context, R.style.AppTheme_Dialog)
                .setTitle(R.string.chunk_done_title)
                .setMessage(R.string.chunk_done_prompt)
                .setPositiveButton(R.string.edit, (dialog, which) -> {
                    holder.binding.targetTranslationBody.setEnabled(true);
                    holder.binding.targetTranslationBody.setFocusable(true);
                    holder.binding.targetTranslationBody.setFocusableInTouchMode(true);
                    holder.binding.targetTranslationBody.setEnableLines(true);

                    item.setComplete(false);
                    editTarget(holder.binding.targetTranslationBody, item);
                })
                .setNegativeButton(R.string.dismiss, null)
                .show();
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
            ClickableRenderingEngine renderer = renderingProvider.setupRenderingGroup(
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
     * @param holder chunk view holder
     */
    public void clearSelectionFromTarget(ViewHolder holder) {
        holder.binding.targetTranslationBody.clearFocus();
    }

    /**
     * Toggle the target translation card between front and back
     *
     * @param holder holder
     * @param swipeLeft true if moving left to right
     */
    public void toggleTargetTranslationCard(final ViewHolder holder, final boolean swipeLeft) {
        closeTargetTranslationCard(holder, !swipeLeft);
        openTargetTranslationCard(holder, !swipeLeft);
        holder.enableClicksIfChunkIsDone();
    }

    /**
     * Moves the target translation card to the back
     *
     * @param holder chunk view holder
     * @param leftToRight true if moving left to right
     */
    public void closeTargetTranslationCard(final ViewHolder holder, final boolean leftToRight) {
        int position = holder.getBindingAdapterPosition();
        if (position == RecyclerView.NO_POSITION) {
            return;
        }
        ChunkListItem item = (ChunkListItem) filteredItems.get(position);

        if (item.isTargetCardOpen) {
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
                            item.isTargetCardOpen = false;
                            if (getListener() != null) {
                                getListener().closeKeyboard();
                            }
                            holder.setCardStatus(item.isComplete(), true);
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
        }
    }

    /**
     * Moves the target translation to the top
     *
     * @param holder chunk view holder
     * @param leftToRight true if moving left to right
     */
    public void openTargetTranslationCard(final ViewHolder holder, final boolean leftToRight) {
        int position = holder.getBindingAdapterPosition();
        if (position == RecyclerView.NO_POSITION) {
            return;
        }
        final ChunkListItem item = (ChunkListItem) filteredItems.get(position);

        if (!item.isTargetCardOpen) {
            ViewUtil.animateSwapCards(
                    holder.binding.sourceTranslationCard,
                    holder.binding.targetTranslationCard,
                    TOP_ELEVATION, BOTTOM_ELEVATION,
                    leftToRight,
                    new Animation.AnimationListener() {
                        @Override
                        public void onAnimationStart(Animation animation) {
                            holder.setCardStatus(item.isComplete(), false);
                        }

                        @Override
                        public void onAnimationEnd(Animation animation) {
                            item.isTargetCardOpen = true;
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
        }
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
        public TextWatcher textWatcher;
        private final Context context;
        private final OnChunkModeListener chunkModeListener;
        private final TabLayout.OnTabSelectedListener tabSelectedListener;
        private final Typography typography;

        public ViewHolder(
                FragmentChunkListItemBinding binding,
                Typography typography,
                OnChunkModeListener chunkModeListener
        ) {
            super(binding.getRoot());
            this.binding = binding;

            context = binding.getRoot().getContext();
            this.chunkModeListener = chunkModeListener;
            this.typography = typography;

            textWatcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if (chunkModeListener != null) {
                        int position = getBindingAdapterPosition();
                        if (position != RecyclerView.NO_POSITION) {
                            chunkModeListener.onTextChanged(s, start, before, count, position);
                        }
                    }
                }

                @Override
                public void afterTextChanged(Editable s) {

                }
            };

            tabSelectedListener = new TabLayout.OnTabSelectedListener() {
                @Override
                public void onTabSelected(TabLayout.Tab tab) {
                    final String sourceTranslationId = (String) tab.getTag();
                    if (chunkModeListener != null) {
                        chunkModeListener.onSourceTranslationTabClick(sourceTranslationId);
                    }
                }

                @Override
                public void onTabUnselected(TabLayout.Tab tab) {
                }

                @Override
                public void onTabReselected(TabLayout.Tab tab) {
                }
            };

            itemView.post(() -> {
                binding.targetTranslationCard.setOnTouchListener((v, event) -> { // for touches on card other than edit area
                    if (MotionEvent.ACTION_UP == event.getAction()) {
                        if (chunkModeListener != null) {
                            return chunkModeListener.onCheckForPromptToEditDoneTargetCard(this);
                        }
                    }
                    return false;
                });

                //for touches on edit area
                binding.targetTranslationBody.setOnTouchListener((v, event) -> {
                    if (MotionEvent.ACTION_UP == event.getAction()) {
                        if (chunkModeListener != null) {
                            return chunkModeListener.onCheckForPromptToEditDoneTargetCard(this);
                        }
                    }
                    return false;
                });

                binding.targetTranslationCard.setOnClickListener(v -> {
                    if (chunkModeListener != null) {
                        chunkModeListener.onOpenTargetTranslationCard(this);

                        // Accept clicks anywhere on card as if they were on the text box --
                        // but only if the text is actually editable (i.e., not yet done).
                        if (binding.targetTranslationBody.isEnabled()) {
                            int position = getBindingAdapterPosition();
                            if (position != RecyclerView.NO_POSITION) {
                                chunkModeListener.onEditTarget(binding.targetTranslationBody, position);
                            }
                        } else {
                            // if marked as done (disabled for edit), enable to allow capture of click events, but do not make it focusable so they can't edit
                            enableClicksIfChunkIsDone();
                        }
                    }
                });

                binding.sourceTranslationCard.setOnClickListener(v -> {
                    if (chunkModeListener != null) {
                        chunkModeListener.onCloseTargetTranslationCard(this);
                    }
                });

                binding.newTabButton.setOnClickListener(v -> {
                    if (chunkModeListener != null) {
                        chunkModeListener.onNewSourceTranslationTabClick();
                    }
                });

                binding.conflictButton.setOnClickListener(v -> {
                    if (chunkModeListener != null) {
                        int position = getBindingAdapterPosition();
                        if (position != RecyclerView.NO_POSITION) {
                            chunkModeListener.onConflictButtonClicked(position);
                        }
                    }
                });
            });
        }

        public void bind(ChunkListItem item) {
            int cardMargin = context.getResources().getDimensionPixelSize(R.dimen.card_margin);
            int stackedCardMargin = context.getResources().getDimensionPixelSize(R.dimen.stacked_card_margin);
            if (item.isTargetCardOpen) {
                // target on top
                binding.sourceTranslationCard.setElevation(BOTTOM_ELEVATION);
                binding.targetTranslationCard.setElevation(TOP_ELEVATION);
                binding.targetTranslationCard.bringToFront();
                CardView.LayoutParams targetParams = (CardView.LayoutParams) binding.targetTranslationCard.getLayoutParams();
                targetParams.setMargins(cardMargin, cardMargin, stackedCardMargin, stackedCardMargin);
                binding.targetTranslationCard.setLayoutParams(targetParams);
                CardView.LayoutParams sourceParams = (CardView.LayoutParams) binding.sourceTranslationCard.getLayoutParams();
                sourceParams.setMargins(stackedCardMargin, stackedCardMargin, cardMargin, cardMargin);
                binding.sourceTranslationCard.setLayoutParams(sourceParams);
                ((View) binding.targetTranslationCard.getParent()).requestLayout();
                ((View) binding.targetTranslationCard.getParent()).invalidate();

                // disable new tab button so we don't accidentally open it
                binding.newTabButton.setEnabled(false);
            } else {
                // source on top
                binding.targetTranslationCard.setElevation(BOTTOM_ELEVATION);
                binding.sourceTranslationCard.setElevation(TOP_ELEVATION);
                binding.sourceTranslationCard.bringToFront();
                CardView.LayoutParams sourceParams = (CardView.LayoutParams) binding.sourceTranslationCard.getLayoutParams();
                sourceParams.setMargins(cardMargin, cardMargin, stackedCardMargin, stackedCardMargin);
                binding.sourceTranslationCard.setLayoutParams(sourceParams);
                CardView.LayoutParams targetParams = (CardView.LayoutParams) binding.targetTranslationCard.getLayoutParams();
                targetParams.setMargins(stackedCardMargin, stackedCardMargin, cardMargin, cardMargin);
                binding.targetTranslationCard.setLayoutParams(targetParams);
                ((View) binding.sourceTranslationCard.getParent()).requestLayout();
                ((View) binding.sourceTranslationCard.getParent()).invalidate();

                // re-enable new tab button
                binding.newTabButton.setEnabled(true);
            }

            // load tabs
            List<ContentValues> tabs = item.getTabs();
            renderSourceTabs(tabs, item.source.slug);

            renderChunk(item);

            // set up fonts
            typography.formatSub(
                    TranslationType.SOURCE,
                    binding.sourceTranslationTitle,
                    item.source.language.slug,
                    item.source.language.direction
            );
            typography.format(
                    TranslationType.SOURCE,
                    binding.sourceTranslationBody,
                    item.source.language.slug,
                    item.source.language.direction
            );
            typography.formatSub(
                    TranslationType.TARGET,
                    binding.targetTranslationTitle,
                    item.target.getTargetLanguage().slug,
                    item.target.getTargetLanguage().direction
            );
            typography.format(
                    TranslationType.TARGET,
                    binding.targetTranslationBody,
                    item.target.getTargetLanguage().slug,
                    item.target.getTargetLanguage().direction
            );

            //////
            // set up card UI for merge conflicts
            if (item.getHasMergeConflicts()) {
                binding.conflictButton.setVisibility(View.VISIBLE);
                binding.conflictFrame.setVisibility(View.VISIBLE);
                binding.targetTranslationBody.setVisibility(View.GONE);
            } else {
                binding.conflictFrame.setVisibility(View.GONE);
                binding.targetTranslationBody.setVisibility(View.VISIBLE);
            }

            ViewUtil.makeLinksClickable(binding.sourceTranslationBody);

            if (tabs.size() >= MAX_SOURCE_ITEMS) {
                binding.newTabButton.setVisibility(View.GONE);
            } else {
                binding.newTabButton.setVisibility(View.VISIBLE);
            }
        }

        /**
         * if chunk that is marked done, then enable click event
         */
        public void enableClicksIfChunkIsDone() {
            if (!binding.targetTranslationBody.isEnabled()) {
                binding.targetTranslationBody.setEnabled(true);
                binding.targetTranslationBody.setFocusable(false);
            }
        }

        /**
         * Renders the frame cards
         *
         * @param item chunk list item
         */
        private void renderChunk(ChunkListItem item) {
            removeTextChangeListener();

            // render source text
            if (item.renderedSourceText == null && chunkModeListener != null) {
                item.renderedSourceText = chunkModeListener.onRenderText(
                        item.getSourceText(),
                        item.getSourceTranslationFormat()
                );
            }
            binding.sourceTranslationBody.setText(item.renderedSourceText);

            // render target text
            if (item.renderedTargetText == null && chunkModeListener != null) {
                item.renderedTargetText = chunkModeListener.onRenderText(
                        item.getTargetText(),
                        item.getTargetTranslationFormat()
                );
            }
            binding.targetTranslationBody.setText(TextUtils.concat(item.renderedTargetText, "\n"));

            // render source title
            if (item.isProjectTitle()) {
                binding.sourceTranslationTitle.setText("");
            } else if (item.isChapter()) {
                binding.sourceTranslationTitle.setText(item.source.project.name.trim());
            } else {
                // TODO: we should read the title from a cache instead of doing file io again
                String title = item.source.readChunk(item.chapterSlug, "title").trim();
                if (title.isEmpty()) {
                    try {
                        title = item.source.project.name.trim() + " " + Integer.parseInt(item.chapterSlug);
                    } catch (Exception e) {
                        title = item.source.project.name.trim() + " " + item.chapterSlug;
                    }
                }
                String verseSpan = Frame.parseVerseTitle(item.getSourceText(), item.getSourceTranslationFormat());
                if (verseSpan.isEmpty()) {
                    try {
                        title += ":" + Integer.parseInt(item.chunkSlug);
                    } catch (Exception e) {
                        title += ":" + item.chunkSlug;
                    }
                } else {
                    title += ":" + verseSpan;
                }
                binding.sourceTranslationTitle.setText(title);
            }

            // render target title
            binding.targetTranslationTitle.setText(item.getTargetTitle());

            // indicate complete
            setCardStatus(item.isComplete(), true);

            attachTextChangeListener();
        }

        public void attachTextChangeListener() {
            binding.targetTranslationBody.removeTextChangedListener(textWatcher);
            binding.targetTranslationBody.addTextChangedListener(textWatcher);
        }

        public void removeTextChangeListener() {
            binding.targetTranslationBody.removeTextChangedListener(textWatcher);
        }

        private void setCardStatus(boolean finished, boolean closed) {
            if (closed) {
                binding.targetTranslationBody.setEnableLines(false);
                if (finished) {
                    binding.targetTranslationInnerCard.setBackgroundResource(R.color.card_background_color);
                } else {
                    binding.targetTranslationInnerCard.setBackgroundResource(R.drawable.paper_repeating);
                }
            } else {
                binding.targetTranslationBody.setEnableLines(true);
                binding.targetTranslationInnerCard.setBackgroundResource(R.color.card_background_color);
            }
        }

        private void renderSourceTabs(List<ContentValues> tabs, String sourceSlug) {
            binding.sourceTranslationTabs.removeOnTabSelectedListener(tabSelectedListener);
            binding.sourceTranslationTabs.removeAllTabs();

            for (ContentValues values : tabs) {
                String tag = values.getAsString("tag");
                String title = values.getAsString("title");

                if (chunkModeListener != null) {
                    View tabLayout = chunkModeListener.onCreateRemovableTabLayout(tag, title);

                    if (tabLayout != null) {
                        TabLayout.Tab tab = binding.sourceTranslationTabs.newTab();
                        tab.setTag(tag);
                        tab.setCustomView(tabLayout);
                        binding.sourceTranslationTabs.addTab(tab);
                    }

                    chunkModeListener.onApplyLanguageTypefaceToTab(binding.sourceTranslationTabs, values, title);
                }
            }

            // select correct tab
            for (int i = 0; i < binding.sourceTranslationTabs.getTabCount(); i++) {
                TabLayout.Tab tab = binding.sourceTranslationTabs.getTabAt(i);
                if (sourceSlug.equals(tab.getTag())) {
                    tab.select();
                    break;
                }
            }

            binding.sourceTranslationTabs.addOnTabSelectedListener(tabSelectedListener);
        }
    }
}
