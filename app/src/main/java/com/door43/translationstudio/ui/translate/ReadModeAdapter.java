package com.door43.translationstudio.ui.translate;

import static com.door43.translationstudio.ui.translate.ChooseSourceTranslationAdapter.MAX_SOURCE_ITEMS;

import android.annotation.SuppressLint;
import android.app.Activity;
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

import com.door43.translationstudio.App;
import com.door43.translationstudio.R;
import com.door43.translationstudio.core.ChapterTranslation;
import com.door43.translationstudio.core.FrameTranslation;
import com.door43.translationstudio.core.ProjectTranslation;
import com.door43.translationstudio.core.SlugSorter;
import com.door43.translationstudio.core.TranslationFormat;
import com.door43.translationstudio.core.TargetTranslation;
import com.door43.translationstudio.core.TranslationType;
import com.door43.translationstudio.core.TranslationViewMode;
import com.door43.translationstudio.core.Translator;
import com.door43.translationstudio.core.Typography;
import com.door43.translationstudio.rendering.ClickableRenderingEngine;
import com.door43.translationstudio.rendering.Clickables;
import com.door43.translationstudio.rendering.DefaultRenderer;
import com.door43.translationstudio.rendering.RenderingGroup;
import com.door43.translationstudio.tasks.CheckForMergeConflictsTask;
import com.door43.translationstudio.ui.spannables.NoteSpan;
import com.door43.translationstudio.ui.spannables.Span;
import com.door43.widget.ViewUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.unfoldingword.door43client.Door43Client;
import org.unfoldingword.door43client.models.SourceLanguage;
import org.unfoldingword.door43client.models.TargetLanguage;
import org.unfoldingword.door43client.models.Translation;
import org.unfoldingword.resourcecontainer.ResourceContainer;
import org.unfoldingword.tools.taskmanager.ManagedTask;
import org.unfoldingword.tools.taskmanager.TaskManager;


/**
 * Created by joel on 9/9/2015.
 */
public class ReadModeAdapter extends ViewModeAdapter<ReadModeAdapter.ViewHolder>  implements ManagedTask.OnFinishedListener {

    private CharSequence[] mRenderedTargetBody = new CharSequence[0];
    private CharSequence[] mRenderedSourceBody = new CharSequence[0];

    private final String startingChapterSlug;
    private SourceLanguage mSourceLanguage;
    private final TargetLanguage mTargetLanguage;
    private boolean[] mTargetStateOpen = new boolean[0];
    private final Activity mContext;
    private static final int BOTTOM_ELEVATION = 2;
    private static final int TOP_ELEVATION = 3;
    private final TargetTranslation mTargetTranslation;
    private ResourceContainer mSourceContainer;
    private final Door43Client mLibrary;
    private final Translator mTranslator;
    private List<String> chapters = new ArrayList<>();
    private int mLayoutBuildNumber = 0;
    private ContentValues[] mTabs = new ContentValues[0];
    private Map<String, List<String>> chunks = new HashMap<>();

    public ReadModeAdapter(Activity context, String targetTranslationId, String startingChapterSlug) {
        this.startingChapterSlug = startingChapterSlug;

        mLibrary = App.getLibrary();
        mTranslator = App.getTranslator();
        mContext = context;
        mTargetTranslation = mTranslator.getTargetTranslation(targetTranslationId);
        mTargetLanguage = mTranslator.languageFromTargetTranslation(mTargetTranslation);
    }

    /**
     * Updates the source translation displayed
     * @param sourceContainer
     */
    public void setSourceContainer(ResourceContainer sourceContainer) {
        mSourceContainer = sourceContainer;
        this.chapters = new ArrayList<>();
        this.chunks = new HashMap<>();
        mLayoutBuildNumber++; // force resetting of fonts

        setListStartPosition(0);

        if(mSourceContainer != null) {
            mSourceLanguage = mLibrary.index.getSourceLanguage(mSourceContainer.language.slug);
            boolean foundStartingChapter = false;
            SlugSorter sorter = new SlugSorter();
            List<String> chapterSlugs = sorter.sort(mSourceContainer.chapters());

            for (String chapterSlug : chapterSlugs) {
                if(!foundStartingChapter && chapterSlug.equals(startingChapterSlug)) {
                    setListStartPosition(this.chapters.size());
                    foundStartingChapter = true;
                }
                this.chapters.add(chapterSlug);
                List<String> chunkSlugs = sorter.sort(mSourceContainer.chunks(chapterSlug));
                this.chunks.put(chapterSlug, chunkSlugs);
            }
        }

        mTargetStateOpen = new boolean[chapters.size()];
        mRenderedSourceBody = new CharSequence[chapters.size()];
        mRenderedTargetBody = new CharSequence[chapters.size()];

        loadTabInfo();

        triggerNotifyDataSetChanged();
        updateMergeConflict();
    }

    @Override
    public ListItem createListItem(String chapterSlug, String chunkSlug) {
        return new ReadListItem(chapterSlug, chunkSlug);
    }

    /**
     * A simple container for list items
     */
    private static class ReadListItem extends ListItem {
        public ReadListItem(String chapterSlug, String chunkSlug) {
            super(chapterSlug, chunkSlug);
        }
    }

    /**
     * check all cards for merge conflicts to see if we should show warning.  Runs as background task.
     */
    private void updateMergeConflict() {
        final List<String> mChapters = new ArrayList<>();
        final List<ListItem> mItems = new ArrayList<>();
        ManagedTask task = new ManagedTask() {
            @Override
            public void start() {
            initializeListItems(mItems, mChapters, mSourceContainer);
            }
        };
        task.addOnFinishedListener(task1 -> doCheckForMergeConflictTask(mItems, mSourceContainer, mTargetTranslation));
        TaskManager.addTask(task);
    }

    @Override
    public void onTaskFinished(ManagedTask task) {
        TaskManager.clearTask(task);

        if (task instanceof CheckForMergeConflictsTask) {
            CheckForMergeConflictsTask mergeConflictsTask = (CheckForMergeConflictsTask) task;

            final boolean mergeConflictFound = mergeConflictsTask.hasMergeConflict();
            Handler hand = new Handler(Looper.getMainLooper());
            hand.post(() -> {
                OnEventListener listener = getListener();
                if(listener != null) {
                    listener.onEnableMergeConflict(mergeConflictFound, false);
                }
            });
        }
    }

    @Override
    void onCoordinate(ViewHolder holder) {

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
    public ViewHolder onCreateManagedViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        FragmentReadListItemBinding binding = FragmentReadListItemBinding.inflate(inflater, parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void markAllChunksDone() {}

    /**
     * Rebuilds the card tabs
     */
    private void loadTabInfo() {
        List<ContentValues> tabContents = new ArrayList<>();
        String[] sourceTranslationSlugs = mTranslator.getOpenSourceTranslations(mTargetTranslation.getId());
        for(String slug:sourceTranslationSlugs) {
            Translation st = mLibrary.index().getTranslation(slug);
            if(st != null) {
                ContentValues values = new ContentValues();
                String title = st.language.name + " " + st.resource.slug.toUpperCase();
                values.put("title", title);
                // include the resource id if there are more than one
                if(mLibrary.index().getResources(st.language.slug, st.project.slug).size() > 1) {
                    values.put("title", title);
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
        int cardMargin = mContext.getResources().getDimensionPixelSize(R.dimen.card_margin);
        int stackedCardMargin = mContext.getResources().getDimensionPixelSize(R.dimen.stacked_card_margin);
        if(mTargetStateOpen[position]) {
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

        // render the source chapter body
        if(mRenderedSourceBody[position] == null) {
            String chapterBody = "";
            for(String chunk:chunks.get(chapterSlug)) {
                if(!chunk.equals("title")) {
                    chapterBody += mSourceContainer.readChunk(chapterSlug, chunk);
                }
            }
            TranslationFormat bodyFormat = TranslationFormat.parse(mSourceContainer.contentMimeType);
            RenderingGroup sourceRendering = new RenderingGroup();
            if (Clickables.isClickableFormat(bodyFormat)) {
                // TODO: add click listeners
                Span.OnClickListener noteClickListener = new Span.OnClickListener() {
                    @Override
                    public void onClick(View view, Span span, int start, int end) {
                        if(span instanceof NoteSpan) {
                            new AlertDialog.Builder(mContext,R.style.AppTheme_Dialog)
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
                CharSequence heading = renderer.getLeadingMajorSectionHeading(chapterBody);
                holder.binding.sourceTranslationHeading.setText(heading);
                holder.binding.sourceTranslationHeading.setVisibility(
                        heading.length() > 0 ? View.VISIBLE : View.GONE);
            } else {
                sourceRendering.addEngine(new DefaultRenderer());
            }
            sourceRendering.init(chapterBody);
            mRenderedSourceBody[position] = sourceRendering.start();
        }

        holder.binding.sourceTranslationBody.setText(mRenderedSourceBody[position]);
        ViewUtil.makeLinksClickable(holder.binding.sourceTranslationBody);

        String chapterTitle = mSourceContainer.readChunk(chapterSlug, "title").trim();
        if(chapterTitle.isEmpty()) {
            chapterTitle = mSourceContainer.readChunk("front", "title").trim();
            if(!chapterSlug.equals("front")) chapterTitle += " " + Integer.parseInt(chapterSlug);
        }
        holder.binding.sourceTranslationTitle.setText(chapterTitle);

        // render the target chapter body
        if(mRenderedTargetBody[position] == null) {
            TranslationFormat bodyFormat = mTargetTranslation.getFormat();
            String chapterBody = "";
            SlugSorter sorter = new SlugSorter();
            List<String> frameSlugs = sorter.sort(mSourceContainer.chunks(chapterSlug));
            for (String frameSlug : frameSlugs) {
                FrameTranslation frameTranslation = mTargetTranslation.getFrameTranslation(chapterSlug, frameSlug, bodyFormat);
                chapterBody += " " + frameTranslation.body;
            }
            RenderingGroup targetRendering = new RenderingGroup();
            if(Clickables.isClickableFormat(bodyFormat)) {
                // TODO: add click listeners
                ClickableRenderingEngine renderer = Clickables.setupRenderingGroup(bodyFormat, targetRendering, null, null, true);
                renderer.setVersesEnabled(true);
            } else {
                targetRendering.addEngine(new DefaultRenderer());
            }
            targetRendering.init(chapterBody);
            mRenderedTargetBody[position] = targetRendering.start();
        }

        // display begin translation button
        if(mRenderedTargetBody[position].toString().trim().isEmpty()) {
            holder.binding.beginTranslatingButton.setVisibility(View.VISIBLE);
            final GestureDetector detector = new GestureDetector(mContext, new GestureDetector.SimpleOnGestureListener() {
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

        // TODO: indicate completed chapter translations
//        if(frameTranslation.isTitleFinished()) {
//            holder.mTargetInnerCard.setBackgroundResource(R.color.white);
//        } else {
//            holder.mTargetInnerCard.setBackgroundResource(R.drawable.paper_repeating);
//        }

        holder.binding.targetTranslationBody.setText(mRenderedTargetBody[position]);

//        ChapterTranslation getChapterTranslation(String chapterSlug);

        String targetCardTitle = "";

        // look for translated chapter title first
        final ChapterTranslation chapterTranslation = mTargetTranslation.getChapterTranslation(chapterSlug);
        if(null != chapterTranslation) {
            targetCardTitle = chapterTranslation.title.trim();
        }

        if (targetCardTitle.isEmpty() && !chapterTitle.trim().isEmpty()) { // if no target chapter title translation, fall back to source chapter title
            targetCardTitle = chapterTitle.trim();
        }

        if (targetCardTitle.isEmpty()) { // if no chapter titles, fall back to project title, try translated title first
            ProjectTranslation projTrans = mTargetTranslation.getProjectTranslation();
            if(!projTrans.getTitle().trim().isEmpty()) {
                targetCardTitle = projTrans.getTitle().trim() + " " + Integer.parseInt(chapterSlug);
            }
        }

        if (targetCardTitle.isEmpty()) { // fall back to project source title
            targetCardTitle = mSourceContainer.readChunk("front", "title").trim();
            if(!chapterSlug.equals("front")) targetCardTitle += " " + Integer.parseInt(chapterSlug);
        }

        holder.binding.targetTranslationTitle.setText(targetCardTitle + " - " + mTargetLanguage.name);

        // load tabs
        holder.binding.sourceTranslationTabs.setOnTabSelectedListener(null);
        holder.binding.sourceTranslationTabs.removeAllTabs();
        for(ContentValues values:mTabs) {
            String tag = values.getAsString("tag");
            String title = values.getAsString("title");
            View tabLayout = createRemovableTabLayout(mContext, getListener(), tag, title);

            TabLayout.Tab tab = holder.binding.sourceTranslationTabs.newTab();
            tab.setTag(tag);
            tab.setCustomView(tabLayout);
            holder.binding.sourceTranslationTabs.addTab(tab);

            applyLanguageTypefaceToTab(mContext, holder.binding.sourceTranslationTabs, values, title);
        }

        // select correct tab
        for(int i = 0; i < holder.binding.sourceTranslationTabs.getTabCount(); i ++) {
            TabLayout.Tab tab = holder.binding.sourceTranslationTabs.getTabAt(i);
            if(tab.getTag().equals(mSourceContainer.slug)) {
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
        if(holder.mLayoutBuildNumber != mLayoutBuildNumber) {
            holder.mLayoutBuildNumber = mLayoutBuildNumber;
            Typography.formatTitle(mContext, TranslationType.SOURCE, holder.binding.sourceTranslationHeading, mSourceLanguage.slug, mSourceLanguage.direction);
            Typography.formatTitle(mContext, TranslationType.SOURCE, holder.binding.sourceTranslationTitle, mSourceLanguage.slug, mSourceLanguage.direction);
            Typography.format(mContext, TranslationType.SOURCE, holder.binding.sourceTranslationBody, mSourceLanguage.slug, mSourceLanguage.direction);
            Typography.formatTitle(mContext, TranslationType.TARGET, holder.binding.targetTranslationTitle, mTargetLanguage.slug, mTargetLanguage.direction);
            Typography.format(mContext, TranslationType.TARGET, holder.binding.targetTranslationBody, mTargetLanguage.slug, mTargetLanguage.direction);
        }

        if (mTabs.length >= MAX_SOURCE_ITEMS) {
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
        if (mTargetStateOpen[position]) {
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
        if (mTargetStateOpen[position]) {
            ViewUtil.animateSwapCards(holder.binding.targetTranslationCard, holder.binding.sourceTranslationCard, TOP_ELEVATION, BOTTOM_ELEVATION, leftToRight, new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {

                }

                @Override
                public void onAnimationEnd(Animation animation) {
                    mTargetStateOpen[position] = false;
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
        if (!mTargetStateOpen[position]) {
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
                            mTargetStateOpen[position] = true;
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

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public final FragmentReadListItemBinding binding;
        public int mLayoutBuildNumber = -1;

        public ViewHolder(FragmentReadListItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
