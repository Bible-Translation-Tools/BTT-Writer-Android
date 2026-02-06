package com.door43.translationstudio.ui.translate;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Html;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.TextUtils;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.DragEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.door43.translationstudio.R;
import com.door43.translationstudio.core.FileHistory;
import com.door43.translationstudio.core.FrameTranslation;
import com.door43.translationstudio.core.MergeConflictsHandler;
import com.door43.translationstudio.core.RenderingProvider;
import com.door43.translationstudio.core.TranslationFormat;
import com.door43.translationstudio.core.Translator;
import com.door43.translationstudio.core.Typography;
import com.door43.translationstudio.databinding.FragmentFootnotePromptBinding;
import com.door43.translationstudio.databinding.FragmentReviewListItemBinding;
import com.door43.translationstudio.databinding.FragmentReviewListItemMergeConflictBinding;
import com.door43.translationstudio.databinding.FragmentVerseMarkerBinding;
import com.door43.translationstudio.rendering.ClickableRenderingEngine;
import com.door43.translationstudio.rendering.Clickables;
import com.door43.translationstudio.rendering.DefaultRenderer;
import com.door43.translationstudio.rendering.RenderingGroup;
import com.door43.translationstudio.ui.spannables.NoteSpan;
import com.door43.translationstudio.ui.spannables.Span;
import com.door43.translationstudio.ui.spannables.USFMNoteSpan;
import com.door43.translationstudio.ui.spannables.USFMVerseSpan;
import com.door43.translationstudio.ui.spannables.VerseSpan;
import com.door43.translationstudio.ui.translate.review.OnReviewModeListener;
import com.door43.translationstudio.ui.translate.review.ReviewHolder;
import com.door43.translationstudio.ui.translate.review.SearchSubject;
import com.door43.util.ColorUtil;
import com.door43.widget.ViewUtil;
import com.google.android.material.tabs.TabLayout;

import org.eclipse.jgit.revwalk.RevCommit;
import org.unfoldingword.resourcecontainer.Link;
import org.unfoldingword.tools.logger.Logger;
import org.unfoldingword.tools.taskmanager.ThreadableUI;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReviewModeAdapter extends ViewModeAdapter<ReviewHolder> implements OnReviewModeListener {
    private static final String TAG = ReviewModeAdapter.class.getSimpleName();

    public interface OnRenderHelpsListener {
        void onRenderHelps(ListItem item);
    }

    public interface OnShowToastListener {
        void onShowToast(String message);
        void onShowToast(int resId);
    }

    public static final int HIGHLIGHT_COLOR = Color.YELLOW;
    public static final int VIEW_TYPE_NORMAL = 0;
    public static final int VIEW_TYPE_CONFLICT = 1;

    private CharSequence searchText = null;
    private SearchSubject searchSubject = null;
    private boolean haveMergeConflict = false;
    private boolean mergeConflictFilterOn;
    private int chunkSearchMatchesCounter = 0;
    private int searchPosition = 0;
    private int searchSubPositionItems = 0;
    private boolean searchingTarget = true;
    private int numberOfChunkMatches = -1;
    private HashSet<Integer> visiblePositions = new HashSet<>();
    private boolean mergeConflictSummaryDisplayed = false;
    private boolean resourcesOpened;

    private OnRenderHelpsListener renderHelpsListener = null;
    private OnShowToastListener itemActionListener = null;
    private final RenderingProvider renderingProvider;

    public ReviewModeAdapter(
            boolean openResources,
            boolean enableMergeConflictsFilter,
            Typography typography,
            RenderingProvider renderingProvider
    ) {
        resourcesOpened = openResources;
        mergeConflictFilterOn = enableMergeConflictsFilter;
        this.typography = typography;
        this.renderingProvider = renderingProvider;
    }

    @Override
    protected void initializeListItems(
            List<ListItem> listItems,
            String startingChapter,
            String startingChunk
    ) {
        super.initializeListItems(listItems, startingChapter, startingChunk);

        setResourcesOpened(resourcesOpened);
        filter(searchText, searchSubject, searchPosition);
        triggerNotifyDataSetChanged();
        updateMergeConflict();
    }

    @Override
    public ReviewListItem createListItem(ListItem item) {
        return item.toType(ReviewListItem::new);
    }

    @Override
    public void onNoteClick(TranslationHelp note, int resourceCardWidth) {
        if (getListener() != null) {
            getListener().onTranslationNoteClick(note, resourceCardWidth);
        }
    }

    @Override
    public void onWordClick(String resourceContainerSlug, Link word, int resourceCardWidth) {
        if (getListener() != null) {
            getListener().onTranslationWordClick(
                    resourceContainerSlug,
                    word.chapter,
                    resourceCardWidth
            );
        }
    }

    @Override
    public void onQuestionClick(TranslationHelp question, int resourceCardWidth) {
        if (getListener() != null) {
            getListener().onTranslationQuestionClick(question, resourceCardWidth);
        }
    }

    @Override
    public void onResourceTabNotesSelected(ReviewHolder holder) {
        int position = holder.getBindingAdapterPosition();
        ReviewListItem item = (ReviewListItem) filteredItems.get(position);
        holder.showNotes(item.source.language);
    }

    @Override
    public void onResourceTabWordsSelected(ReviewHolder holder) {
        int position = holder.getBindingAdapterPosition();
        ReviewListItem item = (ReviewListItem) filteredItems.get(position);
        holder.showWords(item.source.language);
    }

    @Override
    public void onResourceTabQuestionsSelected(ReviewHolder holder) {
        int position = holder.getBindingAdapterPosition();
        ReviewListItem item = (ReviewListItem) filteredItems.get(position);
        holder.showQuestions(item.source.language);
    }

    @Override
    public void onSourceTranslationTabClick(String sourceTranslationId) {
        if (getListener() != null) {
            getListener().onSourceTranslationTabClick(sourceTranslationId);
        }
    }

    @Override
    public void onNewSourceTranslationTabClick() {
        if (getListener() != null) {
            getListener().onNewSourceTranslationTabClick();
        }
    }

    @Override
    public void onTapResourceCard() {
        //if (!mResourcesOpened) openResources();
    }

    /**
     * check all cards for merge conflicts to see if we should show warning.
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
        if (position >= 0 && position < filteredItems.size()) {
            return filteredItems.get(position).chapterSlug;
        }
        return null;
    }

    @Override
    public int getItemPosition(String chapterSlug, String chunkSlug) {
        ListItem item = getItem(chapterSlug, chunkSlug);
        return filteredItems.indexOf(item);
    }

    @Override
    public ListItem getItem(String chapterSlug, String chunkSlug) {
        for (ListItem item : filteredItems) {
            if (chapterSlug.equals(item.chapterSlug) && chunkSlug.equals(item.chunkSlug)) {
                return item;
            }
        }
        return null;
    }

    @Override
    public void setResourcesOpened(boolean status) {
        resourcesOpened = status;
        for (ListItem item : items) {
            ((ReviewListItem) item).resourcesOpened = status;
        }
        triggerNotifyDataSetChanged();
    }

    @Override
    public ReviewListItem getItem(int position) {
        return (ReviewListItem) super.getItem(position);
    }

    @Override
    public int getItemViewType(int position) {
        ListItem item = getItem(position);
        if (item != null) {
            boolean conflicted = item.getHasMergeConflicts();
            if (conflicted) {
                showMergeConflictIcon(true, mergeConflictFilterOn);
                return VIEW_TYPE_CONFLICT;
            }
        }
        return VIEW_TYPE_NORMAL;
    }

    @Override
    public ReviewHolder onCreateManagedViewHolder(ViewGroup parent, int viewType) {
        IReviewListItemBinding binding;
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_CONFLICT) {
            binding = new ReviewListItemMergeConflictBinding(FragmentReviewListItemMergeConflictBinding.inflate(inflater, parent, false));
        } else {
            binding = new ReviewListItemBinding(FragmentReviewListItemBinding.inflate(inflater, parent, false));
        }
        return new ReviewHolder(binding, typography, this);
    }

    /**
     * Perform task garbage collection
     *
     * @param range the position that left the screen
     */
    @Override
    protected void onVisiblePositionsChanged(int[] range) {
        // constrain the upper bound
        if (range[1] >= filteredItems.size()) range[1] = filteredItems.size() - 1;
        if (range[0] >= filteredItems.size()) range[0] = filteredItems.size() - 1;

        HashSet<Integer> visible = new HashSet<>();
        // record visible positions;
        for (int i = range[0]; i < range[1]; i++) {
            visible.add(i);
        }
        // notify not-visible
        this.visiblePositions.removeAll(visible);
        for (Integer i : this.visiblePositions) {
            // TODO Check if there is a need to cancel render tasks for hidden items
            // runTaskGarbageCollection(i);
        }

        this.visiblePositions = visible;
    }

    @Override
    public void markAllChunksDone() {
        new AlertDialog.Builder(context, R.style.AppTheme_Dialog)
                .setTitle(R.string.project_checklist_title)
                .setMessage(Html.fromHtml(context.getString(R.string.project_checklist_body)))
                .setPositiveButton(R.string.confirm, (dialog, which) -> {
                    int marked = 0;
                    int total = filteredItems.size();
                    for (ListItem item : filteredItems) {
                        try {
                            markChunkCompleted(item, item.target.getFormat());
                            marked++;
                        } catch (Exception e) {
                            String msg = String.format(
                                    "There was an error in markAllChunksDone. Translation: " +
                                            "%s, chapter: %s, chunk: %s. Error: %s",
                                    item.target.getId(),
                                    item.chapterSlug,
                                    item.chunkSlug,
                                    e.getMessage());
                            Logger.e(TAG, msg);
                        }
                    }

                    try {
                        filteredItems.get(0).target.commit();

                        new AlertDialog.Builder(context, R.style.AppTheme_Dialog)
                                .setTitle(R.string.result)
                                .setMessage(String.format(context.getString(R.string.mark_chunks_done_result), marked, total))
                                .setPositiveButton(R.string.label_ok, null)
                                .show();

                    } catch (Exception e) {
                        Logger.e(TAG,
                                "Failed to commit translation of " + filteredItems.get(0).target.getId(), e);
                    }

                    triggerNotifyDataSetChanged();
                })
                .setNegativeButton(R.string.title_cancel, null)
                .show();
    }

    @Override
    public void onBindManagedViewHolder(final ReviewHolder holder, final int position) {
        final ReviewListItem item = (ReviewListItem) filteredItems.get(position);
        holder.bind(item);
    }

    @Override
    public void onEditorToggle(ReviewHolder holder) {
        Handler handler = new Handler(Looper.getMainLooper());

        final int position = holder.getBindingAdapterPosition();
        ReviewListItem item = (ReviewListItem) filteredItems.get(position);
        item.isEditing = !item.isEditing;

        EditText view;
        if (item.isEditing) {
            view = holder.binding.getTargetEditableBody();
            if (view != null) {
                item.renderedTargetText = this.renderTargetText(holder, item, true);
                view.setText(item.renderedTargetText);

                handler.post(() -> {
                    getListener().showKeyboard(view);
                    view.requestFocus();
                });
            }
        } else {
            view = holder.binding.getTargetBody();
            if (view != null) {
                // re-render for verse mode
                item.renderedTargetText = renderTargetText(holder, item);
                view.setText(item.renderedTargetText);

                handler.post(() -> {
                    getListener().closeKeyboard();
                    view.requestFocus();
                });
            }
        }

        addMissingVerses(position);
        holder.rebuildControls(item);
    }

    @Override
    public void onApplyChangedText(CharSequence s, ReviewHolder holder) {
        int position = holder.getBindingAdapterPosition();
        ReviewListItem item = (ReviewListItem) filteredItems.get(position);
        applyChangedText(s, item);

        // commit immediately if editing history
        FileHistory history = item.getFileHistory();
        if (history != null && !history.isAtHead()) {
            history.reset();
            holder.rebuildControls(item);
        }
    }

    @Override
    public void onUndoTextInTarget(ReviewHolder holder) {
        int position = holder.getBindingAdapterPosition();
        ReviewListItem item = (ReviewListItem) filteredItems.get(position);
        undoTextInTarget(holder, item);
    }

    @Override
    public void onRedoTextInTarget(ReviewHolder holder) {
        int position = holder.getBindingAdapterPosition();
        ReviewListItem item = (ReviewListItem) filteredItems.get(position);
        redoTextInTarget(holder, item);
    }

    @Override
    public void onDoneSwitchClicked(ReviewHolder holder, boolean checked) {
        int position = holder.getBindingAdapterPosition();
        ReviewListItem item = (ReviewListItem) filteredItems.get(position);
        if (checked) {
            if (item.isEditing && holder.binding.getTargetEditableBody() != null) {
                // make sure to capture verse marker changes before dialog is displayed
                Editable changes = holder.binding.getTargetEditableBody().getText();
                item.renderedTargetText = changes;
                if (changes != null) {
                    item.setTargetText(Translator.compileTranslation(changes));
                }
            }

            new AlertDialog.Builder(context, R.style.AppTheme_Dialog)
                    .setTitle(R.string.chunk_checklist_title)
                    .setMessage(Html.fromHtml(context.getString(R.string.chunk_checklist_body)))
                    .setPositiveButton(R.string.confirm, (dialog, which) -> {
                        try {
                            markChunkCompleted(item, item.target.getFormat());
                            item.target.commit();
                        } catch (Exception e) {
                            Logger.e(TAG, "Failed to commit translation of " + item.target.getId(), e);
                            itemActionListener.onShowToast(e.getMessage());
                        }
                        triggerNotifyDataSetChanged();
                    })
                    .setNegativeButton(R.string.title_cancel, (dialog, which) -> {
                        // off if not accepted
                        if (holder.binding.getDoneSwitch() != null) {
                            holder.binding.getDoneSwitch().setChecked(false); // force back
                        }
                    })
                    .show();
        } else {
            reOpenItem(item);
        }
    }

    @Override
    public void onCreateFootnoteAtSelection(ReviewHolder holder) {
        int position = holder.getBindingAdapterPosition();
        ReviewListItem item = (ReviewListItem) filteredItems.get(position);
        createFootnoteAtSelection(holder, item);
    }

    /**
     * Generate spannable for source text. Will add click listener for notes if supported
     *
     * @param item review list item
     * @return rendered text
     */
    @Override
    public CharSequence onRenderSourceText(ReviewListItem item) {
        RenderingGroup renderingGroup = new RenderingGroup();
        boolean enableSearch = searchText != null && searchSubject == SearchSubject.SOURCE;

        if (Clickables.isClickableFormat(item.getSourceTranslationFormat())) {
            Span.OnClickListener noteClickListener = new Span.OnClickListener() {
                @Override
                public void onClick(View view, Span span, int start, int end) {
                    if (span instanceof NoteSpan) {
                        onSourceFootnoteClick(item, (NoteSpan) span, start, end);
                    }
                }

                @Override
                public void onLongClick(View view, Span span, int start, int end) {
                }
            };
            renderingProvider.setupRenderingGroup(
                    item.getSourceTranslationFormat(),
                    renderingGroup,
                    null,
                    noteClickListener,
                    false
            );
        } else {
            renderingGroup.addEngine(new DefaultRenderer(null));
        }

        if (enableSearch) {
            renderingGroup.setSearchString(searchText, HIGHLIGHT_COLOR);
        }

        renderingGroup.init(item.getSourceText());
        CharSequence results = renderingGroup.start();
        item.hasMissingVerses = renderingGroup.isAddedMissingVerse();
        return results;
    }

    @Override
    public void onSearchItemUpdated(int position, TextView view, boolean isTarget) {
        ReviewListItem item = (ReviewListItem) filteredItems.get(position);
        int selectPosition = checkForSelectedSearchItem(item, position, isTarget);

        if (isTarget) {
            item.refreshSearchHighlightTarget = false;
            selectCurrentSearchItem(position, selectPosition, view);
        } else {
            item.refreshSearchHighlightSource = false;
            selectCurrentSearchItem(position, selectPosition, view);
        }
    }

    @Override
    public void onMergeConflictItemCancel(int position) {
        ReviewListItem item = (ReviewListItem) filteredItems.get(position);
        item.mergeItemSelected = -1;
        notifyItemChanged(position);
    }

    @Override
    public void onMergeConflictItemConfirm(int position) {
        ReviewListItem item = (ReviewListItem) filteredItems.get(position);
        if (item.mergeItemSelected >= 0 && item.mergeItemSelected < item.mergeItems.size()) {
            CharSequence selectedText = item.mergeItems.get(item.mergeItemSelected);
            applyNewCompiledText(selectedText.toString(), item);
            item.setTargetText(selectedText.toString());
            reOpenItem(item);
            item.setHasMergeConflicts(MergeConflictsHandler.isMergeConflicted(selectedText));
            item.mergeItemSelected = -1;
            item.isEditing = false;

            // if in merge conflict mode and merge
            // conflicts resolved, remove item
            if (!item.getHasMergeConflicts() && mergeConflictFilterOn) {
                filteredItems.remove(item);
            }
            notifyItemChanged(position);
            updateMergeConflict();
        }
    }

    @Override
    public CharSequence onRenderTargetText(ReviewHolder holder, ReviewListItem item, boolean editable) {
        return renderTargetText(holder, item, editable);
    }

    @Override
    public CharSequence onRenderTargetText(ReviewHolder holder, ReviewListItem item) {
        return renderTargetText(holder, item);
    }

    @Override
    public void onAddMissingVerses(int position) {
        addMissingVerses(position);
    }

    @Override
    public void onRenderHelps(ReviewListItem item) {
        if (item.resourcesOpened && renderHelpsListener != null) {
            renderHelpsListener.onRenderHelps(item);
        }
    }

    /**
     * if missing verses were found during render, then add them
     *
     * @param position list item position
     */
    private void addMissingVerses(int position) {
        ReviewListItem item = (ReviewListItem) filteredItems.get(position);
        if (item.hasMissingVerses && !item.isComplete()) {
            Log.i(TAG, "Adding Missing verses to: " + item.getTargetText());
            if (!item.getTargetText().isEmpty()) {
                String translation = applyChangedText(item.renderedTargetText, item);
                Log.i(TAG, "Added Missing verses: " + translation);
                item.hasMissingVerses = false;
                item.renderedTargetText = null; // force re-rendering of target text
                notifyItemChanged(position);
            }
        }
    }

    /**
     * check if we have a selected search item in this chunk,
     * returns position if found, -1 if not found
     *
     * @param item review list item
     * @param position list item position
     * @param target true if searching target text
     * @return list item position if found, -1 if not found
     */
    private int checkForSelectedSearchItem(ReviewListItem item, int position, boolean target) {
        int selectPosition = -1;
        if (item.hasSearchText && (position == searchPosition)) {
            if (searchSubPositionItems < 0) { // if we haven't counted items yet
                findSearchItemInChunkAndPreselect(item, target);
                Log.i(TAG,
                        "Re-rendering, Found search items in chunk " + position + ": " + searchSubPositionItems);
            } else if (searchSubPositionItems > 0) { // if we have counted items then find the
                // number selected
                int searchSubPosition = 0;
                MatchResults results = getMatchItemN(item, searchText, searchSubPosition, target);
                if (results.foundLocation >= 0) {
                    Log.i(TAG,
                            "Highlight at position: " + position + " : " + results.foundLocation);
                    selectPosition = results.foundLocation;
                } else {
                    Log.i(TAG, "Highlight failed for position: " + position + "; chunk position: "
                            + searchSubPosition + "; chunk count: " + searchSubPositionItems);
                }
                checkIfAtSearchLimits();
            }
        }
        return selectPosition;
    }

    /**
     * highlight the current selected search text item at position
     *
     * @param position       - list item position
     * @param selectPosition - search item position
     * @param view           - search item view
     */
    private void selectCurrentSearchItem(final int position, int selectPosition, TextView view) {
        if (selectPosition >= 0) {

            Layout layout = view.getLayout();
            if (layout != null) {
                int lineNumberForLocation = layout.getLineForOffset(selectPosition);
                int baseline = layout.getLineBaseline(lineNumberForLocation);
                int ascent = layout.getLineAscent(lineNumberForLocation);

                final int verticalOffset = baseline + ascent;
                Log.i(TAG,
                        "set position for " + selectPosition + ", scroll to y=" + verticalOffset);

                Handler hand = new Handler(Looper.getMainLooper());
                hand.post(() -> {
                    Log.i(TAG,
                            "selectCurrentSearchItem position= " + position + ", offset=" + (-verticalOffset));
                    onSetSelectedPosition(position, -verticalOffset);
                });
            } else {
                Logger.e(TAG, "cannot get layout for position: " + position);
            }
        }
    }

    /**
     * mark item as not done
     *
     * @param item review list item
     */
    private void reOpenItem(ListItem item) {
        boolean opened;
        if (item.isChapterReference()) {
            opened = item.target.reopenChapterReference(item.chapterSlug);
        } else if (item.isChapterTitle()) {
            opened = item.target.reopenChapterTitle(item.chapterSlug);
        } else if (item.isProjectTitle()) {
            opened = item.target.openProjectTitle();
        } else {
            opened = item.target.reopenFrame(item.chapterSlug, item.chunkSlug);
        }
        if (opened) {
            item.renderedTargetText = null;
            item.setComplete(false);
            triggerNotifyItemChanged(filteredItems.indexOf(item));
        }
    }

    /**
     * create a new footnote at selected position in target text.  Displays an edit dialog to
     * enter footnote data.
     *
     * @param holder review holder
     * @param item review list item
     */
    private void createFootnoteAtSelection(final ReviewHolder holder, final ReviewListItem item) {
        final EditText editText = holder.getEditText(item.isEditing);
        int endPos = editText.getSelectionEnd();
        if (endPos < 0) {
            endPos = 0;
        }
        final int insertPos = endPos;
        editFootnote("", holder, item, insertPos, insertPos);
    }

    /**
     * edit contents of footnote at specified position
     *
     * @param initialNote initial note
     * @param holder      review holder
     * @param item        review list item
     * @param footnotePos position of footnote
     * @param footnoteEndPos end position of footnote
     */
    private void editFootnote(
            CharSequence initialNote,
            final ReviewHolder holder,
            final ReviewListItem item,
            final int footnotePos,
            final int footnoteEndPos
    ) {
        final EditText editText = holder.getEditText(item.isEditing);
        final CharSequence original = editText.getText();

        LayoutInflater inflater = LayoutInflater.from(context);
        FragmentFootnotePromptBinding footnoteBinding = FragmentFootnotePromptBinding.inflate(inflater);

        footnoteBinding.footnoteText.setText(initialNote);
        // pop up note prompt
        new AlertDialog.Builder(context, R.style.AppTheme_Dialog)
                .setTitle(R.string.title_add_footnote)
                .setPositiveButton(R.string.label_ok, (dialog, which) -> {
                    CharSequence footnote = footnoteBinding.footnoteText.getText();
                    boolean validated = verifyAndReplaceFootnote(
                            footnote,
                            original,
                            footnotePos,
                            footnoteEndPos,
                            holder,
                            item,
                            editText
                    );
                    if (validated) {
                        dialog.dismiss();
                    }
                })
                .setNegativeButton(R.string.title_cancel, (dialog, which) -> dialog.dismiss())
                .setView(footnoteBinding.getRoot())
                .show();
    }

    /**
     * insert footnote into EditText or remove footnote from EditText if both footnote and
     * footnoteTitleText are null
     *
     * @param footnote    footnote text
     * @param original    original text
     * @param insertPos   insert position
     * @param insertEndPos insert end position
     * @param holder      review holder
     * @param item        review list item
     * @param editText    edit text
     */
    private boolean verifyAndReplaceFootnote(
            CharSequence footnote,
            CharSequence original,
            int insertPos,
            final int insertEndPos,
            final ReviewHolder holder,
            final ReviewListItem item,
            EditText editText
    ) {
        // sanity checks
        if ((null == footnote) || (footnote.length() <= 0)) {
            warnDialog(R.string.title_footnote_invalid, R.string.footnote_message_empty);
            return false;
        }

        placeFootnote(footnote, original, insertPos, insertEndPos, holder, item, editText);
        return true;
    }

    /**
     * display warning dialog
     *
     * @param titleID    title
     * @param messageID message
     */
    private void warnDialog(int titleID, int messageID) {
        new AlertDialog.Builder(context, R.style.AppTheme_Dialog)
                .setTitle(titleID)
                .setMessage(messageID)
                .setPositiveButton(R.string.dismiss, null)
                .show();
    }

    /**
     * insert footnote into EditText or remove footnote from EditText if both footnote and
     * footnoteTitleText are null
     *
     * @param footnote footnote text
     * @param original original text
     * @param start    start position
     * @param end      end position
     * @param holder   review holder
     * @param item     review list item
     * @param editText edit text
     */
    private void placeFootnote(
            CharSequence footnote,
            CharSequence original,
            int start,
            final int end,
            final ReviewHolder holder,
            final ReviewListItem item,
            EditText editText
    ) {
        CharSequence footnotecode = "";
        if (footnote != null) {
            // sanity checks
            if (footnote.length() <= 0) {
                footnote = context.getResources().getString(R.string.footnote_label);
            }

            USFMNoteSpan footnoteSpannable = USFMNoteSpan.generateFootnote(footnote);
            footnotecode = footnoteSpannable.getMachineReadable();
        }

        CharSequence newText = TextUtils.concat(
                original.subSequence(0, start),
                footnotecode,
                original.subSequence(end, original.length())
        );
        editText.setText(newText);

        item.renderedTargetText = newText;
        item.setTargetText(Translator.compileTranslation(editText.getText())); // get XML for footnote
        item.target.applyFrameTranslation(item.getFt(), item.getTargetText()); // save change

        // generate spannable again adding
        if (item.isComplete() || item.isEditing) {
            item.renderedTargetText = this.renderTargetText(holder, item, true);
        } else {
            item.renderedTargetText = renderTargetText(
                    item.getTargetText(),
                    item.getTargetTranslationFormat(),
                    item.getFt(),
                    holder,
                    item
            );
        }
        editText.setText(item.renderedTargetText);
        editText.setSelection(editText.length(), editText.length());
    }

    /**
     * save changed text to item,  first see if it needs to be compiled
     *
     * @param s    A string or editable
     * @param item Review list item
     *
     * @return compiled text
     */
    private String applyChangedText(CharSequence s, ReviewListItem item) {
        String translation;
        if (s == null) {
            return null;
        } else if (s instanceof Editable) {
            translation = Translator.compileTranslation((Editable) s);
        } else if (s instanceof SpannedString) {
            translation = Translator.compileTranslationSpanned((SpannedString) s);
        } else {
            translation = s.toString();
        }

        applyNewCompiledText(translation, item);
        return translation;
    }

    /**
     * save new text to item
     *
     * @param translation translation
     * @param item        review list item
     */
    private void applyNewCompiledText(String translation, ListItem item) {
        translation = translation.replaceAll("\\s*\\R\\s*", "\n");

        item.setTargetText(translation);
        if (item.isChapterReference()) {
            item.target.applyChapterReferenceTranslation(item.getCt(), translation);
        } else if (item.isChapterTitle()) {
            item.target.applyChapterTitleTranslation(item.getCt(), translation);
        } else if (item.isProjectTitle()) {
            try {
                item.target.applyProjectTitleTranslation(translation);
            } catch (IOException e) {
                Logger.e(ReviewModeAdapter.class.getName(), "Failed to save the project title " +
                        "translation", e);
            }
        } else if (item.isChunk()) {
            item.target.applyFrameTranslation(item.getFt(), translation);
        }
    }

    /**
     * restore the text from previous commit for fragment
     *
     * @param holder Review holder
     * @param item Review list item
     */
    private void undoTextInTarget(final ReviewHolder holder, final ReviewListItem item) {
        if (holder.binding.getUndoButton() != null)
            holder.binding.getUndoButton().setVisibility(View.INVISIBLE);
        if (holder.binding.getRedoButton() != null)
            holder.binding.getRedoButton().setVisibility(View.INVISIBLE);

        final FileHistory history = item.getFileHistory();
        ThreadableUI thread = new ThreadableUI(context) {
            RevCommit commit = null;

            @Override
            public void onStop() {
            }

            @Override
            public void run() {
                // commit changes before viewing history
                if (history != null) {
                    if (history.isAtHead()) {
                        if (!item.target.isClean()) {
                            try {
                                item.target.commitSync();
                                history.loadCommits();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    // get previous
                    commit = history.previous();
                }
            }

            @Override
            public void onPostExecute() {
                if (history != null) {
                    if (commit != null) {
                        String text = null;
                        try {
                            text = history.read(commit);
                        } catch (IllegalStateException e) {
                            Logger.w(TAG, "Undo is past end of history for specific file", e);
                            text = ""; // graceful recovery
                        } catch (Exception e) {
                            Logger.w(TAG, "Undo Read Exception", e);
                        }

                        // save and update ui
                        if (text != null) {
                            // TRICKY: prevent history from getting rolled back soon after the user
                            // views it
                            restartAutoCommitTimer();
                            applyChangedText(text, item);

                            if (getListener() != null) getListener().closeKeyboard();
                            item.setHasMergeConflicts(MergeConflictsHandler.isMergeConflicted(text));
                            triggerNotifyDataSetChanged();
                            updateMergeConflict();

                            if (holder.binding.getTargetEditableBody() != null) {
                                holder.removeTextChangeListener();
                                holder.binding.getTargetEditableBody().setText(item.renderedTargetText);
                                holder.attachTextChangeListener();
                            }
                        }
                    }

                    if (holder.binding.getRedoButton() != null && holder.binding.getUndoButton() != null) {
                        if (history.hasNext()) {
                            holder.binding.getRedoButton().setVisibility(View.VISIBLE);
                        } else {
                            holder.binding.getRedoButton().setVisibility(View.GONE);
                        }
                        if (history.hasPrevious()) {
                            holder.binding.getUndoButton().setVisibility(View.VISIBLE);
                        } else {
                            holder.binding.getUndoButton().setVisibility(View.GONE);
                        }
                    }
                }
            }
        };
        thread.start();
    }

    /**
     * restore the text from later commit for fragment
     *
     * @param holder Review holder
     * @param item Review list item
     */
    private void redoTextInTarget(final ReviewHolder holder, final ReviewListItem item) {
        if (holder.binding.getUndoButton() != null)
            holder.binding.getUndoButton().setVisibility(View.INVISIBLE);
        if (holder.binding.getRedoButton() != null)
            holder.binding.getRedoButton().setVisibility(View.INVISIBLE);

        final FileHistory history = item.getFileHistory();
        ThreadableUI thread = new ThreadableUI(context) {
            RevCommit commit = null;

            @Override
            public void onStop() {

            }

            @Override
            public void run() {
                if (history != null) {
                    commit = history.next();
                }
            }

            @Override
            public void onPostExecute() {
                if (history != null) {
                    if (commit != null) {
                        String text = null;
                        try {
                            text = history.read(commit);
                        } catch (IllegalStateException e) {
                            Logger.w(TAG, "Redo is past end of history for specific file", e);
                            text = ""; // graceful recovery
                        } catch (Exception e) {
                            Logger.w(TAG, "Redo Read Exception", e);
                        }

                        // save and update ui
                        if (text != null) {
                            // TRICKY: prevent history from getting rolled back soon after the user
                            // views it
                            restartAutoCommitTimer();
                            applyChangedText(text, item);

                            if (getListener() != null) getListener().closeKeyboard();
                            item.setHasMergeConflicts(MergeConflictsHandler.isMergeConflicted(text));
                            triggerNotifyDataSetChanged();
                            updateMergeConflict();

                            if (holder.binding.getTargetEditableBody() != null) {
                                holder.removeTextChangeListener();
                                holder.binding.getTargetEditableBody().setText(item.renderedTargetText);
                                holder.attachTextChangeListener();
                            }
                        }
                    }

                    if (holder.binding.getRedoButton() != null && holder.binding.getUndoButton() != null) {
                        if (history.hasNext()) {
                            holder.binding.getRedoButton().setVisibility(View.VISIBLE);
                        } else {
                            holder.binding.getRedoButton().setVisibility(View.GONE);
                        }
                        if (history.hasPrevious()) {
                            holder.binding.getUndoButton().setVisibility(View.VISIBLE);
                        } else {
                            holder.binding.getUndoButton().setVisibility(View.GONE);
                        }
                    }
                }
            }
        };
        thread.start();
    }

    private static final Pattern USFM_CONSECUTIVE_VERSE_MARKERS =
            Pattern.compile("\\\\v\\s(\\d+(-\\d+)?)\\s*\\\\v\\s(\\d+(-\\d+)?)");

    private static final Pattern USFM_VERSE_MARKER =
            Pattern.compile(USFMVerseSpan.PATTERN);

    private static final Pattern CONSECUTIVE_VERSE_MARKERS =
            Pattern.compile("(<verse [^>]+/>\\s*){2}");

    private static final Pattern VERSE_MARKER =
            Pattern.compile("<verse\\s+number=\"(\\d+)\"[^>]*>");

    /**
     * Performs some validation, and commits changes if ready.
     *
     * @throws IllegalStateException If there is an error with the chunk
     */
    private void markChunkCompleted(final ListItem item, TranslationFormat format) throws IllegalStateException {
        // Check for empty translation.
        if (item.getTargetText().isEmpty()) {
            throw new IllegalStateException(context.getString(R.string.translate_first));
        }

        Matcher matcher;
        int lowVerse = -1;
        int highVerse = 999999999;
        int[] range = RenderingProvider.Companion.getVerseRange(item.getTargetText(), item.getTargetTranslationFormat());
        if (range.length > 0) {
            lowVerse = range[0];
            highVerse = lowVerse;
            if (range.length > 1) {
                highVerse = range[1];
            }
        }

        // Check for contiguous verse numbers.
        if (format == TranslationFormat.USFM) {
            matcher = USFM_CONSECUTIVE_VERSE_MARKERS.matcher(item.getTargetText());
        } else {
            matcher = CONSECUTIVE_VERSE_MARKERS.matcher(item.getTargetText());
        }
        if (matcher.find()) {
            throw new IllegalStateException(context.getString(R.string.consecutive_verse_markers));
        }

        // check for invalid verse markers
        int error = 0;
        if (format == TranslationFormat.USFM) {
            matcher = USFM_VERSE_MARKER.matcher(item.getTargetText());
        } else {
            matcher = VERSE_MARKER.matcher(item.getTargetText());
        }
        int[] sourceVerseRange = RenderingProvider.Companion.getVerseRange(item.getSourceText(), item.getSourceTranslationFormat());
        if (sourceVerseRange.length > 0) {
            int min = sourceVerseRange[0];
            int max = min;
            if (sourceVerseRange.length == 2) max = sourceVerseRange[1];
            while (matcher.find()) {
                int verse = Integer.parseInt(matcher.group(1));
                if (verse < min || verse > max) {
                    error = R.string.outofrange_verse_marker;
                    break;
                }
            }
        }
        if (error > 0) {
            throw new IllegalStateException(context.getString(error));
        }

        // Check for out-of-order verse markers.
        if (format == TranslationFormat.USFM) {
            matcher = USFM_VERSE_MARKER.matcher(item.getTargetText());
        } else {
            matcher = VERSE_MARKER.matcher(item.getTargetText());
        }
        int lastVerseSeen = 0;
        while (matcher.find()) {
            int currentVerse = Integer.parseInt(matcher.group(1));
            if (currentVerse <= lastVerseSeen) {
                if (currentVerse == lastVerseSeen) {
                    error = R.string.duplicate_verse_marker;
                } else {
                    error = R.string.outoforder_verse_markers;
                }
                break;
            } else if ((currentVerse < lowVerse) || (currentVerse > highVerse)) {
                error = R.string.outofrange_verse_marker;
                break;
            } else {
                lastVerseSeen = currentVerse;
            }
        }
        if (error > 0) {
            throw new IllegalStateException(context.getString(error));
        }

        // Everything looks good so far.
        boolean success;
        if (item.isChapterReference()) {
            success = item.target.finishChapterReference(item.chapterSlug);
        } else if (item.isChapterTitle()) {
            success = item.target.finishChapterTitle(item.chapterSlug);
        } else if (item.isProjectTitle()) {
            success = item.target.closeProjectTitle();
        } else {
            success = item.target.finishFrame(item.chapterSlug, item.chunkSlug);
        }

        if (!success) {
            // TODO: Use a more accurate (if potentially more opaque) error message.
            throw new IllegalStateException(context.getString(R.string.failed_to_commit_chunk));
        } else {
            item.setComplete(true);
        }

        item.isEditing = false;
        item.renderedTargetText = null;
    }

    private CharSequence renderTargetText(final ReviewHolder holder, final ReviewListItem item) {
        return renderTargetText(
                item.getTargetText(),
                item.getTargetTranslationFormat(),
                item.getFt(),
                holder,
                item
        );
    }

    /**
     * generate spannable for target text.  Will add click listener for notes and verses if they
     * are supported
     *
     * @param text text
     * @param format translation format
     * @param frameTranslation frame translation
     * @param holder review holder
     * @param item review list item
     * @return rendered target text
     */
    private CharSequence renderTargetText(
            String text,
            TranslationFormat format,
            final FrameTranslation frameTranslation,
            final ReviewHolder holder,
            final ReviewListItem item
    ) {
        RenderingGroup renderingGroup = new RenderingGroup();
        boolean enableSearch = searchText != null &&
                searchSubject != null &&
                searchSubject == SearchSubject.TARGET;

        if (Clickables.isClickableFormat(format)) {
            Span.OnClickListener verseClickListener = new Span.OnClickListener() {
                @Override
                public void onClick(View view, Span span, int start, int end) {
                    itemActionListener.onShowToast(R.string.long_click_to_drag);
                }

                @SuppressLint("SetTextI18n")
                @Override
                public void onLongClick(final View view, Span span, int start, int end) {
                    toggleDisableItems(true, item);

                    ClipData dragData = ClipData.newPlainText(
                            item.chapterSlug + "-" + item.chunkSlug,
                            span.getMachineReadable()
                    );
                    final VerseSpan pin = ((VerseSpan) span);

                    // create drag shadow
                    LayoutInflater inflater = (LayoutInflater) context.getSystemService(
                            Context.LAYOUT_INFLATER_SERVICE
                    );
                    FragmentVerseMarkerBinding markerBinding = FragmentVerseMarkerBinding.inflate(inflater);

                    if (pin.getEndVerseNumber() > 0) {
                        markerBinding.verse.setText(pin.getStartVerseNumber() + "-" + pin.getEndVerseNumber());
                    } else {
                        markerBinding.verse.setText(pin.getStartVerseNumber() + "");
                    }
                    Bitmap shadow = ViewUtil.convertToBitmap(markerBinding.getRoot());
                    View.DragShadowBuilder myShadow = CustomDragShadowBuilder.fromBitmap(context, shadow);

                    int[] spanRange = {start, end};
                    view.startDrag(dragData,  // the data to be dragged
                            myShadow,  // the drag shadow builder
                            spanRange,      // no need to use local data
                            0          // flags (not currently used, set to 0)
                    );
                    view.setOnDragListener(new View.OnDragListener() {
                        private boolean hasEntered = false;

                        @Override
                        public boolean onDrag(View v, DragEvent e) {
                            EditText editText = ((EditText) v);
                            if (e.getAction() == DragEvent.ACTION_DRAG_STARTED) {
                                // delete old span
                                if (e.getLocalState() instanceof int[] spanRange && spanRange.length >= 2) {
                                    CharSequence in = editText.getText();
                                    if (spanRange[0] < in.length() && spanRange[1] < in.length()) {
                                        CharSequence out = TextUtils.concat(
                                                in.subSequence(0, spanRange[0]),
                                                in.subSequence(spanRange[1], in.length())
                                        );
                                        editText.setText(out);
                                    }
                                }
                            } else if (e.getAction() == DragEvent.ACTION_DROP) {
                                int offset = editText.getOffsetForPosition(e.getX(), e.getY());
                                CharSequence text = editText.getText();
                                offset = closestSpotForVerseMarker(offset, text);

                                if (offset >= 0) {
                                    // insert the verse at the offset
                                    text = TextUtils.concat(
                                            text.subSequence(0, offset),
                                            pin.toCharSequence(context),
                                            text.subSequence(offset, text.length())
                                    );
                                } else {
                                    // place the verse back at the beginning
                                    text = TextUtils.concat(pin.toCharSequence(context), text);
                                }

                                SpannableString noHighlightText = resetHighlightColor(text);
                                editText.setText(noHighlightText);

                                String translation = Translator.compileTranslation(editText.getText());
                                item.target.applyFrameTranslation(frameTranslation, translation);
                                item.setTargetText(translation);
                                item.renderedTargetText = renderTargetText(
                                        translation,
                                        item.getTargetTranslationFormat(),
                                        frameTranslation,
                                        holder,
                                        item
                                );
                            } else if (e.getAction() == DragEvent.ACTION_DRAG_ENDED) {
                                toggleDisableItems(false, null);
                                v.setOnDragListener(null);
                                editText.setSelection(editText.getSelectionEnd());
                                // reset verse if dragged off the view
                                // TODO: 10/5/2015 perhaps we should confirm with the user?
                                if (!hasEntered) {
                                    // place the verse back at the beginning
                                    CharSequence text = editText.getText();
                                    text = TextUtils.concat(pin.toCharSequence(context), text);
                                    editText.setText(text);
                                    String translation = Translator.compileTranslation(editText.getText());
                                    item.target.applyFrameTranslation(frameTranslation, translation);
                                    item.renderedTargetText = renderTargetText(
                                            translation,
                                            item.getTargetTranslationFormat(),
                                            frameTranslation,
                                            holder,
                                            item
                                    );
                                }
                            } else if (e.getAction() == DragEvent.ACTION_DRAG_ENTERED) {
                                hasEntered = true;
                            } else if (e.getAction() == DragEvent.ACTION_DRAG_EXITED) {
                                hasEntered = false;
                                editText.setSelection(editText.getSelectionEnd());
                                SpannableString noHighlightText = resetHighlightColor(editText.getText());
                                editText.setText(noHighlightText);
                            } else if (e.getAction() == DragEvent.ACTION_DRAG_LOCATION) {
                                int offset = editText.getOffsetForPosition(e.getX(), e.getY());
                                if (offset >= 0 && offset < editText.getText().length() - 1) {
                                    CharSequence txt = editText.getText();
                                    SpannableString str = highlightWordAt(offset, txt);
                                    editText.setText(str);
                                } else {
                                    editText.setSelection(editText.getSelectionEnd());
                                }
                            }
                            return true;
                        }
                    });
                }
            };

            Span.OnClickListener noteClickListener = new Span.OnClickListener() {
                @Override
                public void onClick(View view, Span span, int start, int end) {
                    if (span instanceof NoteSpan) {
                        showFootnote(holder, item, (NoteSpan) span, start, end, true);
                    }
                }

                @Override
                public void onLongClick(View view, Span span, int start, int end) {
                }
            };

            ClickableRenderingEngine renderer = renderingProvider.setupRenderingGroup(
                    format,
                    renderingGroup,
                    verseClickListener,
                    noteClickListener,
                    true
            );

            int[] verseRange = RenderingProvider.Companion.getVerseRange(
                    item.getSourceText(),
                    item.getSourceTranslationFormat()
            );
            renderer.setPopulateVerseMarkers(verseRange);
        } else {
            renderingGroup.addEngine(new DefaultRenderer(null));
        }

        if (enableSearch) {
            renderingGroup.setSearchString(searchText, HIGHLIGHT_COLOR);
        }

        if ((text != null) && !text.trim().isEmpty()) {
            renderingGroup.init(text);
            CharSequence results = renderingGroup.start();
            item.hasMissingVerses = renderingGroup.isAddedMissingVerse();
            return results;
        } else {
            return "";
        }
    }

    /**
     * Find the closest position to drop verse marker. Weighted toward beginning of word.
     *
     * @param offset - initial drop position
     * @param text   - edit text
     * @return closest position to drop verse marker
     */
    private int closestSpotForVerseMarker(int offset, CharSequence text) {
        if (offset <= 0) {
            return 0;
        }

        if (offset >= text.length()) {
            offset = text.length() - 1;
        }

        while (offset > 0 && isWhitespace(text.charAt(offset))) {
            offset--;
        }

        while (offset > 0 && !isWhitespace(text.charAt(offset))) {
            offset--;
        }

        while (offset > 0 && isFootNote(text, offset)) {
            offset--;
        }

        return (offset > 0) ? offset + 1 : offset;
    }

    private boolean isFootNote(CharSequence text, int offset) {
        Pattern pattern = Pattern.compile(USFMNoteSpan.PATTERN);
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            if (offset >= matcher.start() && offset < matcher.end()) {
                return true;
            }
        }
        return false;
    }

    /**
     * test if character is whitespace
     *
     * @param c character
     * @return true if whitespace
     */
    private boolean isWhitespace(char c) {
        return (c == ' ') || (c == '\t') || (c == '\n') || (c == '\r');
    }

    private SpannableString resetHighlightColor(CharSequence text) {
        SpannableString noHighlightText = new SpannableString(text);
        BackgroundColorSpan background = new BackgroundColorSpan(Color.TRANSPARENT);
        ForegroundColorSpan foreground = new ForegroundColorSpan(
                ColorUtil.getColor(context, R.color.dark_primary_text)
        );

        noHighlightText.setSpan(
                background,
                0,
                text.length(),
                Spanned.SPAN_INCLUSIVE_INCLUSIVE
        );
        noHighlightText.setSpan(
                foreground,
                0,
                text.length(),
                0
        );
        return noHighlightText;
    }

    /**
     * Highlights one word based on the given position (index) of the original string.
     *
     * @param position the drop position (index) to highlight
     * @param text     the original string
     * @return a SpannableString with the highlighted range added
     */
    private SpannableString highlightWordAt(final int position, CharSequence text) {
        int start = closestSpotForVerseMarker(position, text);
        int end = start + 1;
        // move end position toward the end of word (if currently not)
        while (end < text.length() && !isWhitespace(text.charAt(end))) {
            end++;
        }
        SpannableString str = resetHighlightColor(text);
        BackgroundColorSpan bgColor = new BackgroundColorSpan(
                ColorUtil.getColor(context, R.color.highlight_background_color)
        );
        str.setSpan(bgColor, start, end, 0);
        ForegroundColorSpan fgColor = new ForegroundColorSpan(
                ColorUtil.getColor(context, R.color.highlighted_foreground_color)
        );
        str.setSpan(fgColor, start, end, 0);
        return str;
    }

    /**
     * display selected footnote in dialog. If editable, then it adds options to delete and edit
     * the footnote
     *
     * @param holder review holder
     * @param item review list item
     * @param span note span
     * @param editable if true then allow editing
     */
    private void showFootnote(
            final ReviewHolder holder,
            final ReviewListItem item,
            final NoteSpan span,
            final int start,
            final int end,
            boolean editable
    ) {
        CharSequence marker = span.getPassage();
        CharSequence title = context.getResources().getText(R.string.title_footnote);
        if (!marker.toString().isEmpty()) {
            title = title + ": " + marker;
        }
        CharSequence message = span.getNotes();

        if (editable && !item.isComplete()) {
            new AlertDialog.Builder(context, R.style.AppTheme_Dialog)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton(R.string.dismiss, null)
                    .setNeutralButton(R.string.edit,
                            (dialog, which) -> editFootnote(span.getNotes(), holder, item, start, end))

                    .setNegativeButton(R.string.label_delete,
                            (dialog, which) -> deleteFootnote(span.getNotes(), holder, item, start, end))
                    .show();

        } else {
            new AlertDialog.Builder(context, R.style.AppTheme_Dialog)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton(R.string.dismiss, null)
                    .show();
        }
    }

    /**
     * prompt to confirm removal of specific footnote at position
     *
     * @param note note text
     * @param holder review holder
     * @param item review list item
     * @param start start position
     * @param end end position
     */
    private void deleteFootnote(
            CharSequence note,
            final ReviewHolder holder,
            final ReviewListItem item,
            final int start,
            final int end
    ) {
        final EditText editText = holder.getEditText(item.isEditing);
        final CharSequence original = editText.getText();

        new AlertDialog.Builder(context, R.style.AppTheme_Dialog)
                .setTitle(R.string.footnote_confirm_delete)
                .setMessage(note)
                .setPositiveButton(R.string.label_delete, (dialog, which) -> placeFootnote(null,
                        original, start, end, holder, item, editText))
                .setNegativeButton(R.string.title_cancel, null)
                .show();
    }

    /**
     * generate spannable for source text.  Will add click listener for notes if supported
     * <p>
     * Currently this is also used when rendering the target text when not editable.
     *
     * @param holder Review holder
     * @param item Review list item
     * @param editable Editable
     * @return rendered text
     */
    private CharSequence renderTargetText(
            final ReviewHolder holder,
            final ReviewListItem item,
            final boolean editable
    ) {
        RenderingGroup renderingGroup = new RenderingGroup();
        boolean enableSearch = searchText != null && searchSubject != null;
        if (editable) {
            // make sure we are searching target
            enableSearch &= searchSubject == SearchSubject.TARGET;
        }
        if (Clickables.isClickableFormat(item.getTargetTranslationFormat())) {
            Span.OnClickListener noteClickListener = new Span.OnClickListener() {
                @Override
                public void onClick(View view, Span span, int start, int end) {
                    if (span instanceof NoteSpan) {
                        showFootnote(holder, item, (NoteSpan) span, start, end, editable);
                    }
                }

                @Override
                public void onLongClick(View view, Span span, int start, int end) {
                }
            };

            renderingProvider.setupRenderingGroup(
                    item.getTargetTranslationFormat(),
                    renderingGroup,
                    null,
                    noteClickListener,
                    false
            );

            if (editable) {
                if (!item.isComplete()) {
                    renderingGroup.setVersesEnabled(false);
                    renderingGroup.setParagraphsEnabled(false);
                }
            }
        } else {
            renderingGroup.addEngine(new DefaultRenderer(null));
        }

        if (enableSearch) {
            renderingGroup.setSearchString(searchText, HIGHLIGHT_COLOR);
        }

        renderingGroup.init(item.getTargetText());
        CharSequence results = renderingGroup.start();
        item.hasMissingVerses = renderingGroup.isAddedMissingVerse();
        return results;
    }

    @Override
    public int getItemCount() {
        return filteredItems.size();
    }

    /**
     * show or hide the merge conflict icon
     *
     * @param showMergeConflict if true then show merge conflict icon
     * @param mergeConflictFilterMode if true then filter merge conflicts
     */
    private void showMergeConflictIcon(
            final boolean showMergeConflict,
            boolean mergeConflictFilterMode
    ) {
        final boolean mergeConflictFilterEnabled = showMergeConflict && mergeConflictFilterMode;
        if ((showMergeConflict != haveMergeConflict) || (mergeConflictFilterEnabled != mergeConflictFilterOn)) {
            Handler hand = new Handler(Looper.getMainLooper());
            hand.post(() -> {
                OnEventListener listener = getListener();
                if (listener != null) {
                    listener.onEnableMergeConflict(showMergeConflict, mergeConflictFilterEnabled);
                }
            });
        }
        haveMergeConflict = showMergeConflict;
        mergeConflictFilterOn = mergeConflictFilterEnabled;
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
    public void onSourceFootnoteClick(ReviewListItem item, NoteSpan span, int start, int end) {
        int position = filteredItems.indexOf(item);
        if (getListener() == null) return;
        ReviewHolder holder = (ReviewHolder) getListener().getVisibleViewHolder(position);
        if (holder == null) return;
        showFootnote(holder, item, span, start, end, false);
    }

    /**
     * for returning multiple values in the text search results
     */
    private static class MatchResults {
        final private int foundLocation;
        final private int numberFound;
        final private boolean needRender;

        public MatchResults(int foundLocation, int numberFound, boolean needRender) {
            this.foundLocation = foundLocation;
            this.numberFound = numberFound;
            this.needRender = needRender;
        }
    }

    /**
     * move to next (forward/previous) search item. If current position has matches, then it will
     * first try to move to the next item within the chunk. Otherwise it will find the next
     * chunk with text.
     *
     * @param forward if true then find next instance (moving down the page), otherwise will find
     *                previous (moving up the page)
     */
    @Override
    public void onMoveSearch(boolean forward) {
        Log.i(TAG, "onMoveSearch position " + searchPosition + " forward=" + forward);

        int foundPos = findNextMatchChunk(forward);
        if (foundPos >= 0) {
            Log.i(TAG, "onMoveSearch foundPos=" + foundPos);
            searchPosition = foundPos;
            searchSubPositionItems = -1;

            onSearching(false, numberOfChunkMatches, false, false);

            ReviewListItem item = getItem(searchPosition);
            if (item != null) {
                findSearchItemInChunkAndPreselect(item, searchingTarget);
            }

            if (getListener() != null) {
                Log.i(TAG, "onMoveSearch position=" + foundPos);
                getListener().onSetSelectedPosition(foundPos, 0); // coarse scrolling
            }
        } else { // not found, clear last selection
            Log.i(TAG, "onMoveSearch at limit = " + searchPosition);
            showAtLimit(forward);
            if (forward) {
                searchPosition++;
            } else {
                searchPosition--;
            }
        }
    }

    /**
     * check if current highlight is at either limit (forward or back)
     */
    private void checkIfAtSearchLimits() {
        checkIfAtSearchLimit(true);
        checkIfAtSearchLimit(false);
    }

    /**
     * check if current highlight is at limit
     *
     * @param forward if true then we are at limit, otherwise we are at start
     */
    private void checkIfAtSearchLimit(boolean forward) {
        int nextPos = findNextMatchChunk(forward);
        if (nextPos < 0) {
            showAtLimit(forward);
        }
    }

    /**
     * indicate that we are at limit
     *
     * @param forward if true then we are at limit, otherwise we are at start
     */
    private void showAtLimit(boolean forward) {
        if (forward) {
            onSearching(false, numberOfChunkMatches, true, numberOfChunkMatches == 0);
        } else {
            onSearching(false, numberOfChunkMatches, numberOfChunkMatches == 0, true);
        }
    }

    /**
     * get next match item
     *
     * @param forward if true then find next instance (moving down the page), otherwise will find
     *                previous (moving up the page)
     * @return position of next match item
     */
    private int findNextMatchChunk(boolean forward) {
        int foundPos = -1;
        if (forward) {
            int start = Math.max(searchPosition, -1);
            for (int i = start + 1; i < filteredItems.size(); i++) {
                ReviewListItem item = (ReviewListItem) getItem(i);
                if (item.hasSearchText) {
                    foundPos = i;
                    break;
                }
            }
        } else { // previous
            int start = Math.min(searchPosition, filteredItems.size());
            for (int i = start - 1; i >= 0; i--) {
                ReviewListItem item = (ReviewListItem) getItem(i);
                if (item.hasSearchText) {
                    foundPos = i;
                    break;
                }
            }
        }
        return foundPos;
    }

    /**
     * gets the number of string matches within chunk and selects next item if going forward, or
     * the last item if going backward
     *
     * @param item review list item
     * @param target if true searching target card
     */
    private void findSearchItemInChunkAndPreselect(ReviewListItem item, boolean target) {
        MatchResults results = getMatchItemN(item, searchText, 1000, target); // get item count
        searchSubPositionItems = results.numberFound;
        int searchSubPosition = 0;
        if (results.needRender) {
            searchSubPositionItems = -1; // this will flag to get count after render completes
        } else {
            if (results.numberFound <= 0) {
                item.hasSearchText = false;
            }
            checkIfAtSearchLimits();
        }
        item.selectItemNum = searchSubPosition;
    }

    /**
     * search text to find the nth item (matchNumb) of the search string
     *
     * @param item review list item
     * @param match - search string
     * @param matchNumb - number of item to locate (0 based)
     * @param target    - if true searching target card
     * @return object containing position of match (-1 if not found), number of items actually
     * found (if less), and a flag that indicates that text needs to be rendered
     */
    private MatchResults getMatchItemN(
            ReviewListItem item,
            CharSequence match,
            int matchNumb,
            boolean target
    ) {
        String matcher = match.toString();
        int length = matcher.length();

        CharSequence text = searchingTarget ? item.renderedTargetText : item.renderedSourceText;
        boolean needRender = (text == null);

        boolean matcherEmpty = (matcher.isEmpty());
        if (matcherEmpty || needRender || (matchNumb < 0)
                || (target != searchingTarget)) {
            return new MatchResults(-1, -1, needRender);
        }

        Log.i(TAG, "getMatchItemN() Search started: " + matcher);

        int searchStartLocation = 0;
        int count = 0;
        int pos;
        String textLowerCase = text.toString().toLowerCase();

        while (true) {
            pos = textLowerCase.indexOf(matcher, searchStartLocation);
            if (pos < 0) { // not found
                break;
            }
            searchStartLocation = pos + length;
            if (++count > matchNumb) {
                return new MatchResults(pos, count, false);
            }
        }

        // failed, return number of items
        return new MatchResults(-1, count, false);
        // actually found
    }

    /**
     * technically no longer a filter but now a search that flags items containing search string
     *
     * @param constraint      if null, filter will be reset
     * @param subject         if null, filter will be reset
     * @param initialPosition - initial position
     */
    @Override
    public void filter(CharSequence constraint, SearchSubject subject, final int initialPosition) {
        if (constraint != null) {
            searchText = constraint.toString().toLowerCase().trim();
            searchSubject = subject;
            searchingTarget = subject == SearchSubject.TARGET || subject == SearchSubject.BOTH;

            searchItems(initialPosition);
        } else {
            searchText = "";
            searchSubject = null;

            for (ListItem item : filteredItems) {
                ReviewListItem reviewItem = (ReviewListItem) item;
                // Item will be re-rendered with default text (without highlights)
                if (reviewItem.hasSearchText) {
                    reviewItem.hasSearchText = false;
                    reviewItem.renderedSourceText = null;
                    reviewItem.renderedTargetText = null;
                }
            }
            triggerNotifyDataSetChanged();
        }
    }

    /**
     * notify listener of search state changes
     *
     * @param doingSearch          - search is currently processing
     * @param numberOfChunkMatches - number of chunks that have the search string
     * @param atEnd                - we are at last search item highlighted
     * @param atStart              - we are at first search item highlighted
     */
    private void onSearching(
            boolean doingSearch,
            int numberOfChunkMatches,
            boolean atEnd,
            boolean atStart
    ) {
        if (getListener() != null) {
            getListener().onSearching(doingSearch, numberOfChunkMatches, atEnd, atStart);
            this.numberOfChunkMatches = numberOfChunkMatches;
        }
    }

    /**
     * Sets the position where the list should start when first built
     *
     * @param startPosition - the position to start
     */
    @Override
    protected void setListStartPosition(int startPosition) {
        super.setListStartPosition(startPosition);
        searchPosition = startPosition;
    }

    @Override
    public boolean hasFilter() {
        return true;
    }

    /**
     * enable/disable merge conflict filter in adapter
     *
     * @param enableFilter - if true, then will enable filter
     * @param forceMergeConflict - if true, then will initialize merge conflict flag to true
     */
    @Override
    public void setMergeConflictFilter(boolean enableFilter, boolean forceMergeConflict) {
        // If items are not initialized, don't apply merge filter
        if (items.isEmpty()) return;

        if (forceMergeConflict) {
            // initialize merge conflict flag to true
            haveMergeConflict = true;
        }
        // update display and status flags
        showMergeConflictIcon(haveMergeConflict, enableFilter);

        if (!haveMergeConflict || !enableFilter) {
            // if no merge conflict or filter off, then remove filter
            filteredItems.clear();
            filteredItems.addAll(items);
            filteredChapters.clear();
            filteredChapters.addAll(chapters);

            if (mergeConflictFilterOn) {
                mergeConflictFilterOn = false;
                triggerNotifyDataSetChanged();
            }
            return;
        }

        mergeConflictFilterOn = true;

        CharSequence filterConstraint = "true"; // will filter if string is not null
        showMergeConflictIcon(true, true);

        MergeConflictFilter filter = getMergeConflictFilter();
        filter.filter(filterConstraint);
    }

    private @NonNull MergeConflictFilter getMergeConflictFilter() {
        MergeConflictFilter filter = new MergeConflictFilter(items);
        filter.setListener(new MergeConflictFilter.OnMatchListener() {
            @Override
            public void onMatch(@NonNull ListItem item) {
                if (!filteredChapters.contains(item.chapterSlug)) {
                    filteredChapters.add(item.chapterSlug);
                }
            }

            @Override
            public void onFinished(
                    @NonNull CharSequence constraint,
                    @NonNull ArrayList<ListItem> results
            ) {
                filteredItems.clear();
                filteredItems.addAll(results);
                updateMergeConflict();
                triggerNotifyDataSetChanged();
                checkForConflictSummary(filteredItems.size(), items.size());
            }
        });
        return filter;
    }

    @Override
    public void doCheckForMergeConflict() {
        int conflictCount = getConflictsCount();
        boolean mergeConflictFound = conflictCount > 0;
        Handler hand = new Handler(Looper.getMainLooper());
        hand.post(() -> {
            boolean doMergeFiltering = mergeConflictFound && mergeConflictFilterOn;
            final boolean conflictCountChanged = conflictCount != filteredItems.size();
            final boolean needToUpdateFilter = (doMergeFiltering != mergeConflictFilterOn) || conflictCountChanged;

            checkForConflictSummary(conflictCount, items.size());

            filter(searchText, searchSubject, searchPosition); // update search filter

            showMergeConflictIcon(mergeConflictFound, mergeConflictFilterOn);
            if (needToUpdateFilter) {
                setMergeConflictFilter(mergeConflictFilterOn, false);
            }
        });
    }

    /**
     * check if we are supposed to pop up summary
     *
     * @param conflictCount - number of conflicts
     * @param itemCount - number of items
     */
    protected void checkForConflictSummary(final int conflictCount, int itemCount) {
        if (showMergeSummary && (itemCount > 0) && (conflictCount > 0)) { // wait till after
            // items have been loaded
            showMergeSummary = false; // we just show the merge summary once

            Handler hand = new Handler(Looper.getMainLooper());
            hand.post(() -> {
                String message = context.getString(R.string.merge_summary, conflictCount);
                mergeConflictSummaryDisplayed = true;

                // pop up merge conflict summary
                new AlertDialog.Builder(context, R.style.AppTheme_Dialog)
                        .setTitle(R.string.merge_complete_title)
                        .setMessage(message)
                        .setPositiveButton(R.string.label_close,
                                (dialog, which) -> mergeConflictSummaryDisplayed = false)
                        .setCancelable(false)
                        .show();
            });
        }
    }

    /**
     * returns true if merge conflict summary dialog is being displayed.
     *
     * @return - true if merge conflict summary dialog is being displayed
     */
    @Override
    public boolean isMergeConflictSummaryDisplayed() {
        return mergeConflictSummaryDisplayed;
    }

    public void setOnRenderHelpsListener(OnRenderHelpsListener listener) {
        this.renderHelpsListener = listener;
    }

    public void setOnItemActionListener(OnShowToastListener listener) {
        this.itemActionListener = listener;
    }

    @Override
    public void onNotifyItemChanged(int position) {
        notifyItemChanged(position);
    }

    @Override
    public View onCreateRemovableTabLayout(String tag, String title) {
        return getListener().onCreateRemovableTabLayout(tag, title);
    }

    @Override
    public void onApplyLanguageTypefaceToTab(TabLayout layout, ContentValues values, String title) {
        if (getListener() != null) {
            getListener().onApplyLanguageTypefaceToTab(layout, values, title);
        }
    }

    private void searchItems(final int initialPosition) {
        final String matcher = searchText.toString();
        boolean matcherEmpty = matcher.isEmpty();

        if (matcher.isEmpty() && chunkSearchMatchesCounter == 0) {
            // TRICKY: don't run search if query is empty and there are not already matches
            return;
        }

        Log.i(TAG, "filter(): Search started: " + matcher);

        onSearching(true, 0, true, true);

        chunkSearchMatchesCounter = 0;
        for (ListItem item : filteredItems) {
            ReviewListItem reviewItem = (ReviewListItem) item;
            boolean match = false;

            if (!matcherEmpty) {
                if (searchingTarget) {
                    boolean foundMatch;

                    foundMatch = reviewItem.getTargetText().toLowerCase().contains(matcher);
                    if (foundMatch) { // if match, it could be in markup, so we
                        // double check by rendering and searching that
                        CharSequence text = renderTargetText(
                                reviewItem.getTargetText(),
                                reviewItem.getTargetTranslationFormat(),
                                reviewItem.getFt(),
                                null,
                                reviewItem
                        );
                        foundMatch = text.toString().toLowerCase().contains(matcher);
                    }
                    match = foundMatch;
                } else {
                    boolean foundMatch;
                    if (reviewItem.renderedSourceText != null) {
                        foundMatch = reviewItem.renderedSourceText.toString().toLowerCase()
                                .contains(matcher);
                    } else {
                        foundMatch = reviewItem.getSourceText().toLowerCase().contains(matcher);
                        if (foundMatch) { // if match, it could be in markup, so we
                            // double check by rendering and searching that
                            CharSequence text = onRenderSourceText(reviewItem);
                            foundMatch = text.toString().toLowerCase().contains(matcher);
                        }
                    }
                    match = foundMatch;
                }
            }

            if (reviewItem.hasSearchText && !match) { // check for search match cleared
                reviewItem.renderedTargetText = null;  // re-render target
                reviewItem.renderedSourceText = null;  // re-render source
                triggerNotifyItemChanged(reviewItem);
            }

            reviewItem.hasSearchText = match;
            if (match) {
                reviewItem.renderedTargetText = null;  // re-render target
                reviewItem.renderedSourceText = null;  // re-render source
                chunkSearchMatchesCounter++;
                triggerNotifyItemChanged(reviewItem);
            }
        }

        searchPosition = initialPosition;
        layoutBuildNumber++; // force redraw of displayed cards
        boolean zeroItemsFound = chunkSearchMatchesCounter <= 0;
        onSearching(false, chunkSearchMatchesCounter, zeroItemsFound, zeroItemsFound);
        if (!zeroItemsFound) {
            checkIfAtSearchLimits();
        }
    }

    /**
     * Disable/Enable items
     *
     * @param disable       - disable or enable
     * @param itemToExclude - item to exclude from disabling/enabling
     */
    private void toggleDisableItems(Boolean disable, @Nullable ListItem itemToExclude) {
        for (ListItem i : filteredItems) {
            if (itemToExclude == i) continue;
            i.isDisabled = disable;
        }
        triggerNotifyDataSetChanged();
    }
}