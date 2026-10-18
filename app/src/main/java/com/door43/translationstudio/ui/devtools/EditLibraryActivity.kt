package com.door43.translationstudio.ui.devtools

import android.os.Bundle
import android.view.Menu
import androidx.activity.viewModels
import androidx.appcompat.widget.SearchView
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.BaseActivity
import com.door43.translationstudio.ui.Searchable
import com.door43.translationstudio.ui.dialogs.ProgressHelper
import com.door43.translationstudio.ui.newtranslation.TargetLanguageListFragment
import com.door43.translationstudio.ui.viewmodels.EditLibraryViewModel
import dagger.hilt.android.AndroidEntryPoint
import org.unfoldingword.door43client.models.TargetLanguage

/**
 * Developer tool: pick a target language from the library and edit it.
 */
@AndroidEntryPoint
class EditLibraryActivity : BaseActivity(), TargetLanguageListFragment.OnItemClickListener {

    private val viewModel: EditLibraryViewModel by viewModels()
    private var progressDialog: ProgressHelper.ProgressDialog? = null

    private val currentFragment
        get() = supportFragmentManager.findFragmentById(R.id.fragment_container)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_library)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .add(R.id.fragment_container, TargetLanguageListFragment())
                .commit()
        }
        supportFragmentManager.addOnBackStackChangedListener { invalidateOptionsMenu() }

        viewModel.progress.observe(this) {
            if (it != null) {
                progressDialog?.show()
                progressDialog?.setMessage(it.message)
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
        searchMenuItem.isVisible = currentFragment is Searchable
        (searchMenuItem.actionView as SearchView).setOnQueryTextListener(
            object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String): Boolean = true

                override fun onQueryTextChange(newText: String): Boolean {
                    (currentFragment as? Searchable)?.onSearchQuery(newText)
                    return true
                }
            }
        )
        return true
    }

    override fun onItemClick(targetLanguage: TargetLanguage) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, EditTargetLanguageFragment.newInstance(targetLanguage.slug))
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
