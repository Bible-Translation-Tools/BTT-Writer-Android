package com.door43.translationstudio.ui.translate;

import androidx.fragment.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.door43.data.IPreferenceRepository;
import com.door43.translationstudio.core.TargetTranslation;
import com.door43.translationstudio.databinding.ActivityTargetTranslationDetailBinding;
import com.door43.translationstudio.ui.viewmodels.TargetTranslationViewModel;
import com.google.android.material.snackbar.Snackbar;
import android.text.Editable;
import android.text.Layout;
import android.text.TextWatcher;
import android.util.Log;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import org.unfoldingword.tools.logger.Logger;

import com.door43.translationstudio.App;
import com.door43.translationstudio.R;
import com.door43.translationstudio.ui.SettingsActivity;
import com.door43.translationstudio.core.TranslationViewMode;
import com.door43.translationstudio.core.Translator;
import com.door43.translationstudio.ui.dialogs.BackupDialog;
import com.door43.translationstudio.ui.dialogs.FeedbackDialog;
import com.door43.translationstudio.ui.dialogs.PrintDialog;
import com.door43.translationstudio.ui.draft.DraftActivity;
import com.door43.translationstudio.ui.publish.PublishActivity;
import com.door43.translationstudio.ui.translate.review.SearchSubject;
import com.door43.widget.VerticalSeekBar;
import com.door43.widget.VerticalSeekBarHint;
import com.door43.widget.ViewUtil;
import com.door43.translationstudio.ui.BaseActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import it.moondroid.seekbarhint.library.SeekBarHint;

@AndroidEntryPoint
public class TargetTranslationActivity extends BaseActivity implements ViewModeFragment.OnEventListener, FirstTabFragment.OnEventListener, Spinner.OnItemSelectedListener {
    @Inject
    IPreferenceRepository prefRepository;

    private TargetTranslationViewModel viewModel;
    private ActivityTargetTranslationDetailBinding binding;

    private static final String TAG = "TranslationActivity";

    private static final long COMMIT_INTERVAL = 2 * 60 * 1000; // commit changes every 2 minutes
    public static final int SEARCH_START_DELAY = 1000;
    public static final String STATE_SEARCH_ENABLED = "state_search_enabled";
    public static final String STATE_SEARCH_TEXT = "state_search_text";
    public static final String STATE_HAVE_MERGE_CONFLICT = "state_have_merge_conflict";
    public static final String STATE_MERGE_CONFLICT_FILTER_ENABLED = "state_merge_conflict_filter_enabled";
    public static final String STATE_MERGE_CONFLICT_SUMMARY_DISPLAYED = "state_merge_conflict_summary_displayed";
    public static final String STATE_FILTER_MERGE_CONFLICTS = "state_filter_merge_conflicts";
    public static final String SEARCH_SOURCE = "search_source";
    public static final String STATE_SEARCH_AT_END = "state_search_at_end";
    public static final String STATE_SEARCH_AT_START = "state_search_at_start";
    public static final String STATE_SEARCH_FOUND_CHUNKS = "state_search_found_chunks";
    public static final int RESULT_DO_UPDATE = 42;

    private Fragment fragment;
    private ViewGroup graduations;
    private Timer commitTimer = new Timer();
    private boolean searchEnabled = false;
    private TextWatcher searchTextWatcher;
    private SearchTimerTask searchTimerTask;
    private Timer searchTimer;
    private String searchString;

    private final boolean enableGrids = false;
    private int seekbarMultiplier = 1; // allows for more granularity in setting position if cards are few
    private boolean haveMergeConflict = false;
    private boolean mergeConflictFilterEnabled = false;
    private int foundTextFormat;
    private boolean searchAtEnd = false;
    private boolean searchAtStart = false;
    private int numberOfChunkMatches = 0;
    private boolean searchResumed = false;
    private boolean showConflictSummary = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTargetTranslationDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(TargetTranslationViewModel.class);

        // validate parameters
        Bundle args = getIntent().getExtras();
        assert args != null;

        String targetTranslationId = args.getString(Translator.EXTRA_TARGET_TRANSLATION_ID, null);
        mergeConflictFilterEnabled = args.getBoolean(Translator.EXTRA_START_WITH_MERGE_FILTER, false);

        TargetTranslation translation = viewModel.getTargetTranslation(targetTranslationId);
        if (translation == null) {
            Logger.e(
                    TAG,
                    "A valid target translation id is required. Received " + targetTranslationId + " but the translation could not be found"
            );
            finish();
            return;
        }

        if(savedInstanceState == null) {
            // reset cached values
            ViewModeFragment.reset();
        }

        // open used source translations by default
        viewModel.openUsedSourceTranslations();

        // notify user that a draft translation exists the first time activity starts
        if(savedInstanceState == null && viewModel.draftIsAvailable() && viewModel.getTargetTranslation().numTranslated() == 0) {
            Snackbar snack = Snackbar.make(findViewById(android.R.id.content), R.string.draft_translation_exists, Snackbar.LENGTH_LONG)
                    .setAction(R.string.preview, v -> {
                        Intent intent = new Intent(TargetTranslationActivity.this, DraftActivity.class);
                        intent.putExtra(DraftActivity.EXTRA_TARGET_TRANSLATION_ID, viewModel.getTargetTranslation().getId());
                        startActivity(intent);
                    });
            ViewUtil.setSnackBarTextColor(snack, getResources().getColor(R.color.light_primary_text));
            snack.show();
        }

        // manual location settings
        int modeIndex = args.getInt(Translator.EXTRA_VIEW_MODE, -1);
        if (modeIndex > 0 && modeIndex < TranslationViewMode.values().length) {
            viewModel.setLastViewMode(modeIndex);
        }

        binding.searchPane.downSearch.setOnClickListener(v -> moveSearch(true));
        binding.searchPane.upSearch.setOnClickListener(v -> moveSearch(false));

        foundTextFormat = R.string.found_in_chunks;

        // inject fragments
        if (findViewById(R.id.fragment_container) != null) {
            if (savedInstanceState != null) {
                fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            } else {
                TranslationViewMode viewMode = viewModel.getLastViewMode();
                switch (viewMode) {
                    case READ:
                        fragment = new ReadModeFragment();
                        break;
                    case CHUNK:
                        fragment = new ChunkModeFragment();
                        break;
                    case REVIEW:
                        fragment = new ReviewModeFragment();
                        break;
                }
                fragment.setArguments(getIntent().getExtras());
                getSupportFragmentManager()
                        .beginTransaction()
                        .add(R.id.fragment_container, fragment)
                        .commit();
                // TODO: animate
                // TODO: udpate menu
            }
        }

        setUpSeekBar();

        // set up menu items
        buildMenu();

        binding.translatorSidebar.warnMergeConflict.setOnClickListener(v -> {
            mergeConflictFilterEnabled = !mergeConflictFilterEnabled; // toggle filter state
            setMergeConflictFilter(); // update displayed state
            openTranslationMode(TranslationViewMode.REVIEW, null); // make sure we are in review mode
        });

        binding.translatorSidebar.actionRead.setOnClickListener(v -> {
            removeSearchBar();
            openTranslationMode(TranslationViewMode.READ, null);
        });

        binding.translatorSidebar.actionChunk.setOnClickListener(v -> {
            removeSearchBar();
            openTranslationMode(TranslationViewMode.CHUNK, null);
        });

        binding.translatorSidebar.actionReview.setOnClickListener(v -> {
            removeSearchBar();
            mergeConflictFilterEnabled = false;
            setMergeConflictFilter();
            openTranslationMode(TranslationViewMode.REVIEW, null);
        });

        if(savedInstanceState != null) {
            searchEnabled = savedInstanceState.getBoolean(STATE_SEARCH_ENABLED, false);
            searchResumed = searchEnabled;
            searchAtEnd = savedInstanceState.getBoolean(STATE_SEARCH_AT_END, false);
            searchAtStart = savedInstanceState.getBoolean(STATE_SEARCH_AT_START, false);
            numberOfChunkMatches = savedInstanceState.getInt(STATE_SEARCH_FOUND_CHUNKS, 0);
            searchString = savedInstanceState.getString(STATE_SEARCH_TEXT, null);
            haveMergeConflict = savedInstanceState.getBoolean(STATE_HAVE_MERGE_CONFLICT, false);
            mergeConflictFilterEnabled = savedInstanceState.getBoolean(STATE_MERGE_CONFLICT_FILTER_ENABLED, false);
            showConflictSummary = savedInstanceState.getBoolean(STATE_MERGE_CONFLICT_SUMMARY_DISPLAYED, false);
        } else {
            showConflictSummary = mergeConflictFilterEnabled;
        }

        setupSidebarModeIcons();
        setSearchBarVisibility(searchEnabled);
        if(searchEnabled) {
            setSearchSpinner(true, numberOfChunkMatches, searchAtEnd, searchAtStart); // restore initial state
        }

        restartAutoCommitTimer();
    }

    /**
     * enable/disable merge conflict filter in adapter
     */
    private void setMergeConflictFilter() {
        Handler hand = new Handler(Looper.getMainLooper());
        hand.post(() -> {
            if(fragment instanceof ViewModeFragment viewModeFragment) {
                viewModeFragment.setShowMergeSummary(showConflictSummary);
                viewModeFragment.setMergeConflictFilter(
                        mergeConflictFilterEnabled,
                        false
                );
            }
            onEnableMergeConflict(haveMergeConflict, mergeConflictFilterEnabled);
        });
    }

    /**
     * called by adapter to set state for merge conflict icon
     */
    @Override
    public void onEnableMergeConflict(boolean showConflicted, boolean active) {
        haveMergeConflict = showConflicted;
        mergeConflictFilterEnabled = active;
        binding.translatorSidebar.warnMergeConflict.setVisibility(showConflicted ? View.VISIBLE : View.GONE);
        if(mergeConflictFilterEnabled) {
            binding.translatorSidebar.warnMergeConflict.setImageResource(R.drawable.ic_warning_white_24dp);
            final int highlightedColor = getResources().getColor(R.color.primary_dark);
            binding.translatorSidebar.warnMergeConflict.setBackgroundColor(highlightedColor);
        } else {
            binding.translatorSidebar.warnMergeConflict.setImageResource(R.drawable.ic_warning_inactive_24dp);
            binding.translatorSidebar.warnMergeConflict.setBackgroundDrawable(null); // clear any previous background highlighting
        }
    }

    private void setUpSeekBar() {
        if(enableGrids) {
            graduations = binding.translatorSidebar.actionSeekGraduations;
        }
        SeekBar seekBar = (SeekBar) binding.translatorSidebar.actionSeek;
        seekBar.setMax(100);
        seekBar.setProgress(computePositionFromProgress(0));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
//                progress = handleItemCountIfChanged(progress);
                int correctedProgress = correctProgress(progress);
                correctedProgress = limitRange(correctedProgress, 0, seekBar.getMax() - 1);
                int position = correctedProgress / seekbarMultiplier;
                int percentage = 0;

                if(seekbarMultiplier > 1) { // if we need some granularity, calculate fractional amount
                    int fractional = correctedProgress - position * seekbarMultiplier;
                    if(fractional != 0) {
                        percentage = 100 * fractional / seekbarMultiplier;
                    }
                }

                // TODO: 2/16/17 record position

                // If this change was initiated by a click on a UI element (rather than as a result
                // of updates within the program), then update the view accordingly.
                if (fragment instanceof ViewModeFragment && fromUser) {
                    ((ViewModeFragment) fragment).onScrollProgressUpdate(position, percentage);
                }

                TargetTranslationActivity activity = (TargetTranslationActivity) seekBar.getContext();
                if (activity != null) {
                    activity.closeKeyboard();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                if(graduations != null) {
                    graduations.animate().alpha(1.f);
                }
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if(graduations != null) {
                    graduations.animate().alpha(0.f);
                }
            }
        });

        if(seekBar instanceof SeekBarHint) {
            ((SeekBarHint) seekBar).setOnProgressChangeListener((seekBarHint, progress) ->
                    getFormattedChapter(progress));
        }

        if(seekBar instanceof VerticalSeekBarHint) {
            ((VerticalSeekBarHint) seekBar).setOnProgressChangeListener((seekBarHint, progress) -> getFormattedChapter(progress));
        }
    }

    /**
     * clips value to within range min to max
     * @param value
     * @param min
     * @param max
     * @return
     */
    private int limitRange(int value, int min, int max) {
        int newValue = value;
        if(newValue < min) {
            newValue = min;
        } else
        if(newValue > max) {
            newValue = max;
        }
        return newValue;
    }

    /**
     * get chapter string to display
     * @param progress
     * @return
     */
    private String getFormattedChapter(int progress) {
        int position = computePositionFromProgress(progress);
        String chapter = getChapterSlug(position);
        String displayedText = " " + chapter + " ";
        return displayedText;
    }

    public void onSaveInstanceState(Bundle out) {
        out.putBoolean(STATE_SEARCH_ENABLED, searchEnabled);
        String searchText = getFilterText();
        if(searchEnabled) {
            out.putString(STATE_SEARCH_TEXT, searchText);
            out.putBoolean(STATE_SEARCH_AT_END, searchAtEnd);
            out.putBoolean(STATE_SEARCH_AT_START, searchAtStart);
            out.putInt(STATE_SEARCH_FOUND_CHUNKS, numberOfChunkMatches);
        }
        out.putBoolean(STATE_HAVE_MERGE_CONFLICT, haveMergeConflict);
        out.putBoolean(STATE_MERGE_CONFLICT_FILTER_ENABLED, mergeConflictFilterEnabled);
        if(fragment instanceof ViewModeFragment) {
            out.putBoolean(
                    STATE_MERGE_CONFLICT_SUMMARY_DISPLAYED,
                    ((ViewModeFragment) fragment).ismMergeConflictSummaryDisplayed()
            );
        }
        super.onSaveInstanceState(out);
    }

    private void buildMenu() {
        binding.translatorSidebar.actionMore.setOnClickListener(v -> {
            PopupMenu moreMenu = new PopupMenu(TargetTranslationActivity.this, v);
            ViewUtil.forcePopupMenuIcons(moreMenu);
            moreMenu.getMenuInflater().inflate(R.menu.menu_target_translation_detail, moreMenu.getMenu());

            // display menu item for draft translations
            MenuItem draftsMenuItem = moreMenu.getMenu().findItem(R.id.action_drafts_available);
            draftsMenuItem.setVisible(viewModel.draftIsAvailable());

            MenuItem searchMenuItem = moreMenu.getMenu().findItem(R.id.action_search);
            boolean searchSupported = isSearchSupported();
            searchMenuItem.setVisible(searchSupported);

            MenuItem markAllChunksItem = moreMenu.getMenu().findItem(R.id.mark_chunks_done);
            boolean markAllChunksSupported = isMarkAllChunksSupported();
            markAllChunksItem.setVisible(markAllChunksSupported);

            moreMenu.setOnMenuItemClickListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.action_translations) {
                    finish();
                    return true;
                } else if (itemId == R.id.action_publish) {
                    Intent publishIntent = new Intent(TargetTranslationActivity.this, PublishActivity.class);
                    publishIntent.putExtra(PublishActivity.EXTRA_TARGET_TRANSLATION_ID, viewModel.getTargetTranslation().getId());
                    publishIntent.putExtra(PublishActivity.EXTRA_CALLING_ACTIVITY, PublishActivity.ACTIVITY_TRANSLATION);
                    startActivity(publishIntent);
                    // TRICKY: we may move back and forth between the publisher and translation activites
                    // so we finish to avoid filling the stack.
                    finish();
                    return true;
                } else if (itemId == R.id.action_drafts_available) {
                    Intent intent = new Intent(TargetTranslationActivity.this, DraftActivity.class);
                    intent.putExtra(DraftActivity.EXTRA_TARGET_TRANSLATION_ID, viewModel.getTargetTranslation().getId());
                    startActivity(intent);
                    return true;
                } else if (itemId == R.id.action_backup) {
                    FragmentTransaction backupFt = getSupportFragmentManager().beginTransaction();
                    Fragment backupPrev = getSupportFragmentManager().findFragmentByTag(BackupDialog.TAG);
                    if (backupPrev != null) {
                        backupFt.remove(backupPrev);
                    }
                    backupFt.addToBackStack(null);

                    BackupDialog backupDialog = new BackupDialog();
                    Bundle args = new Bundle();
                    args.putString(BackupDialog.ARG_TARGET_TRANSLATION_ID, viewModel.getTargetTranslation().getId());
                    backupDialog.setArguments(args);
                    backupDialog.show(backupFt, BackupDialog.TAG);
                    return true;
                } else if (itemId == R.id.action_print) {
                    FragmentTransaction printFt = getSupportFragmentManager().beginTransaction();
                    Fragment printPrev = getSupportFragmentManager().findFragmentByTag("printDialog");
                    if (printPrev != null) {
                        printFt.remove(printPrev);
                    }
                    printFt.addToBackStack(null);

                    PrintDialog printDialog = new PrintDialog();
                    Bundle printArgs = new Bundle();
                    printArgs.putString(PrintDialog.ARG_TARGET_TRANSLATION_ID, viewModel.getTargetTranslation().getId());
                    printDialog.setArguments(printArgs);
                    printDialog.show(printFt, "printDialog");
                    return true;
                } else if (itemId == R.id.action_feedback) {
                    FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                    Fragment prev = getSupportFragmentManager().findFragmentByTag("bugDialog");
                    if (prev != null) {
                        ft.remove(prev);
                    }
                    ft.addToBackStack(null);

                    FeedbackDialog dialog = new FeedbackDialog();
                    dialog.show(ft, "bugDialog");
                    return true;
                } else if (itemId == R.id.action_settings) {
                    Intent settingsIntent = new Intent(
                            TargetTranslationActivity.this,
                            SettingsActivity.class
                    );
                    startActivity(settingsIntent);
                    return true;
                } else if (itemId == R.id.action_search) {
                    setSearchBarVisibility(true);
                    return true;
                } else if (itemId == R.id.mark_chunks_done) {
                    ((ViewModeFragment) fragment).markAllChunksDone();
                    return true;
                } else {
                    return false;
                }
            });
            moreMenu.show();
        });
    }

    /**
     * hide search bar and clear search text
     */
    private void removeSearchBar() {
        setSearchBarVisibility(false);
        setFilterText(null);
        filter(null); // clear search filter
    }

    /**
     * method to see if searching is supported
     */
    public boolean isSearchSupported() {
        if(fragment instanceof ViewModeFragment) {
            return ((ViewModeFragment) fragment).hasFilter();
        }
        return false;
    }

    /**
     * Check to see if marking all chunks done is supported
     */
    public boolean isMarkAllChunksSupported() {
        return fragment instanceof ReviewModeFragment;
    }

    /**
     * change state of search bar
     * @param show - if true set visible
     */
    private void setSearchBarVisibility(boolean show) {
        // toggle search bar
        int visibility = View.GONE;
        if(show) {
            visibility = View.VISIBLE;
        } else {
            App.closeKeyboard(TargetTranslationActivity.this);
            setSearchSpinner(false, 0, true, true);
        }

        binding.searchPane.getRoot().setVisibility(visibility);
        searchEnabled = show;

        if(searchTextWatcher != null) {
            binding.searchPane.searchText.removeTextChangedListener(searchTextWatcher); // remove old listener
            searchTextWatcher = null;
        }

        if(show) {
            searchTextWatcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }
                @Override
                public void afterTextChanged(Editable s) {
                    if(searchTimer != null) {
                        searchTimer.cancel();
                    }

                    searchTimer = new Timer();
                    searchTimerTask = new SearchTimerTask(TargetTranslationActivity.this, s);
                    searchTimer.schedule(searchTimerTask, SEARCH_START_DELAY);
                }
            };

            binding.searchPane.searchText.addTextChangedListener(searchTextWatcher);
            if(searchResumed) {
                // we don't have a way to reliably determine the state of the soft keyboard
                //   so we don't initially show the keyboard on resume.  This should be less
                //   annoying than always popping up the keyboard on resume
                searchResumed = false;
            } else {
                setFocusOnTextSearchEdit();
            }
        } else {
            filter(null); // clear search filter
        }

        if(searchString != null) { // restore after rotate
            binding.searchPane.searchText.setText(searchString);
            if(show) {
                filter(searchString);
            }
            searchString = null;
        }

        binding.searchPane.closeSearch.setOnClickListener(v -> removeSearchBar());

        List<String> types = new ArrayList<String>();
        types.add(this.getResources().getString(R.string.search_source));
        types.add(this.getResources().getString(R.string.search_translation));
        ArrayAdapter<String> typesAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, types);
        typesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.searchPane.searchType.setAdapter(typesAdapter);

        // restore last search type
        String lastSearchSourceStr = prefRepository.getDefaultPref(SEARCH_SOURCE, SearchSubject.SOURCE.name().toUpperCase(), String.class);
        SearchSubject lastSearchSource = SearchSubject.SOURCE;
        try {
            lastSearchSource = SearchSubject.valueOf(lastSearchSourceStr.toUpperCase());
        } catch(Exception e) {
            e.printStackTrace();
        }
        binding.searchPane.searchType.setSelection(lastSearchSource.ordinal());
        binding.searchPane.searchType.setOnItemSelectedListener(this);
    }

    /**
     * this seems crazy that we have to do a delay within a delay, but it is the only thing that works
     *   to bring up keyboard.  Guessing that it is because there is so much redrawing that is
     *   happening on bringing up the search bar.
     */
    @Deprecated
    private void setFocusOnTextSearchEdit() {
        Handler hand = new Handler(Looper.getMainLooper());
        hand.post(() -> {
            binding.searchPane.searchText.setFocusableInTouchMode(true);
            binding.searchPane.searchText.requestFocus();

            Handler hand1 = new Handler(Looper.getMainLooper());
            hand1.post(() -> App.showKeyboard(
                    TargetTranslationActivity.this,
                    binding.searchPane.searchText,
                    true
            ));
        });
    }

    /**
     * notify listener of search state changes
     * @param doingSearch - search is currently processing
     * @param numberOfChunkMatches - number of chunks that have the search string
     * @param atEnd - we are at last search item
     * @param atStart - we are at first search item
     */
    private void setSearchSpinner(boolean doingSearch, int numberOfChunkMatches, boolean atEnd, boolean atStart) {
        searchAtEnd = atEnd;
        searchAtStart = atStart;
        this.numberOfChunkMatches = numberOfChunkMatches;
        binding.searchPane.searchProgress.setVisibility(doingSearch ? View.VISIBLE : View.GONE);

        boolean showSearchNavigation = !doingSearch && (numberOfChunkMatches > 0);
        int searchVisibility = showSearchNavigation ? View.VISIBLE : View.INVISIBLE;
        binding.searchPane.downSearch.setVisibility(atEnd ? View.INVISIBLE : searchVisibility);
        binding.searchPane.upSearch.setVisibility(atStart ? View.INVISIBLE : searchVisibility);

        String msg = getResources().getString(foundTextFormat, numberOfChunkMatches);
        binding.searchPane.found.setVisibility( !doingSearch ? View.VISIBLE : View.INVISIBLE);
        binding.searchPane.found.setText(msg);
    }

    /**
     * called if search type is changed
     * @param parent
     * @param view
     * @param pos
     * @param id
     */
    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        filter(getFilterText());  // do search with search string in edit control
    }

    /**
     * called if no search type is selected
     * @param parent
     */
    public void onNothingSelected(AdapterView<?> parent) {
        // do nothing
    }

    /**
     * get the type of search
     */
    private SearchSubject getFilterSubject() {
        int pos = binding.searchPane.searchType.getSelectedItemPosition();
        if(pos == 0) {
            return SearchSubject.SOURCE;
        }
        return SearchSubject.TARGET;
    }

    /**
     * get search text in search bar
     */
    private String getFilterText() {
        return binding.searchPane.searchText.getText().toString();
    }

    /**
     * set search text in search bar
     */
    private void setFilterText(String text) {
        binding.searchPane.searchText.setText(text);
    }


    /**
     * Filters the list, currently it just marks chunks with text
     * @param constraint
     */
    public void filter(final String constraint) {
        Handler hand = new Handler(Looper.getMainLooper());
        hand.post(() -> {
            if((fragment != null) && (fragment instanceof ViewModeFragment)) {
                // preserve current search type
                SearchSubject subject = getFilterSubject();
                viewModel.saveSearchSource(subject.name().toUpperCase());
                ((ViewModeFragment) fragment).filter(constraint, subject);
            }
        });
    }

    /**
     * move to next/previous search item
     * @param next if true then find next, otherwise will find previous
     */
    public void moveSearch(final boolean next) {
        App.closeKeyboard(this);
        Handler hand = new Handler(Looper.getMainLooper());
        hand.post(() -> {
            if((fragment != null) && (fragment instanceof ViewModeFragment)) {
                ((ViewModeFragment) fragment).onMoveSearch(next);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        notifyDatasetChanged();
        buildMenu();
        //setMergeConflictFilter(mMergeConflictFilterEnabled, mMergeConflictFilterEnabled); // restore last state
    }

    @Override
    public void onPause() {
        super.onPause();

        if(fragment instanceof ViewModeFragment) {
            showConflictSummary = ((ViewModeFragment) fragment).ismMergeConflictSummaryDisplayed(); // update current state
        }
    }

    public void closeKeyboard() {
        if (fragment instanceof ViewModeFragment) {
            boolean enteringSearchText = searchEnabled && (binding.searchPane.searchText.hasFocus());
            if(!enteringSearchText) { // we don't want to close keyboard if we are entering search text
                ((ViewModeFragment) fragment).closeKeyboard();
            }
        }
    }

    public void checkIfCursorStillOnScreen() {

        Rect cursorPos = getCursorPositionOnScreen();
        if (cursorPos != null) {

            View scrollView = findViewById(R.id.fragment_container);
            if (scrollView != null) {

                Boolean visible = true;

                Rect scrollBounds = new Rect();
                scrollView.getHitRect(scrollBounds);

                if (cursorPos.top < scrollBounds.top) {
                    visible = false;
                }
                else if (cursorPos.bottom > scrollBounds.bottom) {
                    visible = false;
                }

                if (!visible) {
                    closeKeyboard();
                }
            }
        }
    }

    public Rect getCursorPositionOnScreen() {

        View focusedView = (View) getCurrentFocus();
        if (focusedView != null) {

            // get view position on screen
            int[] l = new int[2];
            focusedView.getLocationOnScreen(l);
            int focusedViewX = l[0];
            int focusedViewY = l[1];

            if (focusedView instanceof EditText) {

                // getting relative cursor position
                EditText editText = (EditText) focusedView;
                int pos = editText.getSelectionStart();
                Layout layout = editText.getLayout();
                if (layout != null) {
                    int line = layout.getLineForOffset(pos);
                    int baseline = layout.getLineBaseline(line);
                    int ascent = layout.getLineAscent(line);

                    // convert relative positions to absolute position
                    int x = focusedViewX + (int) layout.getPrimaryHorizontal(pos);
                    int bottomY = focusedViewY + baseline;
                    int y = bottomY + ascent;

                    return new Rect(x, y, x, bottomY); // ignore width of cursor for now
                }
            }
        }

        return null;
    }

    @Override
    public void onScrollProgress(int position) {
//        position = handleItemCountIfChanged(position);
        // TODO: 2/16/17 record scroll position
        int progress = computeProgressFromPosition(position);
        // Too much logging
        //Log.d(TAG, "onScrollProgress: position=" + position + ", mapped to progressbar=" + progress);
        SeekBar seekBar = (SeekBar) binding.translatorSidebar.actionSeek;
        seekBar.setProgress(progress);
        checkIfCursorStillOnScreen();
    }

    @Override
    public void onDataSetChanged(int count) {
        SeekBar seekBar = (SeekBar) binding.translatorSidebar.actionSeek;
        int initialMax = seekBar.getMax();
        int initialProgress = seekBar.getProgress();

        count = setSeekbarMax(count);
        int newMax = seekBar.getMax();
        if(initialMax != newMax) { // if seekbar maximum has changed
            // adjust proportionally
            int newProgress = newMax * initialProgress / initialMax;
            seekBar.setProgress(newProgress);
        }
        closeKeyboard();
        setupGraduations();
    }

    /**
     * get number of items in adapter
     * @return
     */
    private int getItemCount() {
        if((fragment != null) && (fragment instanceof ViewModeFragment)) {
            return ((ViewModeFragment) fragment).getItemCount();
        }
        return 0;
    }

    /**
     * sets seekbar maximum based on item count, and add granularity if item count is small
     * @param itemCount
     * @return
     */
    private int setSeekbarMax(int itemCount) {
        final int minimumSteps = 300;

        Log.i(TAG,"setSeekbarMax: itemCount=" + itemCount);

        if(itemCount < 1) { // sanity check
            itemCount = 1;
        }

        if(itemCount < minimumSteps) {  // increase step size if number of cards is small, this gives more granularity in positioning
            seekbarMultiplier = (int) (minimumSteps / itemCount) + 1;
        } else {
            seekbarMultiplier = 1;
        }

        SeekBar seekBar = (SeekBar) binding.translatorSidebar.actionSeek;
        int newMax = itemCount * seekbarMultiplier;
        int oldMax = seekBar.getMax();
        if(newMax != oldMax) {
            Log.i(TAG,"setSeekbarMax: oldMax=" + oldMax + ", newMax=" + newMax + ", mSeekbarMultiplier=" + seekbarMultiplier);
            seekBar.setMax(newMax);
        } else {
            Log.i(TAG, "setSeekbarMax: max unchanged=" + oldMax);
        }
        return itemCount;
    }

    /**
     * initialize text on graduations if enabled
     */
    private void setupGraduations() {
        if(enableGrids) {
            SeekBar seekBar = (SeekBar) binding.translatorSidebar.actionSeek;
            final int numCards = seekBar.getMax() / seekbarMultiplier;

            String maxChapterStr = getChapterSlug(numCards - 1);
            int maxChapter = Integer.valueOf(maxChapterStr);

            // Set up visibility of the graduation bar.
            // Display graduations evenly spaced by number of chapters (but not more than the number
            // of chapters that exist). As a special case, display nothing if there's only one chapter.
            // Also, show nothing unless we're in read mode, since the other modes are indexed by
            // frame, not by chapter, so displaying either frame numbers or chapter numbers would be
            // nonsensical.
            int numVisibleGraduations = Math.min(numCards, graduations.getChildCount());

            if ((maxChapter > 0) && (maxChapter < numVisibleGraduations)) {
                numVisibleGraduations = maxChapter;
            }

            if (numVisibleGraduations < 2) {
                numVisibleGraduations = 0;
            }

            // Set up the visible chapters.
            for (int i = 0; i < numVisibleGraduations; ++i) {
                ViewGroup container = (ViewGroup) graduations.getChildAt(i);
                container.setVisibility(View.VISIBLE);
                TextView text = (TextView) container.getChildAt(1);

                int position = i * (numCards - 1) / (numVisibleGraduations - 1);
                String chapter = getChapterSlug(position);
                text.setText(chapter);
            }

            // Undisplay the invisible chapters.
            for (int i = numVisibleGraduations; i < graduations.getChildCount(); ++i) {
                graduations.getChildAt(i).setVisibility(View.GONE);
            }
        }
    }

    /**
     * get the chapter slug for the position
     * @param position
     * @return
     */
    private String getChapterSlug(int position) {
        if( (fragment != null) && (fragment instanceof ViewModeFragment)) {
            return ((ViewModeFragment) fragment).getChapterSlug(position);
        }
        return Integer.toString(position + 1);
    }

    /**
     * user has selected to update sources
     */
    public void onUpdateSources() {
        setResult(RESULT_DO_UPDATE);
        finish();
    }

    private boolean displaySeekBarAsInverted() {
        return binding.translatorSidebar.actionSeek instanceof VerticalSeekBar;
    }

    private int computeProgressFromPosition(int position) {
        SeekBar seekBar = (SeekBar) binding.translatorSidebar.actionSeek;
        int correctedProgress = correctProgress(position * seekbarMultiplier);
        int progress = limitRange(correctedProgress, 0, seekBar.getMax());
        return progress;
    }

    private int computePositionFromProgress(int progress) {
        SeekBar seekBar = (SeekBar) binding.translatorSidebar.actionSeek;
        int correctedProgress = correctProgress(progress);
        correctedProgress = limitRange(correctedProgress, 0, seekBar.getMax() - 1);
        int position = correctedProgress / seekbarMultiplier;
        return position;
    }

    /**
     * if seekbar is inverted, this will correct the progress
     * @param progress
     * @return
     */
    private int correctProgress(int progress) {
        SeekBar seekBar = (SeekBar) binding.translatorSidebar.actionSeek;
        return displaySeekBarAsInverted() ? seekBar.getMax() - progress : progress;
    }

    @Override
    public void onNoSourceTranslations() {
        if (!(fragment instanceof FirstTabFragment)) {
            fragment = new FirstTabFragment();
            fragment.setArguments(getIntent().getExtras());
            getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, fragment).commit();
            buildMenu();
        }
    }

    @Override
    public void openTranslationMode(TranslationViewMode mode, Bundle extras) {
        Bundle fragmentExtras = new Bundle();
        fragmentExtras.putAll(getIntent().getExtras());
        if (extras != null) {
            fragmentExtras.putAll(extras);
        }

        // close the keyboard when switching between modes
        if (getCurrentFocus() != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        } else {
            getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        }

        viewModel.setLastViewMode(mode);
        setupSidebarModeIcons();

        switch (mode) {
            case READ:
                if (!(fragment instanceof ReadModeFragment)) {
                    fragment = new ReadModeFragment();
                    fragment.setArguments(fragmentExtras);

                    getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, fragment).commit();
                    // TODO: animate
                    // TODO: update menu
                }
                break;
            case CHUNK:
                if (!(fragment instanceof ChunkModeFragment)) {
                    fragment = new ChunkModeFragment();
                    fragment.setArguments(fragmentExtras);

                    getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, fragment).commit();
                    // TODO: animate
                    // TODO: update menu
                }
                break;
            case REVIEW:
                if (!(fragment instanceof ReviewModeFragment)) {
                    fragmentExtras.putBoolean(STATE_FILTER_MERGE_CONFLICTS, mergeConflictFilterEnabled);
                    fragment = new ReviewModeFragment();
                    fragment.setArguments(fragmentExtras);

                    getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, fragment).commit();
                    // TODO: animate
                    // TODO: update menu
                }
                break;
        }
    }

    /**
     * Restart scheduled translation commits
     */
    public void restartAutoCommitTimer() {
        commitTimer.cancel();
        commitTimer = new Timer();
        commitTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    viewModel.getTargetTranslation().commit();
                } catch (Exception e) {
                    Logger.e(TargetTranslationActivity.class.getName(), "Failed to commit the latest translation of " + viewModel.getTargetTranslation().getId(), e);
                }
            }
        }, COMMIT_INTERVAL, COMMIT_INTERVAL);
    }

    /**
     * callback on search state changes
     * @param doingSearch - search is currently processing
     * @param numberOfChunkMatches - number of chunks that have the search string
     * @param atEnd - we are at last search item highlighted
     * @param atStart - we are at first search item highlighted
     */
    @Override
    public void onSearching(boolean doingSearch, int numberOfChunkMatches, boolean atEnd, boolean atStart) {
        setSearchSpinner(doingSearch, numberOfChunkMatches, atEnd, atStart);
    }

    @Override
    public void onHasSourceTranslations() {
        TranslationViewMode viewMode = viewModel.getLastViewMode();
        if (viewMode == TranslationViewMode.READ) {
            fragment = new ReadModeFragment();
        } else if (viewMode == TranslationViewMode.CHUNK) {
            fragment = new ChunkModeFragment();
        } else if (viewMode == TranslationViewMode.REVIEW) {
            fragment = new ReviewModeFragment();
        }
        fragment.setArguments(getIntent().getExtras());
        getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, (Fragment) fragment).commit();
        // TODO: animate
        // TODO: update menu
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (fragment instanceof ViewModeFragment) {
            if (!((ViewModeFragment) fragment).onTouchEvent(event)) {
                return super.dispatchTouchEvent(event);
            } else {
                return true;
            }
        } else {
            return super.dispatchTouchEvent(event);
        }
    }

    @Override
    public void onDestroy() {
        commitTimer.cancel();
        try {
            viewModel.getTargetTranslation().commit();
        } catch (Exception e) {
            Logger.e(this.getClass().getName(), "Failed to commit changes before closing translation", e);
        }
        App.closeKeyboard(TargetTranslationActivity.this);
        super.onDestroy();
    }

    /**
     * Causes the activity to tell the fragment it needs to reload
     */
    public void notifyDatasetChanged() {
        if (fragment instanceof ViewModeFragment && ((ViewModeFragment) fragment).getAdapter() != null) {
            ((ViewModeFragment) fragment).getAdapter().triggerNotifyDataSetChanged();
        }
    }

    /**
     * Causes the activity to tell the fragment that everything needs to be redrawn
     */
    public void redrawTarget() {
        if (fragment instanceof ViewModeFragment) {
            ((ViewModeFragment) fragment).onResume();
        }
    }

    /**
     * Updates the visual state of all the sidebar icons to match the application's current mode.
     *
     * Call this when creating the activity or when changing modes.
     */
    private void setupSidebarModeIcons() {
        TranslationViewMode viewMode = viewModel.getLastViewMode();

        // Set the non-highlighted icons by default.
        binding.translatorSidebar.actionReview.setImageResource(R.drawable.ic_view_week_inactive_24dp);
        binding.translatorSidebar.actionChunk.setImageResource(R.drawable.ic_content_copy_inactive_24dp);
        binding.translatorSidebar.actionRead.setImageResource(R.drawable.ic_subject_inactive_24dp);

        // Clear the highlight background.
        //
        // This is more properly done with setBackground(), but that requires a higher API
        // level than this application's minimum. Equivalently use setBackgroundDrawable(),
        // which is deprecated, instead.
        binding.translatorSidebar.actionReview.setBackground(null);
        binding.translatorSidebar.actionChunk.setBackground(null);
        binding.translatorSidebar.actionRead.setBackground(null);

        // For the active view, set the correct icon, and highlight the background.
        final int highlightedColor = getResources().getColor(R.color.primary_dark);
        switch (viewMode) {
            case READ:
                binding.translatorSidebar.actionRead.setImageResource(R.drawable.ic_subject_white_24dp);
                binding.translatorSidebar.actionRead.setBackgroundColor(highlightedColor);
                break;
            case CHUNK:
                binding.translatorSidebar.actionChunk.setImageResource(R.drawable.ic_content_copy_white_24dp);
                binding.translatorSidebar.actionChunk.setBackgroundColor(highlightedColor);
                break;
            case REVIEW:
                if(mergeConflictFilterEnabled) {
                    binding.translatorSidebar.warnMergeConflict.setBackgroundColor(highlightedColor); // highlight background of the conflict icon
                    onEnableMergeConflict(true, true);
                } else {
                    binding.translatorSidebar.actionReview.setBackgroundColor(highlightedColor);
                    binding.translatorSidebar.actionReview.setImageResource(R.drawable.ic_view_week_white_24dp);
                }
                break;
        }
    }

    private class SearchTimerTask extends TimerTask {

        private TargetTranslationActivity activity;
        private Editable searchString;

        public SearchTimerTask(TargetTranslationActivity activity, Editable searchString) {
            this.activity = activity;
            this.searchString = searchString;
        }

        @Override
        public void run() {
            activity.filter(searchString.toString());
        }
    }
}