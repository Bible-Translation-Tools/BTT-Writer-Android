package com.door43.translationstudio.ui.translate;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import org.unfoldingword.door43client.models.Translation;
import org.unfoldingword.resourcecontainer.ResourceContainer;
import org.unfoldingword.tools.logger.Logger;

import com.door43.translationstudio.App;
import com.door43.translationstudio.R;
import com.door43.translationstudio.core.ContainerCache;
import com.door43.translationstudio.core.TranslationViewMode;
import com.door43.translationstudio.core.Translator;
import com.door43.translationstudio.core.entity.SourceTranslation;
import com.door43.translationstudio.databinding.FragmentStackedCardListBinding;
import com.door43.translationstudio.ui.BaseFragment;
import com.door43.translationstudio.ui.dialogs.ProgressHelper;
import com.door43.translationstudio.ui.translate.review.SearchSubject;
import com.door43.translationstudio.ui.viewmodels.TargetTranslationViewModel;

import org.json.JSONException;
import org.unfoldingword.tools.taskmanager.ManagedTask;

import java.util.ArrayList;
import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Created by joel on 9/18/2015.
 */
@AndroidEntryPoint
public abstract class ViewModeFragment extends BaseFragment implements ViewModeAdapter.OnEventListener,
        ChooseSourceTranslationDialog.OnClickListener,
        ManagedTask.OnFinishedListener {

    public static final String TAG = ViewModeFragment.class.getSimpleName();
    private LinearLayoutManager layoutManager;
    private ViewModeAdapter adapter;
    private boolean fingerScroll = false;
    private OnEventListener listener;
    private GestureDetector gesture;
    private ProgressHelper.ProgressDialog progressDialog = null;

    protected FragmentStackedCardListBinding binding;
    protected TargetTranslationViewModel viewModel;

    protected String chapterSlug;
    protected String chunkSlug;

    /**
     * Returns an instance of the adapter
     * @return
     */
    abstract ViewModeAdapter generateAdapter();

    /**
     * Resets the static variables
     */
    public static void reset() {
        ContainerCache.empty();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        adapter = generateAdapter();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentStackedCardListBinding.inflate(inflater, container, false);
        viewModel = new ViewModelProvider(requireActivity()).get(TargetTranslationViewModel.class);

        Bundle args = getArguments();
        assert args != null;

        chapterSlug = args.getString(Translator.EXTRA_CHAPTER_ID, null);
        chunkSlug = args.getString(Translator.EXTRA_FRAME_ID, null);

        progressDialog = ProgressHelper.newInstance(
                requireContext(),
                R.string.loading_sources,
                false
        );

        viewModel.setSelectedResourceContainer();

        // TRICKY: there is a bug in Android's LinearLayoutManager
        layoutManager = new WrapContentLinearLayoutManager(getActivity());
        binding.recyclerView.setLayoutManager(layoutManager);
        binding.recyclerView.setItemAnimator(new DefaultItemAnimator());
        binding.recyclerView.setAdapter(adapter);
        binding.recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                fingerScroll = true;
                super.onScrollStateChanged(recyclerView, newState);
            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (fingerScroll) {
                    int position = getCurrentPosition();
                    if(listener != null) listener.onScrollProgress(position);
                }
            }
        });

        // notify activity contents changed
        onDataSetChanged(adapter.getItemCount());

        adapter.setOnClickListener(this);

        gesture = new GestureDetector(new GestureDetector.SimpleOnGestureListener() {
            public MotionEvent mLastOnDownEvent;
            private final float SWIPE_THRESHOLD_VELOCITY = 20f;
            private final float SWIPE_MIN_DISTANCE = 50f;
            private final float SWIPE_MAX_ANGLE_DEG = 30;
            @Override
            public boolean onDown(MotionEvent e) {
                mLastOnDownEvent = e;
                return super.onDown(e);
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if(e1 == null) {
                    e1 = mLastOnDownEvent;
                }
                try {
                    float distanceX = e2.getX() - e1.getX();
                    float distanceY = e2.getY() - e1.getY();
                    // don't handle vertical swipes (division error)
                    if (distanceX == 0) return false;

                    double flingAngle = Math.toDegrees(Math.asin(Math.abs(distanceY / distanceX)));
                    if (flingAngle <= SWIPE_MAX_ANGLE_DEG && Math.abs(distanceX) >= SWIPE_MIN_DISTANCE && Math.abs(velocityX) >= SWIPE_THRESHOLD_VELOCITY) {
                        if (distanceX > 0) {
                            onRightSwipe(e1, e2);
                        } else {
                            onLeftSwipe(e1, e2);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return false;
            }
        });

        // let child classes modify the view
        onPrepareView(binding.getRoot());
        setupObservers();

        return binding.getRoot();
    }

    protected void setupObservers() {
        viewModel.getProgress().observe(getViewLifecycleOwner(), progress -> {
            if (progress != null) {
                if (progressDialog != null) {
                    progressDialog.show();
                    progressDialog.setProgress(progress.getProgress());
                    progressDialog.setMessage(progress.getMessage());
                    progressDialog.setMax(progress.getMax());
                }
            } else {
                if (progressDialog != null) progressDialog.dismiss();
            }
        });
        viewModel.getListItems().observe(getViewLifecycleOwner(), items -> {
            if (!items.isEmpty()) {
                if (adapter != null) {
                    if (chapterSlug == null) {
                        chapterSlug = viewModel.getLastFocusChapterId();
                    }
                    if (chunkSlug == null) {
                        chunkSlug = viewModel.getLastFocusFrameId();
                    }
                    adapter.initializeListItems(items, chapterSlug, chunkSlug);
                    doScrollToPosition(adapter.getListStartPosition(), 0);
                }
            } else {
                if(listener != null) {
                    listener.onNoSourceTranslations();
                }
            }
        });
    }

    /**
     * scroll panes to go to specific position with vertical offset
     * @param position
     * @param offset
     */
    public void doScrollToPosition(int position, int offset) {
        if(layoutManager != null) {
            layoutManager.scrollToPositionWithOffset(position, offset);
            Logger.i(TAG, "doScrollToPosition: position=" + position + ", offset=" + offset);
        }
        if(listener != null) listener.onScrollProgress(position);
    }

    /**
     * set true if we want to initially show a summary of merge conflicts
     * @param showMergeSummary
     */
    public void setShowMergeSummary(boolean showMergeSummary) {
        if(adapter != null) {
            adapter.setShowMergeSummary(showMergeSummary);
        }
    }

    /**
     * get the chapter slug for the position
     * @param position
     */
    public String getChapterSlug(int position) {
        if(adapter != null) return adapter.getChapterSlug(position);
        return Integer.toString(position + 1);
    }

    /**
     * Called when the user performs a swipe towards the right
     * @param e1
     * @param e2
     */
    protected void onRightSwipe(MotionEvent e1, MotionEvent e2) {
    }

    /**
     * Called when the user performs a swipe towards the left
     * @param e1
     * @param e2
     */
    protected void onLeftSwipe(MotionEvent e1, MotionEvent e2) {
    }

    /**
     * Returns the currently selected resource container
     * @return
     */
    protected ResourceContainer getSelectedResourceContainer() {
        return viewModel.getResourceContainer();
    }

    /**
     * Scrolls to the given frame
     * // TODO: 11/2/16 this does not scroll to the correct chunk. see obs 1:15. it seems to always be 2 off.
     * @param chapterSlug
     * @param chunkSlug
     */
    public void scrollToChunk(String chapterSlug, String chunkSlug) {
        closeKeyboard();
        int position = adapter.getItemPosition(chapterSlug, chunkSlug);
        if(position != -1) {
            doScrollToPosition(position, 0);
        }
    }

    /**
     * Similar to scrollToChunk except it will automatically guess what chunk to scroll to.
     * @param chapterSlug
     * @param verseSlug
     */
    public void scrollToVerse(String chapterSlug, String verseSlug) {
        String chunkSlug = getVerseChunk(chapterSlug, verseSlug);
        scrollToChunk(chapterSlug, chunkSlug);
    }

    /**
     * Returns the corresponding chunk slug.
     * Override this method if you need to map verses to chunks.
     *
     * @param chapterSlug
     * @param verseSlug
     * @return
     */
    public String getVerseChunk(String chapterSlug, String verseSlug) {
        // stub
        return verseSlug;
    }

    /**
     * Returns the adapter position of a view holder under the coordinates
     * @param x
     * @param y
     * @return
     */
    protected int findViewHolderAdapterPosition(float x, float y) {
        View view = binding.recyclerView.findChildViewUnder(x, y);
        if (view != null) {
            return binding.recyclerView.getChildAdapterPosition(view);
        } else {
            return 0;
        }
    }

    /**
     * Returns a viewholder item for the adapter position
     * @param position
     * @return
     */
    protected RecyclerView.ViewHolder getViewHolderForAdapterPosition(int position) {
        return binding.recyclerView.findViewHolderForAdapterPosition(position);
    }

    /**
     * Returns a sample viewholder so we can check on the state of the ui
     * @return
     */
    protected RecyclerView.ViewHolder getViewHolderSample() {
        if(layoutManager != null) {
            int position = getCurrentPosition();
            return binding.recyclerView.findViewHolderForLayoutPosition(position);
        } else {
            return null;
        }
    }

    protected void onPrepareView(View rootView) {
        // place holder so child classes can modify the view
    }

    protected ViewModeAdapter getAdapter() {
        return adapter;
    }

    public void onDataSetChanged(int count) {
        if(listener != null) listener.onDataSetChanged(count);
    }

    public void onEnableMergeConflict(boolean showConflicted, boolean active) {
        if(listener != null) {
            listener.onEnableMergeConflict(showConflicted, active);
        }
    }

    @Override
    public RecyclerView.ViewHolder getVisibleViewHolder(int position) {
        if(layoutManager != null) {
            return binding.recyclerView.findViewHolderForAdapterPosition(position);
        }
        return null;
    }

    /**
     * gets item count of adapter
     * @return
     */
    public int getItemCount() {
        if(adapter != null) {
            return adapter.getItemCount();
        }
        return 0;
    }

    /**
     * Filters the adapter by the constraint
     * @param constraint the search will be cleared if null or an empty string
     * @param subject the text to be searched
     */
    public final void filter(CharSequence constraint, SearchSubject subject) {
        if(getAdapter() != null) {
            getAdapter().filter(constraint, subject, getCurrentPosition());
            getAdapter().triggerNotifyDataSetChanged();
        }
    }

    /**
     * move to next/previous search item
     * @param next if true then find next, otherwise will find previous
     */
    public void onMoveSearch(boolean next) {
        if(getAdapter() != null) {
            getAdapter().onMoveSearch(next);
        }
    }

    /**
     * Checks if filtering is enabled.
     */
    public final boolean hasFilter() {
        if(getAdapter() != null) return getAdapter().hasFilter();
        return false;
    }

    /**
     * returns true if merge conflict summary dialog is being displayed.  Override in adapters that
     *      support this.
     * @return
     */
    public boolean ismMergeConflictSummaryDisplayed() {
        if(getAdapter() != null) {
            return getAdapter().isMergeConflictSummaryDisplayed();
        }
        return false;
    }

    /**
     * Forces the software keyboard to close
     */
    public void closeKeyboard() {
        App.closeKeyboard(getActivity());
    }

    @Override
    public void onTranslationWordClick(String resourceContainerSlug, String chapterSlug, int width) {

    }

    @Override
    public void onTranslationArticleClick(String volume, String manual, String slug, int width) {

    }

    @Override
    public void onTranslationNoteClick(TranslationHelp note, int width) {

    }

    @Override
    public void onTranslationQuestionClick(TranslationHelp question, int width) {

    }

    /**
     * Require correct interface
     * @param context Activity context
     */
    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        onAttachToContext(context);
    }

    /**
     * This method will be called when the fragment attaches to the context/activity
     * @param context
     */
    protected void onAttachToContext(Context context) {
        try {
            this.listener = (OnEventListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context + " must implement ViewModeFragment.OnEventListener");
        }
    }

    /**
     * Called when the scroll progress manually changes
     * @param scrollProgress
     * @param percent - percentage to scroll within card
     */
    public void onScrollProgressUpdate(int scrollProgress, int percent) {
        fingerScroll = false;
        Log.d(TAG, "onScrollProgressUpdate: scrollProgress=" + scrollProgress + ", percent=" + percent);
        if(percent == 0) {
            binding.recyclerView.scrollToPosition(scrollProgress);
        } else {
            fineScrollToPosition(scrollProgress, percent);
        }
    }

    /**
     * makes sure view is visible, plus it scrolls down proportionally in view
     * @param position
     * @param percent - percentage to scroll within card
     * @return
     */
    private void fineScrollToPosition(int position, int percent) {

        binding.recyclerView.scrollToPosition(position); // do coarse adjustment

        View visibleChild = binding.recyclerView.getChildAt(0);
        if (visibleChild == null) {
            return;
        }

        RecyclerView.ViewHolder holder = binding.recyclerView.getChildViewHolder(visibleChild);
        if(holder == null) {
            return;
        }

        int itemHeight = holder.itemView.getHeight();
        int offset = (int) (percent * itemHeight / 100);

        LinearLayoutManager layoutManager = (LinearLayoutManager) binding.recyclerView.getLayoutManager();
        if (layoutManager != null) {
            layoutManager.scrollToPositionWithOffset(position, -offset);
        }
    }

    public void setScrollProgress(int position) {
        // TODO: 6/28/16 update scrollbar
    }

    @Override
    public void onSourceTranslationTabClick(String sourceTranslationId) {
        chapterSlug = null;
        chunkSlug = null;
        updateListStartPosition();
        viewModel.setSelectedResourceContainer(sourceTranslationId);
    }

    @Override
    public void onSourceRemoveButtonClicked(String sourceTranslationId) {
        viewModel.removeOpenSourceTranslation(sourceTranslationId);
        String[] sourceTranslationIds = viewModel.getOpenSourceTranslations();

        if (sourceTranslationIds.length > 0) {
            String selectedSourceId = viewModel.getSelectedSourceTranslationId();
            viewModel.setSelectedResourceContainer(selectedSourceId);
        } else {
            if (listener != null) {
                listener.onNoSourceTranslations();
            }
        }
    }

    @Override
    public void onNewSourceTranslationTabClick() {
        FragmentTransaction ft = getParentFragmentManager().beginTransaction();
        Fragment prev = getParentFragmentManager().findFragmentByTag("tabsDialog");
        if (prev != null) {
            ft.remove(prev);
        }
        ft.addToBackStack(null);

        ChooseSourceTranslationDialog dialog = new ChooseSourceTranslationDialog();
        Bundle args = new Bundle();
        args.putString(
            ChooseSourceTranslationDialog.ARG_TARGET_TRANSLATION_ID,
            viewModel.getTargetTranslation().getId()
        );
        dialog.setOnClickListener(this);
        dialog.setArguments(args);
        dialog.show(ft, "tabsDialog");
    }

    @Override
    public void onCancelTabsDialog(String targetTranslationId) {

    }

    @Override
    public void onConfirmTabsDialog(List<String> sourceTranslationIds) {
        String[] oldSourceTranslationIds = viewModel.getOpenSourceTranslations();
        for (String id : oldSourceTranslationIds) {
            viewModel.removeOpenSourceTranslation(id);
        }
        if (!sourceTranslationIds.isEmpty()) {
            setSelectedSources(sourceTranslationIds);
            String selectedSourceId = viewModel.getSelectedSourceTranslationId();
            viewModel.setSelectedResourceContainer(selectedSourceId);
        } else {
            if (listener != null) listener.onNoSourceTranslations();
        }
    }

    private void setSelectedSources(List<String> sourceSlugs) {
        List<SourceTranslation> sources = new ArrayList<>();
        for (String slug : sourceSlugs) {
            try {
                viewModel.addOpenSourceTranslation(slug);
            } catch (Exception e) {
                Logger.e(
                        this.getClass().getName(),
                        "Error while adding source " + slug + " for " + viewModel.getTargetTranslation().getId()
                );
                e.printStackTrace();
            }

            Translation translation = viewModel.getTranslation(slug);
            if (translation != null) {
                int modifiedAt = viewModel.getResourceContainerLastModified(translation);
                sources.add(new SourceTranslation(translation, modifiedAt));
            }
        }

        try {
            viewModel.getTargetTranslation().setSourceTranslations(sources);
        } catch (JSONException e) {
            Logger.e(
                    this.getClass().getName(),
                    "Failed to set source translations for the target translation " + viewModel.getTargetTranslation().getId(), e
            );
        }
    }

    /**
     * Updates the position where the list should start when first built
     */
    private void updateListStartPosition() {
        int lastItemPosition = getCurrentPosition();
        String chapterId = adapter.getFocusedChapterSlug(lastItemPosition);
        String frameId = adapter.getFocusedChunkSlug(lastItemPosition);
        if (chapterId != null) {
            viewModel.setLastFocus(chapterId, frameId);
            adapter.setListStartPosition(lastItemPosition);
        }
    }

    @Override
    public void onDestroy() {
        viewModel.cancelGeneralJobs();
        viewModel.cancelRenderJobs();
        if(layoutManager != null) {
            // save position state
            updateListStartPosition();
        }
        if(adapter != null) adapter.setOnClickListener(null);
        super.onDestroy();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        if (progressDialog != null) {
            progressDialog.dismiss();
            progressDialog = null;
        }
    }

    /**
     * gets the currently viewed position
     * @return
     */
    public int getCurrentPosition() {
        if(layoutManager != null) {
            return layoutManager.findFirstVisibleItemPosition();
        }
        return 0;
    }

    /**
     * Receives touch events directly from the activity
     * Some times click events can get consumed by a list view or some other object.
     * Receiving events directly from the activity avoids these issues
     *
     * @param event
     * @return
     */
    public boolean onTouchEvent(MotionEvent event) {
        if(gesture != null) {
            return gesture.onTouchEvent(event);
        } else {
            Logger.w(this.getClass().getName(), "The gesture detector was not initialized so the touch was not handled");
            return false;
        }
    }

    /**
     * Opens a translation mode
     * @param mode
     */
    public void openTranslationMode(TranslationViewMode mode, Bundle extras) {
        if(listener != null) listener.openTranslationMode(mode, extras);
    }

    /**
     * Restarts the auto commit timer
     */
    public void restartAutoCommitTimer() {
        if(listener != null) listener.restartAutoCommitTimer();
    }

    /**
     * notify listener of search state changes
     * @param doingSearch - search is currently processing
     * @param numberOfChunkMatches - number of chunks that have the search string
     * @param atEnd - we are at last search item highlighted
     * @param atStart - we are at first search item highlighted
     */
    public void onSearching(boolean doingSearch, int numberOfChunkMatches, boolean atEnd, boolean atStart) {
        if(listener != null) listener.onSearching(doingSearch, numberOfChunkMatches, atEnd, atStart);
    }

    /**
     * user has selected to update sources
     */
    public void onUpdateSources() {
        if(listener != null) listener.onUpdateSources();
    }

    @Override
    public void onTaskFinished(final ManagedTask task) {
    }

    /**
     * called to set new selected position
     * @param position
     * @param offset - if greater than or equal to 0, then set specific offset
     */
    public void onSetSelectedPosition(int position, int offset) {
        Log.d(TAG, "onSetSelectedPosition: position=" + position + ", offset=" + offset);
        doScrollToPosition(position, offset);
    }

    protected abstract void markAllChunksDone();

    public interface OnEventListener {

        /**
         * Called when the user scrolls with their finger
         * @param progress
         */
        void onScrollProgress(int progress);

        /**
         * Called when the dataset in the adapter changed
         * @param count the number of items in the adapter
         */
        void onDataSetChanged(int count);

        /**
         * No source translation has been chosen
         */
        void onNoSourceTranslations();

        /**
         * Opens a particular translation mode
         * @param mode
         */
        void openTranslationMode(TranslationViewMode mode, Bundle extras);

        /**
         * Restarts the timer to auto commit changes
         */
        void restartAutoCommitTimer();

        /**
         * notify listener of search state changes
         * @param doingSearch - search is currently processing
         * @param numberOfChunkMatches - number of chunks that have the search string
         * @param atEnd - we are at last search item highlighted
         * @param atStart - we are at first search item highlighted
         */
        void onSearching(boolean doingSearch, int numberOfChunkMatches, boolean atEnd, boolean atStart);

        /**
         * enable/disable merge conflict indicator
         * @param showConflicted
         */
        void onEnableMergeConflict(boolean showConflicted, boolean active);

        /**
         * user has selected to update sources
         */
        void onUpdateSources();
    }
}
