package com.door43.translationstudio.ui.translate;

import static com.door43.translationstudio.ui.translate.ChooseSourceTranslationAdapter.MAX_SOURCE_ITEMS;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.door43.translationstudio.databinding.FragmentReadListItemBinding;
import com.google.android.material.tabs.TabLayout;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;

import com.door43.translationstudio.R;
import com.door43.translationstudio.core.ChapterTranslation;
import com.door43.translationstudio.core.ProjectTranslation;
import com.door43.translationstudio.core.TranslationFormat;
import com.door43.translationstudio.core.TranslationType;
import com.door43.translationstudio.core.TranslationViewMode;
import com.door43.translationstudio.core.Translator;
import com.door43.translationstudio.core.Typography;
import com.door43.translationstudio.rendering.ClickableRenderingEngine;
import com.door43.translationstudio.rendering.Clickables;
import com.door43.translationstudio.rendering.DefaultRenderer;
import com.door43.translationstudio.rendering.RenderingGroup;
import com.door43.translationstudio.ui.spannables.NoteSpan;
import com.door43.translationstudio.ui.spannables.Span;
import com.door43.widget.ViewUtil;

import java.util.List;

import org.unfoldingword.tools.taskmanager.ManagedTask;
import org.unfoldingword.tools.taskmanager.TaskManager;


/**
 * Created by joel on 9/9/2015.
 */
public class ReadModeAdapter extends ViewModeAdapter<ReadModeAdapter.ViewHolder> {
    private static final int BOTTOM_ELEVATION = 2;
    private static final int TOP_ELEVATION = 3;

    private CharSequence[] renderedTargetBody = new CharSequence[0];
    private CharSequence[] renderedSourceBody = new CharSequence[0];

    private boolean[] targetStateOpen = new boolean[0];

    public ReadModeAdapter() {
    }

    @Override
    public void initializeListItems(
            List<ListItem> items,
            String startingChapter,
            String startingChunk
    ) {
        chapters.clear();
        this.items.clear();

        setListStartPosition(0);
        boolean foundStartingChapter = false;

        for (ListItem item: items) {
            if (!foundStartingChapter && item.chapterSlug.equals(startingChapter)) {
                setListStartPosition(this.items.size());
                foundStartingChapter = true;
            }
            if (!chapters.contains(item.chapterSlug)) {
                chapters.add(item.chapterSlug);
                this.items.add(createListItem(item));
            }
        }

        filteredChapters = chapters;
        filteredItems = this.items;

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
     * check all cards for merge conflicts to see if we should show warning.  Runs as background task.
     */
    private void updateMergeConflict() {
        doCheckForMergeConflictTask();
    }

    @Override
    public void onTaskFinished(ManagedTask task) {
        TaskManager.clearTask(task);
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
        return new ViewHolder(binding);
    }

    @Override
    public void markAllChunksDone() {}

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

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onBindManagedViewHolder(final ViewHolder holder, final int position) {
        int cardMargin = context.getResources().getDimensionPixelSize(R.dimen.card_margin);
        int stackedCardMargin = context.getResources().getDimensionPixelSize(R.dimen.stacked_card_margin);
        if(targetStateOpen[position]) {
            // target on top
            holder.binding.sourceTranslationCard.setElevation(BOTTOM_ELEVATION);
            holder.binding.targetTranslationCard.setElevation(TOP_ELEVATION);
            holder.binding.targetTranslationCard.bringToFront();
            CardView.LayoutParams targetParams = (CardView.LayoutParams)holder.binding.targetTranslationCard.getLayoutParams();
            targetParams.setMargins(cardMargin, cardMargin, stackedCardMargin, stackedCardMargin);
            holder.binding.targetTranslationCard.setLayoutParams(targetParams);
            CardView.LayoutParams sourceParams = (CardView.LayoutParams)holder.binding.sourceTranslationCard.getLayoutParams();
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
            CardView.LayoutParams sourceParams = (CardView.LayoutParams)holder.binding.sourceTranslationCard.getLayoutParams();
            sourceParams.setMargins(cardMargin, cardMargin, stackedCardMargin, stackedCardMargin);
            holder.binding.sourceTranslationCard.setLayoutParams(sourceParams);
            CardView.LayoutParams targetParams = (CardView.LayoutParams)holder.binding.targetTranslationCard.getLayoutParams();
            targetParams.setMargins(stackedCardMargin, stackedCardMargin, cardMargin, cardMargin);
            holder.binding.targetTranslationCard.setLayoutParams(targetParams);
            ((View) holder.binding.sourceTranslationCard.getParent()).requestLayout();
            ((View) holder.binding.sourceTranslationCard.getParent()).invalidate();

            // re-enable new tab button
            holder.binding.newTabButton.setEnabled(true);
        }

        holder.binding.targetTranslationCard.setOnClickListener(v ->
                openTargetTranslationCard(holder, position)
        );
        holder.binding.sourceTranslationCard.setOnClickListener(v ->
                closeTargetTranslationCard(holder, position)
        );

        holder.binding.newTabButton.setOnClickListener(v -> {
            if (getListener() != null) {
                getListener().onNewSourceTranslationTabClick();
            }
        });

        final String chapterSlug = chapters.get(position);
        final ReadListItem item = (ReadListItem) items.get(position);

        // render the source chapter body
        if(renderedSourceBody[position] == null) {
            String sourceChapterBody = item.getSourceChapterBody();
            TranslationFormat bodyFormat = TranslationFormat.parse(item.source.contentMimeType);
            RenderingGroup sourceRendering = new RenderingGroup();
            if (Clickables.isClickableFormat(bodyFormat)) {
                // TODO: add click listeners
                Span.OnClickListener noteClickListener = new Span.OnClickListener() {
                    @Override
                    public void onClick(View view, Span span, int start, int end) {
                        if(span instanceof NoteSpan) {
                            new AlertDialog.Builder(context, R.style.AppTheme_Dialog)
                                    .setTitle(R.string.title_footnote)
                                    .setMessage(((NoteSpan)span).getNotes())
                                    .setPositiveButton(R.string.dismiss, null)
                                    .show();
                        }
                    }
                    @Override
                    public void onLongClick(View view, Span span, int start, int end) {
                    }
                };
                ClickableRenderingEngine renderer = Clickables.setupRenderingGroup(bodyFormat, sourceRendering, null, noteClickListener, true);

                // In read mode (and only in read mode), pull leading major section headings out for
                // display above chapter headings.
                renderer.setSuppressLeadingMajorSectionHeadings(true);
                CharSequence heading = renderer.getLeadingMajorSectionHeading(sourceChapterBody);
                holder.binding.sourceTranslationHeading.setText(heading);
                holder.binding.sourceTranslationHeading.setVisibility(
                        heading.length() > 0 ? View.VISIBLE : View.GONE);
            } else {
                sourceRendering.addEngine(new DefaultRenderer());
            }
            sourceRendering.init(sourceChapterBody);
            renderedSourceBody[position] = sourceRendering.start();
        }

        holder.binding.sourceTranslationBody.setText(renderedSourceBody[position]);
        ViewUtil.makeLinksClickable(holder.binding.sourceTranslationBody);

        holder.binding.sourceTranslationTitle.setText(item.getChapterTitle());

        // render the target chapter body
        if(renderedTargetBody[position] == null) {
            TranslationFormat bodyFormat = item.target.getFormat();
            String chapterBody = item.getTargetChapterBody();
            RenderingGroup targetRendering = new RenderingGroup();
            if(Clickables.isClickableFormat(bodyFormat)) {
                // TODO: add click listeners
                ClickableRenderingEngine renderer = Clickables.setupRenderingGroup(
                        bodyFormat,
                        targetRendering,
                        null,
                        null,
                        true
                );
                renderer.setVersesEnabled(true);
            } else {
                targetRendering.addEngine(new DefaultRenderer());
            }
            targetRendering.init(chapterBody);
            renderedTargetBody[position] = targetRendering.start();
        }

        // display begin translation button
        if(renderedTargetBody[position].toString().trim().isEmpty()) {
            holder.binding.beginTranslatingButton.setVisibility(View.VISIBLE);
            final GestureDetector detector = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onSingleTapUp(MotionEvent e) {
                    Bundle args = new Bundle();
                    args.putBoolean(ChunkModeFragment.EXTRA_TARGET_OPEN, true);
                    args.putString(Translator.EXTRA_CHAPTER_ID, chapterSlug);
                    getListener().openTranslationMode(TranslationViewMode.CHUNK, args);
                    return true;
                }
            });
            holder.binding.beginTranslatingButton.setOnTouchListener((v, event) -> detector.onTouchEvent(event));
        } else {
            holder.binding.beginTranslatingButton.setVisibility(View.GONE);
        }

        holder.binding.targetTranslationBody.setText(renderedTargetBody[position]);

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
            if(!chapterSlug.equals("front")) targetCardTitle += " " + Integer.parseInt(chapterSlug);
        }

        holder.binding.targetTranslationTitle.setText(targetCardTitle + " - " + item.target.getTargetLanguage().name);

        // load tabs
        holder.binding.sourceTranslationTabs.setOnTabSelectedListener(null);
        holder.binding.sourceTranslationTabs.removeAllTabs();

        var tabs = item.getTabs().invoke().toArray(new ContentValues[0]);
        for(ContentValues values:tabs) {
            String tag = values.getAsString("tag");
            String title = values.getAsString("title");
            View tabLayout = createRemovableTabLayout(context, getListener(), tag, title);

            TabLayout.Tab tab = holder.binding.sourceTranslationTabs.newTab();
            tab.setTag(tag);
            tab.setCustomView(tabLayout);
            holder.binding.sourceTranslationTabs.addTab(tab);

            applyLanguageTypefaceToTab(context, holder.binding.sourceTranslationTabs, values, title);
        }

        // select correct tab
        for(int i = 0; i < holder.binding.sourceTranslationTabs.getTabCount(); i ++) {
            TabLayout.Tab tab = holder.binding.sourceTranslationTabs.getTabAt(i);
            if(tab.getTag().equals(item.source.slug)) {
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

        // set up fonts
        if(holder.layoutBuildNumber != layoutBuildNumber) {
            holder.layoutBuildNumber = layoutBuildNumber;
            Typography.formatTitle(context, TranslationType.SOURCE, holder.binding.sourceTranslationHeading, item.source.language.slug, item.source.language.direction);
            Typography.formatTitle(context, TranslationType.SOURCE, holder.binding.sourceTranslationTitle, item.source.language.slug, item.source.language.direction);
            Typography.format(context, TranslationType.SOURCE, holder.binding.sourceTranslationBody, item.source.language.slug, item.source.language.direction);
            Typography.formatTitle(context, TranslationType.TARGET, holder.binding.targetTranslationTitle, item.target.getTargetLanguage().slug, item.target.getTargetLanguage().direction);
            Typography.format(context, TranslationType.TARGET, holder.binding.targetTranslationBody, item.target.getTargetLanguage().slug, item.target.getTargetLanguage().direction);
        }

        if (tabs.length >= MAX_SOURCE_ITEMS) {
            holder.binding.newTabButton.setVisibility(View.GONE);
        } else {
            holder.binding.newTabButton.setVisibility(View.VISIBLE);
        }
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
     * Moves the target translation card to the back - left to right
     * @param holder
     * @param position
     * @return true if action was taken, else false
     */
    public void closeTargetTranslationCard(final ViewHolder holder, final int position) {
        closeTargetTranslationCard ( holder, position, true);
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
     * Moves the target translation to the top
     * @param holder
     * @param position
     * @return true if action was taken, else false
     */
    public void openTargetTranslationCard(final ViewHolder holder, final int position) {
        openTargetTranslationCard( holder, position, false);
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
        public int layoutBuildNumber = -1;

        public ViewHolder(FragmentReadListItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
