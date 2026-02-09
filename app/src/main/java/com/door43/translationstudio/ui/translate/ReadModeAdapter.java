package com.door43.translationstudio.ui.translate;

import static com.door43.translationstudio.ui.translate.ChooseSourceTranslationAdapter.MAX_SOURCE_ITEMS;

import android.content.ContentValues;
import android.content.Context;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;

import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.door43.translationstudio.R;
import com.door43.translationstudio.core.ChapterTranslation;
import com.door43.translationstudio.core.ProjectTranslation;
import com.door43.translationstudio.core.RenderingProvider;
import com.door43.translationstudio.core.TranslationFormat;
import com.door43.translationstudio.core.TranslationType;
import com.door43.translationstudio.core.TranslationViewMode;
import com.door43.translationstudio.core.Translator;
import com.door43.translationstudio.core.Typography;
import com.door43.translationstudio.databinding.FragmentReadListItemBinding;
import com.door43.translationstudio.rendering.ClickableRenderingEngine;
import com.door43.translationstudio.rendering.Clickables;
import com.door43.translationstudio.rendering.RenderingGroup;
import com.door43.translationstudio.ui.spannables.NoteSpan;
import com.door43.translationstudio.ui.spannables.Span;
import com.door43.widget.ViewUtil;
import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

/**
 * Created by joel on 9/9/2015.
 */
public class ReadModeAdapter extends ViewModeAdapter<ReadModeAdapter.ViewHolder> implements OnReadModeListener {
    private static final int BOTTOM_ELEVATION = 2;
    private static final int TOP_ELEVATION = 3;

    private CharSequence[] renderedTargetBody = new CharSequence[0];
    private CharSequence[] renderedSourceBody = new CharSequence[0];

    private boolean[] targetStateOpen = new boolean[0];

    /**
     * Reference to the list of all items (chunks)
     */
    private List<ListItem> chunks = new ArrayList<>();

    public ReadModeAdapter(Typography typography, RenderingProvider renderingProvider) {
        this.typography = typography;
        this.renderingProvider = renderingProvider;
    }

    @Override
    public void initializeListItems(
            List<ListItem> listItems,
            String startingChapter,
            String startingChunk
    ) {
        layoutBuildNumber++; // force resetting of fonts
        chunks.clear();
        chapters.clear();
        items.clear();

        setListStartPosition(0);
        boolean foundStartingChapter = false;

        for (ListItem item: listItems) {
            if (!foundStartingChapter && item.chapterSlug.equals(startingChapter)) {
                setListStartPosition(items.size());
                foundStartingChapter = true;
            }
            if (!chapters.contains(item.chapterSlug)) {
                chapters.add(item.chapterSlug);
                items.add(createListItem(item));
            }
        }

        chunks.addAll(listItems);

        targetStateOpen = new boolean[chapters.size()];
        renderedSourceBody = new CharSequence[chapters.size()];
        renderedTargetBody = new CharSequence[chapters.size()];

        triggerNotifyDataSetChanged();
        updateMergeConflict();
    }

    @Override
    public ReadListItem createListItem(ListItem item) {
        return item.toType(ReadListItem::new);
    }

    /**
     * check all cards for merge conflicts to see if we should show warning. Runs as background task.
     */
    private void updateMergeConflict() {
        doCheckForMergeConflict();
    }

    @Override
    protected int getConflictsCount() {
        int conflictsCount = 0;
        for (ListItem item : chunks) {
            if(item.getHasMergeConflicts()) {
                conflictsCount++;
            }
        }
        return conflictsCount;
    }

    @Override
    public String getFocusedChunkSlug(int position) {
        return null;
    }

    @Override
    public String getFocusedChapterSlug(int position) {
        if(position >= 0 && position < chapters.size()) {
            return chapters.get(position);
        } else {
            return null;
        }
    }

    @Override
    public int getItemPosition(String chapterSlug, String chunkSlug) {
        return chapters.indexOf(chapterSlug);
    }

    @Override
    public ListItem getItem(String chapterSlug, String chunkSlug) {
        return items.get(getItemPosition(chapterSlug, chunkSlug));
    }

    @Override
    public ViewHolder onCreateManagedViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        FragmentReadListItemBinding binding = FragmentReadListItemBinding.inflate(inflater, parent, false);
        return new ViewHolder(binding, typography, this);
    }

    @Override
    public void markAllChunksDone() {}

    @Override
    public void onOpenTargetTranslationCard(ViewHolder holder) {
        int position = holder.getBindingAdapterPosition();
        openTargetTranslationCard(holder, position, false);
    }

    @Override
    public void onCloseTargetTranslationCard(ViewHolder holder) {
        int position = holder.getBindingAdapterPosition();
        closeTargetTranslationCard(holder, position, true);
    }

    @Override
    public void onNewSourceTranslationTabClick() {
        if (getListener() != null) {
            getListener().onNewSourceTranslationTabClick();
        }
    }

    @Override
    public CharSequence onRenderSourceText(ReadModeAdapter.ViewHolder holder) {
        int position = holder.getBindingAdapterPosition();
        ReadListItem item = (ReadListItem) items.get(position);

        String sourceChapterBody = item.getSourceText();
        TranslationFormat bodyFormat = TranslationFormat.parse(item.source.contentMimeType);
        RenderingGroup sourceRendering = new RenderingGroup();

        if (Clickables.isClickableFormat(bodyFormat)) {
            Span.OnClickListener noteClickListener = new Span.OnClickListener() {
                @Override
                public void onClick(View view, Span span, int start, int end) {
                    if(span instanceof NoteSpan) {
                        showFootnote((NoteSpan)span);
                    }
                }
                @Override
                public void onLongClick(View view, Span span, int start, int end) {
                }
            };
            ClickableRenderingEngine renderer = renderingProvider.setupRenderingGroup(
                    bodyFormat,
                    sourceRendering,
                    null,
                    noteClickListener,
                    true
            );
            // In read mode (and only in read mode), pull leading major section headings out for
            // display above chapter headings.
            renderer.setSuppressLeadingMajorSectionHeadings(true);
            CharSequence heading = renderer.getLeadingMajorSectionHeading(sourceChapterBody);
            holder.binding.sourceTranslationHeading.setText(heading);
            holder.binding.sourceTranslationHeading.setVisibility(heading.length() > 0 ? View.VISIBLE : View.GONE);
        } else {
            sourceRendering.addEngine(renderingProvider.createDefaultRenderer());
        }
        sourceRendering.init(sourceChapterBody);
        renderedSourceBody[position] = sourceRendering.start();

        return renderedSourceBody[position];
    }

    @Override
    public CharSequence onRenderTargetText(ViewHolder holder) {
        int position = holder.getBindingAdapterPosition();
        ReadListItem item = (ReadListItem) items.get(position);

        TranslationFormat bodyFormat = item.target.getFormat();
        String chapterBody = item.getTargetText();
        RenderingGroup targetRendering = new RenderingGroup();

        if(Clickables.isClickableFormat(bodyFormat)) {
            Span.OnClickListener noteClickListener = new Span.OnClickListener() {
                @Override
                public void onClick(View view, Span span, int start, int end) {
                    if(span instanceof NoteSpan) {
                        showFootnote((NoteSpan)span);
                    }
                }
                @Override
                public void onLongClick(View view, Span span, int start, int end) {
                }
            };
            ClickableRenderingEngine renderer = renderingProvider.setupRenderingGroup(
                    bodyFormat,
                    targetRendering,
                    null,
                    noteClickListener,
                    true
            );
            renderer.setVersesEnabled(true);
        } else {
            targetRendering.addEngine(renderingProvider.createDefaultRenderer());
        }
        targetRendering.init(chapterBody);
        renderedTargetBody[position] = targetRendering.start();

        return renderedTargetBody[position];
    }

    @Override
    public void onOpenTranslationMode(String chapterSlug) {
        Bundle args = new Bundle();
        args.putBoolean(ChunkModeFragment.EXTRA_TARGET_OPEN, true);
        args.putString(Translator.EXTRA_CHAPTER_ID, chapterSlug);
        if (getListener() != null) {
            getListener().openTranslationMode(TranslationViewMode.CHUNK, args);
        }
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

    /**
     * get the chapter for the position, or null if not found
     * @param position
     * @return
     */
    public String getChapterForPosition(int position) {
        if(position < 0) {
            position = 0;
        } else if(position >= chapters.size()) {
            position = chapters.size() - 1;
        }
        return chapters.get(position);
    }

    @Override
    public void onBindManagedViewHolder(final ViewHolder holder, final int position) {
        final ReadListItem item = (ReadListItem) items.get(position);
        boolean targetOpen = targetStateOpen[position];
        String chapterSlug = chapters.get(position);
        CharSequence renderedSourceText = renderedSourceBody[position];
        CharSequence renderedTargetText = renderedTargetBody[position];

        holder.bind(item, targetOpen, chapterSlug, renderedSourceText, renderedTargetText);
    }

    @Override
    public int getItemCount() {
        return chapters.size();
    }

    /**
     * Toggle the target translation card between front and back
     * @param holder
     * @param position
     * @param swipeLeft
     * @return true if action was taken, else false
     */
    public void toggleTargetTranslationCard(final ViewHolder holder, final int position, final boolean swipeLeft) {
        if (targetStateOpen[position]) {
            closeTargetTranslationCard( holder, position, !swipeLeft);
            return;
        }
        openTargetTranslationCard( holder, position, !swipeLeft);
    }

    /**
     * Moves the target translation card to the back
     * @param holder
     * @param position
     * @param leftToRight
     */
    public void closeTargetTranslationCard(final ViewHolder holder, final int position, final boolean leftToRight) {
        if (targetStateOpen[position]) {
            ViewUtil.animateSwapCards(holder.binding.targetTranslationCard, holder.binding.sourceTranslationCard, TOP_ELEVATION, BOTTOM_ELEVATION, leftToRight, new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {

                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    targetStateOpen[position] = false;
                }

                @Override
                public void onAnimationRepeat(Animation animation) {

                }
            });

            // re-enable new tab button
            holder.binding.newTabButton.setEnabled(true);
        }
    }

    /**
     * Moves the target translation to the top
     * @param holder
     * @param position
     * @param leftToRight
     */
    public void openTargetTranslationCard(final ViewHolder holder, final int position, final boolean leftToRight) {
        if (!targetStateOpen[position]) {
            ViewUtil.animateSwapCards(
                    holder.binding.sourceTranslationCard,
                    holder.binding.targetTranslationCard,
                    TOP_ELEVATION,
                    BOTTOM_ELEVATION,
                    leftToRight,
                    new Animation.AnimationListener() {
                        @Override
                        public void onAnimationStart(Animation animation) {

                        }

                        @Override
                        public void onAnimationEnd(Animation animation) {
                            targetStateOpen[position] = true;
                        }

                        @Override
                        public void onAnimationRepeat(Animation animation) {

                        }
                    }
            );

            // disable new tab button so we don't accidentally open it
            holder.binding.newTabButton.setEnabled(false);
        }
    }

    /**
     * display selected footnote in dialog.
     *
     * @param span note span
     */
    private void showFootnote(final NoteSpan span) {
        CharSequence marker = span.getPassage();
        CharSequence title = context.getResources().getText(R.string.title_footnote);
        if (!marker.toString().isEmpty()) {
            title = title + ": " + marker;
        }
        CharSequence message = span.getNotes();

        new AlertDialog.Builder(context, R.style.AppTheme_Dialog)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.dismiss, null)
                .show();
    }

    @Override
    public Object[] getSections() {
        return chapters.toArray();
    }

    @Override
    public int getPositionForSection(int sectionIndex) {
        // not used
        return sectionIndex;
    }

    @Override
    public int getSectionForPosition(int position) {
        return position;
    }

    @Override
    public void setResourcesOpened(boolean status) {
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final FragmentReadListItemBinding binding;

        private final OnReadModeListener readModeListener;
        private final Typography typography;
        private final Context context;
        private final TabLayout.OnTabSelectedListener tabSelectedListener;

        @Nullable
        private String chapterSlug;

        public ViewHolder(
                FragmentReadListItemBinding binding,
                Typography typography,
                OnReadModeListener readModeListener
        ) {
            super(binding.getRoot());
            this.binding = binding;

            context = binding.getRoot().getContext();
            this.readModeListener = readModeListener;
            this.typography = typography;

            tabSelectedListener = new TabLayout.OnTabSelectedListener() {
                @Override
                public void onTabSelected(TabLayout.Tab tab) {
                    final String sourceTranslationId = (String) tab.getTag();
                    if (readModeListener != null) {
                        readModeListener.onSourceTranslationTabClick(sourceTranslationId);
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
                binding.targetTranslationCard.setOnClickListener(v -> {
                    if (readModeListener != null) {
                        readModeListener.onOpenTargetTranslationCard(this);
                    }
                });

                binding.sourceTranslationCard.setOnClickListener(v -> {
                    if (readModeListener != null) {
                        readModeListener.onCloseTargetTranslationCard(this);
                    }
                });

                binding.newTabButton.setOnClickListener(v -> {
                    if (readModeListener != null) {
                        readModeListener.onNewSourceTranslationTabClick();
                    }
                });

                final GestureDetector detector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onSingleTapUp(MotionEvent e) {
                        if (readModeListener != null && chapterSlug != null) {
                            readModeListener.onOpenTranslationMode(chapterSlug);
                        }
                        return true;
                    }
                });
                binding.beginTranslatingButton.setOnTouchListener((v, event) -> detector.onTouchEvent(event));
            });
        }

        public void bind(
                ReadListItem item,
                boolean isTargetOpen,
                String chapterSlug,
                @Nullable CharSequence renderedSourceText,
                @Nullable CharSequence renderedTargetText
        ) {
            int cardMargin = context.getResources().getDimensionPixelSize(R.dimen.card_margin);
            int stackedCardMargin = context.getResources().getDimensionPixelSize(R.dimen.stacked_card_margin);
            this.chapterSlug = chapterSlug;

            if(isTargetOpen) {
                // target on top
                binding.sourceTranslationCard.setElevation(BOTTOM_ELEVATION);
                binding.targetTranslationCard.setElevation(TOP_ELEVATION);
                binding.targetTranslationCard.bringToFront();
                CardView.LayoutParams targetParams = (CardView.LayoutParams)binding.targetTranslationCard.getLayoutParams();
                targetParams.setMargins(cardMargin, cardMargin, stackedCardMargin, stackedCardMargin);
                binding.targetTranslationCard.setLayoutParams(targetParams);
                CardView.LayoutParams sourceParams = (CardView.LayoutParams)binding.sourceTranslationCard.getLayoutParams();
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
                CardView.LayoutParams sourceParams = (CardView.LayoutParams)binding.sourceTranslationCard.getLayoutParams();
                sourceParams.setMargins(cardMargin, cardMargin, stackedCardMargin, stackedCardMargin);
                binding.sourceTranslationCard.setLayoutParams(sourceParams);
                CardView.LayoutParams targetParams = (CardView.LayoutParams)binding.targetTranslationCard.getLayoutParams();
                targetParams.setMargins(stackedCardMargin, stackedCardMargin, cardMargin, cardMargin);
                binding.targetTranslationCard.setLayoutParams(targetParams);
                ((View) binding.sourceTranslationCard.getParent()).requestLayout();
                ((View) binding.sourceTranslationCard.getParent()).invalidate();

                // re-enable new tab button
                binding.newTabButton.setEnabled(true);
            }

            // render the source chapter body
            CharSequence sourceText = renderedSourceText;
            if(sourceText == null) {
                if (readModeListener != null) {
                    sourceText = readModeListener.onRenderSourceText(this);
                } else {
                    sourceText = "";
                }
            }

            binding.sourceTranslationBody.setText(sourceText);
            ViewUtil.makeLinksClickable(binding.sourceTranslationBody);
            binding.sourceTranslationTitle.setText(item.getChapterTitle());

            // render the target chapter body
            CharSequence targetText = renderedTargetText;
            if(targetText == null) {
                if (readModeListener != null) {
                    targetText = readModeListener.onRenderTargetText(this);
                } else {
                    targetText = "";
                }
            }

            // display begin translation button
            if(targetText.toString().trim().isEmpty()) {
                binding.beginTranslatingButton.setVisibility(View.VISIBLE);
            } else {
                binding.beginTranslatingButton.setVisibility(View.GONE);
            }

            binding.targetTranslationBody.setText(targetText);
            ViewUtil.makeLinksClickable(binding.targetTranslationBody);

            String targetCardTitle = "";

            // look for translated chapter title first
            final ChapterTranslation chapterTranslation = item.target.getChapterTranslation(chapterSlug);
            if(null != chapterTranslation) {
                targetCardTitle = chapterTranslation.title.trim();
            }

            // if no target chapter title translation, fall back to source chapter title
            if (targetCardTitle.isEmpty() && !item.getChapterTitle().trim().isEmpty()) {
                targetCardTitle = item.getChapterTitle().trim();
            }

            if (targetCardTitle.isEmpty()) { // if no chapter titles, fall back to project title, try translated title first
                ProjectTranslation projTrans = item.target.getProjectTranslation();
                if(!projTrans.getTitle().trim().isEmpty()) {
                    targetCardTitle = projTrans.getTitle().trim() + " " + Integer.parseInt(chapterSlug);
                }
            }

            if (targetCardTitle.isEmpty()) { // fall back to project source title
                targetCardTitle = item.source.readChunk("front", "title").trim();
                if(!chapterSlug.equals("front")) {
                    try {
                        targetCardTitle += " " + Integer.parseInt(chapterSlug);
                    } catch (Exception e) {
                        targetCardTitle += " " + chapterSlug;
                    }
                }
            }

            binding.targetTranslationTitle.setText(targetCardTitle + " - " + item.target.getTargetLanguage().name);

            // load tabs
            var tabs = item.getTabs();
            renderSourceTabs(tabs, item.source.slug);

            // set up fonts
            typography.formatTitle(
                    TranslationType.SOURCE,
                    binding.sourceTranslationHeading,
                    item.source.language.slug,
                    item.source.language.direction
            );
            typography.formatTitle(
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
            typography.formatTitle(
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

            if (tabs.size() >= MAX_SOURCE_ITEMS) {
                binding.newTabButton.setVisibility(View.GONE);
            } else {
                binding.newTabButton.setVisibility(View.VISIBLE);
            }
        }

        private void renderSourceTabs(List<ContentValues> tabs, String sourceSlug) {
            binding.sourceTranslationTabs.removeOnTabSelectedListener(tabSelectedListener);
            binding.sourceTranslationTabs.removeAllTabs();

            for(ContentValues values:tabs) {
                String tag = values.getAsString("tag");
                String title = values.getAsString("title");

                if (readModeListener != null) {
                    View tabLayout = readModeListener.onCreateRemovableTabLayout(tag, title);

                    if (tabLayout != null) {
                        TabLayout.Tab tab = binding.sourceTranslationTabs.newTab();
                        tab.setTag(tag);
                        tab.setCustomView(tabLayout);
                        binding.sourceTranslationTabs.addTab(tab);
                    }

                    readModeListener.onApplyLanguageTypefaceToTab(binding.sourceTranslationTabs, values, title);
                }
            }

            // select correct tab
            for(int i = 0; i < binding.sourceTranslationTabs.getTabCount(); i ++) {
                TabLayout.Tab tab = binding.sourceTranslationTabs.getTabAt(i);
                if(sourceSlug.equals(tab.getTag())) {
                    tab.select();
                    break;
                }
            }

            // hook up listener
            binding.sourceTranslationTabs.setOnTabSelectedListener(tabSelectedListener);
        }
    }
}
