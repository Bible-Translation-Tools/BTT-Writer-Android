package com.door43.translationstudio.library;

import android.app.AlertDialog;
import android.app.SearchManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.door43.translationstudio.R;
import com.door43.translationstudio.library.temp.LibraryTempData;
import com.door43.translationstudio.util.TranslatorBaseActivity;

/**
 * An activity representing a list of Projects. This activity
 * has different presentations for handset and tablet-size devices. On
 * handsets, the activity presents a list of items, which when touched,
 * lead to a {@link ProjectLibraryDetailActivity} representing
 * item details. On tablets, the activity presents the list of items and
 * item details side-by-side using two vertical panes.
 * <p/>
 * The activity makes heavy use of fragments. The list of items is a
 * {@link ProjectLibraryListFragment} and the item details
 * (if present) is a {@link ProjectLibraryDetailFragment}.
 * <p/>
 * This activity also implements the required
 * {@link ProjectLibraryListFragment.Callbacks} interface
 * to listen for item selections.
 */
public class ProjectLibraryListActivity extends TranslatorBaseActivity implements ProjectLibraryListFragment.Callbacks, TranslationDraftsTabFragment.Callbacks, LibraryCallbacks {

    public static final String ARG_SHOW_UPDATES = "only_show_updates";
    public static final String ARG_SHOW_NEW = "only_show_new";
    private AlertDialog mConfirmDialog;
    /**
     * Whether or not the activity is in two-pane mode, i.e. running on a tablet
     * device.
     */
    private boolean mTwoPane;
    private Toolbar mToolbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_project_library_list);

        // set up tool bar
        mToolbar = (Toolbar)findViewById(R.id.toolbar_server_library);
        setSupportActionBar(mToolbar);
        getSupportActionBar().setHomeButtonEnabled(true);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Show the Up button in the action bar.
//        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        if (findViewById(R.id.project_detail_container) != null) {
            // The detail container view will be present only in the
            // large-screen layouts (res/values-large and
            // res/values-sw600dp). If this view is present, then the
            // activity should be in two-pane mode.
            mTwoPane = true;

            // In two-pane mode, list items should be given the
            // 'activated' state when touched.
            ((ProjectLibraryListFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.project_list))
                    .setActivateOnItemClick(true);

        }

        handleIntent(getIntent());

        // TODO: If exposing deep links into your app, handle intents here.
    }

    public void onResume() {
        super.onResume();
        reload(false);
    }

    /**
     * Callback method from {@link ProjectLibraryListFragment.Callbacks}
     * indicating that the item with the given ID was selected.
     */
    @Override
    public void onItemSelected(String id) {
        if (mTwoPane) {
            // In two-pane mode, show the detail view in this activity by
            // adding or replacing the detail fragment using a
            // fragment transaction.
//            Bundle arguments = new Bundle();
            Bundle arguments = getIntent().getExtras();
            arguments.putString(ProjectLibraryDetailFragment.ARG_ITEM_ID, id);
            ProjectLibraryDetailFragment fragment = new ProjectLibraryDetailFragment();
            if(arguments != null) {
                fragment.setArguments(arguments);
            }
            getFragmentManager().beginTransaction().replace(R.id.project_detail_container, fragment).commit();

        } else {
            // In single-pane mode, simply start the detail activity
            // for the selected item ID.
            Intent detailIntent = new Intent(this, ProjectLibraryDetailActivity.class);
            Bundle arguments = getIntent().getExtras();
            if(arguments != null) {
                detailIntent.putExtras(arguments);
            }
            detailIntent.putExtra(ProjectLibraryDetailFragment.ARG_ITEM_ID, id);
            startActivity(detailIntent);
        }
    }

    @Override
    public void onEmptyDraftsList() {
        ProjectLibraryDetailFragment fragment = (ProjectLibraryDetailFragment)getFragmentManager().findFragmentById(R.id.project_detail_container);
        fragment.hideDraftsTab();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_project_library, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if(LibraryTempData.getShowNewProjects()) {
            menu.findItem(R.id.action_show_new).setVisible(false);
            menu.findItem(R.id.action_download_all).setVisible(true);
        } else{
            menu.findItem(R.id.action_show_new).setVisible(true);
            menu.findItem(R.id.action_download_all).setVisible(false);
        }
        if(LibraryTempData.getShowProjectUpdates()) {
            menu.findItem(R.id.action_show_updates).setVisible(false);
            menu.findItem(R.id.action_update_all).setVisible(true);
            if(LibraryTempData.getEnableEditing()) {
                menu.findItem(R.id.action_edit_projects).setVisible(false);
                menu.findItem(R.id.action_cancel_edit_projects).setVisible(true);
            } else {
                menu.findItem(R.id.action_edit_projects).setVisible(true);
                menu.findItem(R.id.action_cancel_edit_projects).setVisible(false);
            }
        } else {
            menu.findItem(R.id.action_show_updates).setVisible(true);
            menu.findItem(R.id.action_update_all).setVisible(false);
            menu.findItem(R.id.action_edit_projects).setVisible(false);
            menu.findItem(R.id.action_cancel_edit_projects).setVisible(false);
        }

        // hook up the search
        SearchManager searchManager = (SearchManager)getSystemService(Context.SEARCH_SERVICE);
        final MenuItem searchMenuItem = menu.findItem(R.id.action_search);
        final SearchView searchViewAction = (SearchView) MenuItemCompat.getActionView(searchMenuItem);
//        searchViewAction.setOnQueryTextFocusChangeListener(new View.OnFocusChangeListener() {
//            @Override
//            public void onFocusChange(View view, boolean b) {
//                if(searchViewAction.getQuery().toString().isEmpty()) {
//                    search("");
//                }
//            }
//        });
        searchViewAction.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                return true;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                // clear the search
//                if(searchMenuItem.isActionViewExpanded() && !TextUtils.isEmpty(s)) {
//                    search(s);
//                    return true;
//                }
                search(s);
                return true;
            }
        });
        searchViewAction.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_show_new:
                LibraryTempData.setShowProjectUpdates(false);
                LibraryTempData.setShowNewProjects(true);
                reload(true);
                return true;
            case R.id.action_show_updates:
                LibraryTempData.setShowProjectUpdates(true);
                LibraryTempData.setShowNewProjects(false);
                reload(true);
                return true;
            case R.id.action_update_all:
                mConfirmDialog = new AlertDialog.Builder(this)
                        .setTitle(R.string.action_update_all)
                        .setMessage(R.string.download_all_confirmation)
                        .setIcon(R.drawable.ic_update_small)
                        .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                // TODO: begin update
                            }
                        })
                        .setNegativeButton(R.string.no, null)
                        .show();
                return true;
            case R.id.action_download_all:
                mConfirmDialog = new AlertDialog.Builder(this)
                        .setTitle(R.string.action_download_all)
                        .setMessage(R.string.download_all_confirmation)
                        .setIcon(R.drawable.ic_download_small)
                        .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                // TODO: begin download
                            }
                        })
                        .setNegativeButton(R.string.no, null)
                        .show();
                return true;
            case R.id.action_edit_projects:
                LibraryTempData.setEnableEditing(true);
                reload(false);
                return true;
            case R.id.action_cancel_edit_projects:
                LibraryTempData.setEnableEditing(false);
                reload(false);
                return true;
            case R.id.action_search:
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void reload(boolean scrollToTop) {
        invalidateOptionsMenu();
        if(mTwoPane) {
            // remove details
            if(getFragmentManager().findFragmentById(R.id.project_detail_container) != null) {
                getFragmentManager().beginTransaction().remove(getFragmentManager().findFragmentById(R.id.project_detail_container)).commit();
            }
        }
        // reload list
        ProjectLibraryListFragment listFragment = ((ProjectLibraryListFragment) getSupportFragmentManager().findFragmentById(R.id.project_list));
        listFragment.reload(scrollToTop);
    }

    public void onDestroy() {
        if(mConfirmDialog != null) {
            mConfirmDialog.dismiss();
        }
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        handleIntent(intent);
    }

    /**
     * Handles the search
     * @param intent
     */
    private void handleIntent(Intent intent) {
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String query = intent.getStringExtra(SearchManager.QUERY);
            search(query);
        }
    }

    /**
     * Performs the search
     * @param query
     */
    private void search(String query) {
        ProjectLibraryListFragment listFragment = ((ProjectLibraryListFragment) getSupportFragmentManager().findFragmentById(R.id.project_list));
        listFragment.filter(query);
    }

    @Override
    public void refreshUI() {
        reload(false);
    }
}