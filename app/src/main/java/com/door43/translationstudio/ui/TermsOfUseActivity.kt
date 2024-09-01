package com.door43.translationstudio.ui

import android.content.Intent
import android.os.Bundle
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.databinding.ActivityTermsBinding
import com.door43.translationstudio.tasks.LogoutTask
import com.door43.translationstudio.ui.home.HomeActivity
import com.door43.translationstudio.ui.legal.LegalDocumentActivity
import dagger.hilt.android.AndroidEntryPoint
import org.unfoldingword.tools.taskmanager.ManagedTask
import org.unfoldingword.tools.taskmanager.TaskManager
import javax.inject.Inject

/**
 * This activity checks if the user has accepted the terms of use before continuing to load the app
 */
@AndroidEntryPoint
class TermsOfUseActivity : BaseActivity(), ManagedTask.OnFinishedListener {
    @Inject lateinit var profile: Profile

    private lateinit var binding: ActivityTermsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val termsVersion = resources.getInteger(R.integer.terms_of_use_version)

        if (!profile.loggedIn) {
            finish()
            return
        }

        if (termsVersion == profile.termsOfUseLastAccepted) {
            // skip terms if already accepted
            startMainActivity()
        } else {
            binding = ActivityTermsBinding.inflate(layoutInflater)
            setContentView(binding.root)

            with(binding) {
                rejectTermsBtn.setOnClickListener { // log out
                    val user = profile.gogsUser
                    val task = LogoutTask(user)
                    TaskManager.addTask(task, LogoutTask.TASK_ID)
                    task.addOnFinishedListener(this@TermsOfUseActivity)
                    profile.logout()

                    // return to login
                    val intent = Intent(
                        this@TermsOfUseActivity,
                        ProfileActivity::class.java
                    )
                    startActivity(intent)
                    finish()
                }
                acceptTermsBtn.setOnClickListener {
                    profile.termsOfUseLastAccepted = termsVersion
                    startMainActivity()
                }
                licenseBtn.setOnClickListener {
                    showLicenseDialog(R.string.license_pdf)
                }
                translationGuidelinesBtn.setOnClickListener {
                    showLicenseDialog(R.string.translation_guidlines)
                }
                statementOfFaithBtn.setOnClickListener {
                    showLicenseDialog(R.string.statement_of_faith)
                }
            }
        }
    }

    /**
     * Continues to the splash screen where local resources will be loaded
     */
    private fun startMainActivity() {
        val intent = Intent(this, HomeActivity::class.java)
        startActivity(intent)
        finish()
    }

    /**
     * Displays a license dialog with the given resource as the text
     * @param stringResource the string resource to display in the dialog.
     */
    private fun showLicenseDialog(stringResource: Int) {
        val intent = Intent(this, LegalDocumentActivity::class.java)
        intent.putExtra(LegalDocumentActivity.ARG_RESOURCE, stringResource)
        startActivity(intent)
    }

    override fun onTaskFinished(task: ManagedTask) {
        TaskManager.clearTask(task)
    }
}
