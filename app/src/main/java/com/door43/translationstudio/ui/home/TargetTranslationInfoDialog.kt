package com.door43.translationstudio.ui.home

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.door43.translationstudio.R
import com.door43.translationstudio.core.TranslationType
import com.door43.translationstudio.core.Typography
import com.door43.translationstudio.databinding.DialogTargetTranslationInfoBinding
import com.door43.translationstudio.ui.dialogs.BackupDialog
import com.door43.translationstudio.ui.dialogs.PrintDialog
import com.door43.translationstudio.ui.newtranslation.NewTargetTranslationActivity
import com.door43.translationstudio.ui.publish.PublishActivity
import com.door43.translationstudio.ui.viewmodels.HomeViewModel
import dagger.hilt.android.AndroidEntryPoint
import org.unfoldingword.tools.logger.Logger
import javax.inject.Inject
import kotlin.math.min

/**
 * Displays detailed information about a target translation
 */
@AndroidEntryPoint
class TargetTranslationInfoDialog : DialogFragment() {
    @Inject lateinit var typography: Typography
    private var targetTranslation: TranslationItem? = null

    private var _binding: DialogTargetTranslationInfoBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HomeViewModel by activityViewModels()

    private lateinit var changeTargetLanguageLauncher: ActivityResultLauncher<Intent>

    @SuppressLint("SetTextI18n")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        dialog?.requestWindowFeature(Window.FEATURE_NO_TITLE)
        _binding = DialogTargetTranslationInfoBinding.inflate(inflater, container, false)

        val args = arguments
        if (args == null || !args.containsKey(ARG_TARGET_TRANSLATION_ID)) {
            dismiss()
        } else {
            val targetTranslationId = args.getString(ARG_TARGET_TRANSLATION_ID, null)
            targetTranslation = viewModel.findTranslationItem(targetTranslationId)
            if (targetTranslation == null) {
                Logger.w(
                    "TargetTranslationInfoDialog",
                    "Unknown target translation $targetTranslationId"
                )
                dismiss()
                return binding.root
            }
        }

        changeTargetLanguageLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result: ActivityResult ->
            onChangeTargetLanguageRequest(result)
        }

        setupObservers()

        targetTranslation?.let { item ->
            // set typeface for language
            val targetLanguage = item.translation.targetLanguage
            val typeface = typography.getBestFontForLanguage(
                TranslationType.SOURCE,
                targetLanguage.slug,
                targetLanguage.direction
            )

            viewModel.getTranslationProgress(item.translation)

            with(binding) {
                title.setTypeface(typeface, Typeface.NORMAL)
                languageTitle.setTypeface(typeface, Typeface.NORMAL)

                title.text = item.project.name + " - " + item.translation.targetLanguageName
                projectTitle.text = item.formattedProjectName
                languageTitle.text =
                    item.translation.targetLanguageName + " (" + item.translation.targetLanguageId + ")"

                // calculate translation progress
                progress.text = ""

                // list translators
                translators.text = ""
                val translatorsList = getTranslatorNames()
                if (translatorsList != null) {
                    translators.text = translatorsList
                }

                changeLanguage.setOnClickListener {
                    val intent = Intent(activity, NewTargetTranslationActivity::class.java)
                    intent.putExtra(
                        NewTargetTranslationActivity.EXTRA_TARGET_TRANSLATION_ID,
                        item.translation.id
                    )
                    intent.putExtra(
                        NewTargetTranslationActivity.EXTRA_DISABLED_LANGUAGES, arrayOf(
                            item.translation.targetLanguage.slug
                        )
                    )
                    intent.putExtra(
                        NewTargetTranslationActivity.EXTRA_CHANGE_TARGET_LANGUAGE_ONLY,
                        true
                    )
                    changeTargetLanguageLauncher.launch(intent)
                }

                deleteButton.setOnClickListener {
                    AlertDialog.Builder(requireActivity(), R.style.AppTheme_Dialog)
                        .setTitle(R.string.label_delete)
                        .setIcon(R.drawable.ic_delete_secondary_24dp)
                        .setMessage(R.string.confirm_delete_target_translation)
                        .setPositiveButton(R.string.confirm) { _, _ ->
                            try {
                                deleteTargetTranslation(false)
                            } catch (e: Exception) {
                                // If delete failed, try again as orphaned
                                try {
                                    deleteTargetTranslation(true)
                                } catch (ex: Exception) {
                                    throw RuntimeException("Could not delete target translation.", ex)
                                }
                            }
                            dismiss()
                        }
                        .setNegativeButton(R.string.no, null)
                        .show()
                }

                backupButton.setOnClickListener {
                    val backupFt = parentFragmentManager.beginTransaction()
                    val backupPrev = parentFragmentManager.findFragmentByTag(BackupDialog.TAG)
                    if (backupPrev != null) {
                        backupFt.remove(backupPrev)
                    }
                    backupFt.addToBackStack(null)

                    val backupDialog = BackupDialog()
                    val arguments = Bundle()
                    arguments.putString(BackupDialog.ARG_TARGET_TRANSLATION_ID, item.translation.id)
                    backupDialog.arguments = arguments
                    backupDialog.show(backupFt, BackupDialog.TAG)
                }

                publishButton.setOnClickListener {
                    val publishIntent = Intent(activity, PublishActivity::class.java)
                    publishIntent.putExtra(
                        PublishActivity.EXTRA_TARGET_TRANSLATION_ID,
                        item.translation.id
                    )
                    publishIntent.putExtra(
                        PublishActivity.EXTRA_CALLING_ACTIVITY,
                        PublishActivity.ACTIVITY_HOME
                    )
                    startActivity(publishIntent)
                    dismiss() // close dialog so notifications will pass back to HomeActivity
                }

                printButton.setOnClickListener {
                    val printFt = parentFragmentManager.beginTransaction()
                    val printPrev = parentFragmentManager.findFragmentByTag("printDialog")
                    if (printPrev != null) {
                        printFt.remove(printPrev)
                    }
                    printFt.addToBackStack(null)

                    val printDialog = PrintDialog()
                    val printArgs = Bundle()
                    printArgs.putString(PrintDialog.ARG_TARGET_TRANSLATION_ID, item.translation.id)
                    printDialog.arguments = printArgs
                    printDialog.show(printFt, "printDialog")
                }

                contributorsGroup.setOnClickListener {
                    val ft = parentFragmentManager.beginTransaction()
                    val prev = parentFragmentManager.findFragmentByTag("manage-contributors")
                    if (prev != null) {
                        ft.remove(prev)
                    }
                    ft.addToBackStack(null)

                    val dialog = ManageContributorsDialog()
                    val args1 = Bundle()
                    args1.putString(
                        ManageContributorsDialog.EXTRA_TARGET_TRANSLATION_ID,
                        item.translation.id
                    )
                    dialog.arguments = args1
                    dialog.show(ft, "manage-contributors")
                }
            }
        }

        // TODO: re-connect to dialogs
        return binding.root
    }

    @SuppressLint("SetTextI18n")
    private fun setupObservers() {
        viewModel.translationProgress.observe(viewLifecycleOwner) {
            it?.let { progress ->
                val hand = Handler(Looper.getMainLooper())
                hand.post {
                    binding.progress.text = "${Math.round(progress * 100)}%"
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // widen dialog to accommodate more text
        val desiredWidth = 600
        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels
        val density = displayMetrics.density
        val correctedWidth = width / density
        var screenWidthFactor = desiredWidth / correctedWidth
        screenWidthFactor = min(screenWidthFactor.toDouble(), 1.0).toFloat() // sanity check

        dialog?.window?.setLayout(
            (width * screenWidthFactor).toInt(),
            WindowManager.LayoutParams.MATCH_PARENT
        )
    }

    /**
     * returns a concatenated list of names or null if error
     */
    private fun getTranslatorNames(): String? {
        return targetTranslation?.translation?.contributors?.let {
            var listString = ""

            for (i in it.indices) {
                if (listString.isNotEmpty()) {
                    listString += "\n"
                }
                listString += it[i].name
            }

            return listString
        }
    }

    private fun onChangeTargetLanguageRequest(result: ActivityResult) {
        if (NewTargetTranslationActivity.RESULT_MERGE_CONFLICT == result.resultCode) {
            val targetTranslationID =
                result.data!!.getStringExtra(NewTargetTranslationActivity.EXTRA_TARGET_TRANSLATION_ID)
            if (targetTranslationID != null) {
                val activity: Activity? = activity
                if (activity is HomeActivity) {
                    activity.doManualMerge(targetTranslationID)
                }
            }
        }
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    @Throws(Exception::class)
    private fun deleteTargetTranslation(orphaned: Boolean) {
        viewModel.deleteTargetTranslation(targetTranslation!!, orphaned)
    }

    companion object {
        const val ARG_TARGET_TRANSLATION_ID: String = "target_translation_id"
    }
}
