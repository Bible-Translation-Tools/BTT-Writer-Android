package com.door43.translationstudio.ui.devtools

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.widget.SearchView
import com.door43.translationstudio.R
import com.door43.translationstudio.databinding.ActivityEditLibraryBinding
import com.door43.translationstudio.ui.BaseActivity
import com.door43.translationstudio.ui.Searchable
import com.door43.translationstudio.ui.devtools.library.LibraryRowEditFragment
import com.door43.translationstudio.ui.devtools.library.LibraryRowsFragment
import com.door43.translationstudio.ui.devtools.library.LibraryTable
import com.door43.translationstudio.ui.devtools.library.LibraryTablesFragment
import com.door43.translationstudio.ui.dialogs.ProgressHelper
import com.door43.translationstudio.ui.viewmodels.EditLibraryViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class EditLibraryActivity : BaseActivity(),
    LibraryTablesFragment.Listener,
    LibraryRowsFragment.Listener {

    private lateinit var binding: ActivityEditLibraryBinding
    private val viewModel: EditLibraryViewModel by viewModels()
    private var progressDialog: ProgressHelper.ProgressDialog? = null

    private val currentFragment
        get() = supportFragmentManager.findFragmentById(R.id.fragment_container)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditLibraryBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .add(R.id.fragment_container, LibraryTablesFragment())
                .commit()
        }
        supportFragmentManager.addOnBackStackChangedListener { invalidateOptionsMenu() }

        viewModel.progress.observe(this) {
            if (it != null) {
                progressDialog?.show()
                progressDialog?.setProgress(it.progress)
                progressDialog?.setMessage(it.message)
                progressDialog?.setMax(it.max)
            } else {
                progressDialog?.dismiss()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        progressDialog = ProgressHelper.newInstance(
            supportFragmentManager,
            R.string.edit_library,
            false
        )
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_edit_library, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        super.onPrepareOptionsMenu(menu)
        val searchMenuItem = menu.findItem(R.id.action_search)
        val searchable = currentFragment as? Searchable
        searchMenuItem.isVisible = searchable != null
        val searchView = searchMenuItem.actionView as SearchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean = true

            override fun onQueryTextChange(newText: String): Boolean {
                (currentFragment as? Searchable)?.onSearchQuery(newText)
                return true
            }
        })
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_search -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onTableSelected(table: LibraryTable) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, LibraryRowsFragment.newInstance(table))
            .addToBackStack(null)
            .commit()
    }

    override fun onRowSelected(table: LibraryTable, rowId: String) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, LibraryRowEditFragment.newInstance(table, rowId))
            .addToBackStack(null)
            .commit()
    }

    override fun onSupportNavigateUp(): Boolean {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack()
        } else {
            finish()
        }
        return true
    }
}
