package com.door43.translationstudio.ui.publish

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Button
import androidx.activity.addCallback
import androidx.activity.viewModels
import com.door43.translationstudio.R
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.databinding.ActivityPublishBinding
import com.door43.translationstudio.ui.BaseActivity
import com.door43.translationstudio.ui.dialogs.BackupDialog
import com.door43.translationstudio.ui.translate.TargetTranslationActivity
import com.door43.translationstudio.ui.viewmodels.TargetTranslationViewModel
import com.door43.widget.ViewUtil
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import org.unfoldingword.tools.logger.Logger
import java.security.InvalidParameterException

@AndroidEntryPoint
class PublishActivity : BaseActivity(), PublishStepFragment.OnEventListener {
    private var fragment: PublishStepFragment? = null
    private var currentStep = 0
    private var publishFinished = false
    private var callingActivity = 0

    private val viewModel: TargetTranslationViewModel by viewModels()
    private lateinit var binding: ActivityPublishBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPublishBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // validate parameters
        val args = checkNotNull(intent.extras)
        val targetTranslationId = args.getString(Translator.EXTRA_TARGET_TRANSLATION_ID, null)
        val translation = viewModel.getTargetTranslation(targetTranslationId)
        if (translation == null) {
            Logger.e(
                PublishActivity::class.java.simpleName,
                "A valid target translation id is required. Received $targetTranslationId but the translation could not be found"
            )
            finish()
            return
        }

        // identify calling activity
        callingActivity = args.getInt(EXTRA_CALLING_ACTIVITY, 0)
        if (callingActivity == 0) {
            Logger.e(
                PublishActivity::class.java.simpleName,
                "you must specify the calling activity"
            )
            finish()
            return
        }

        // stage indicators
        if (savedInstanceState != null) {
            currentStep = savedInstanceState.getInt(STATE_STEP, 0)
            publishFinished = savedInstanceState.getBoolean(STATE_PUBLISH_FINISHED, false)
        }

        // inject fragments
        if (findViewById<View?>(R.id.fragment_container) != null) {
            if (savedInstanceState != null) {
                fragment = supportFragmentManager.findFragmentById(
                    R.id.fragment_container
                ) as? PublishStepFragment
            } else {
                fragment = ValidationFragment()
                var sourceTranslationId = viewModel.getSelectedSourceTranslationId()

                if (sourceTranslationId == null) {
                    // use the default target translation if they have not chosen one.
                    sourceTranslationId = viewModel.getDefaultSourceTranslation()
                }
                if (sourceTranslationId != null) {
                    args.putSerializable(
                        PublishStepFragment.ARG_SOURCE_TRANSLATION_ID,
                        sourceTranslationId
                    )
                    fragment?.setArguments(args)
                    supportFragmentManager
                        .beginTransaction()
                        .add(R.id.fragment_container, fragment!!)
                        .commit()
                    // TODO: animate
                } else {
                    // the user must choose a source translation before they can publish
                    val snack = Snackbar.make(
                        findViewById(android.R.id.content),
                        R.string.choose_source_translations,
                        Snackbar.LENGTH_LONG
                    )
                    ViewUtil.setSnackBarTextColor(
                        snack,
                        resources.getColor(R.color.light_primary_text)
                    )
                    snack.show()
                    finish()
                }
            }
        }

        selectButtonForCurrentStep()

        // add step button listeners
        binding.validationButton.setOnClickListener {
            goToStep(STEP_VALIDATE, false)
        }

        binding.profileButton.setOnClickListener {
            goToStep(STEP_PROFILE, false)
        }

        binding.uploadButton.setOnClickListener {
            showBackupDialog()
        }

        onBackPressedDispatcher.addCallback {
            onBackPressedHandler()
        }
    }

    /**
     * display Backup dialog
     */
    private fun showBackupDialog() {
        val backupFt = supportFragmentManager.beginTransaction()
        val backupPrev = supportFragmentManager.findFragmentByTag(BackupDialog.TAG)
        if (backupPrev != null) {
            backupFt.remove(backupPrev)
        }
        backupFt.addToBackStack(null)

        val backupDialog = BackupDialog()
        val args = Bundle()
        args.putString(BackupDialog.ARG_TARGET_TRANSLATION_ID, viewModel.targetTranslation.id)
        backupDialog.arguments = args
        backupDialog.show(backupFt, BackupDialog.TAG)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            if (callingActivity == ACTIVITY_TRANSLATION) {
                // TRICKY: the translation activity is finished after opening the publish activity
                // because we may have to go back and forth and don't want to fill up the stack
                val intent = Intent(this, TargetTranslationActivity::class.java)
                val args = Bundle()
                args.putString(
                    Translator.EXTRA_TARGET_TRANSLATION_ID,
                    viewModel.targetTranslation.id
                )
                intent.putExtras(args)
                startActivity(intent)
            }
            finish()
        }
        return true
    }

    private fun onBackPressedHandler() {
        // TRICKY: the translation activity is finished after opening the publish activity
        // because we may have to go back and forth and don't want to fill up the stack
        if (callingActivity == ACTIVITY_TRANSLATION) {
            val intent = Intent(this, TargetTranslationActivity::class.java)
            val args = Bundle()
            args.putString(Translator.EXTRA_TARGET_TRANSLATION_ID, viewModel.targetTranslation.id)
            intent.putExtras(args)
            startActivity(intent)
        }
        finish()
    }


    override fun nextStep() {
        goToStep(currentStep + 1, true)
    }

    override fun finishPublishing() {
        publishFinished = true
    }

    /**
     * Moves to the a stage in the publish process
     * @param step
     * @param force forces the step to be opened even if it has never been opened before
     */
    private fun goToStep(step: Int, force: Boolean) {
        if (step == currentStep) {
            return
        }

        if (step > STEP_PROFILE) { // if we are ready to upload
            currentStep = STEP_PROFILE
            showBackupDialog()
            return
        } else {
            currentStep = step
        }

        when (currentStep) {
            STEP_PROFILE -> fragment = TranslatorsFragment()
            STEP_VALIDATE -> {
                currentStep = STEP_VALIDATE
                fragment = ValidationFragment()
            }
            else -> {
                currentStep = STEP_VALIDATE
                fragment = ValidationFragment()
            }
        }
        selectButtonForCurrentStep()

        val args = checkNotNull(intent.extras)
        var sourceTranslationId = viewModel.getSelectedSourceTranslationId()
        // TRICKY: if the user has not chosen a source translation (this is an empty translation) the id will be null
        if (sourceTranslationId == null) {
            sourceTranslationId = viewModel.getDefaultSourceTranslation()
        }
        if (sourceTranslationId != null) {
            args.putSerializable(PublishStepFragment.ARG_SOURCE_TRANSLATION_ID, sourceTranslationId)
            args.putBoolean(PublishStepFragment.ARG_PUBLISH_FINISHED, publishFinished)
            fragment!!.arguments = args
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.fragment_container, fragment!!)
                .commit()
            // TODO: animate
        }
    }

    /**
     * select the button for current state
     */
    private fun selectButtonForCurrentStep() {
        setButtonSelectionState(binding.validationButton, currentStep == STEP_VALIDATE)
        setButtonSelectionState(binding.profileButton, currentStep == STEP_PROFILE)
    }

    /**
     * show which fragment is selected by changing background color
     * @param button
     * @param selected
     */
    private fun setButtonSelectionState(button: Button, selected: Boolean) {
        var newResource = R.color.accent
        if (selected) {
            newResource = R.color.accent_dark
        }
        button.setBackgroundResource(newResource)
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_STEP, currentStep)
        outState.putBoolean(STATE_PUBLISH_FINISHED, publishFinished)

        super.onSaveInstanceState(outState)
    }

    companion object {
        const val STEP_VALIDATE: Int = 0
        const val STEP_PROFILE: Int = 1
        const val EXTRA_TARGET_TRANSLATION_ID: String = "extra_target_translation_id"
        const val EXTRA_CALLING_ACTIVITY: String = "extra_calling_activity"
        private const val STATE_STEP = "state_step"
        private const val STATE_PUBLISH_FINISHED = "state_publish_finished"
        const val ACTIVITY_HOME: Int = 1001
        const val ACTIVITY_TRANSLATION: Int = 1002
    }
}
