package com.door43.translationstudio.ui.dialogs

import android.content.Intent
import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.door43.translationstudio.R
import com.door43.translationstudio.core.NativeSpeaker
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.databinding.DialogNativeSpeakerBinding
import com.door43.translationstudio.ui.legal.LegalDocumentActivity
import com.door43.widget.ViewUtil
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import java.security.InvalidParameterException
import javax.inject.Inject

/**
 * Created by joel on 2/19/2016.
 */
@AndroidEntryPoint
class ContributorDialog : DialogFragment() {
    @Inject lateinit var translator: Translator

    private var targetTranslation: TargetTranslation? = null
    private var nativeSpeaker: NativeSpeaker? = null
    private var listener: View.OnClickListener? = null

    private var _binding: DialogNativeSpeakerBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        dialog?.requestWindowFeature(Window.FEATURE_NO_TITLE)
        _binding = DialogNativeSpeakerBinding.inflate(inflater, container, false)

        // load target
        val args = arguments
        if (args != null) {
            val nativeSpeakerName = args.getString(ARG_NATIVE_SPEAKER, null)
            val targetTranslationId = args.getString(ARG_TARGET_TRANSLATION, null)
            targetTranslation = translator.getTargetTranslation(targetTranslationId)
            if (nativeSpeakerName != null && targetTranslation != null) {
                nativeSpeaker = targetTranslation!!.getContributor(nativeSpeakerName)
            }
        }
        if (targetTranslation == null) {
            throw InvalidParameterException("Missing the target translation parameter")
        }

        with(binding) {
            if (nativeSpeaker != null) {
                name.setText(nativeSpeaker!!.name)
                title.setText(R.string.edit_contributor)
                deleteButton.visibility = View.VISIBLE
                agreementCheck.isEnabled = false
                agreementCheck.isChecked = true
                licenseGroup.visibility = View.GONE
            } else {
                title.setText(R.string.add_contributor)
                deleteButton.visibility = View.GONE
                agreementCheck.isEnabled = true
                agreementCheck.isChecked = false
                licenseGroup.visibility = View.VISIBLE
            }

            licenseAgreementBtn.setOnClickListener {
                val intent = Intent(activity, LegalDocumentActivity::class.java)
                intent.putExtra(LegalDocumentActivity.ARG_RESOURCE, R.string.license_pdf)
                startActivity(intent)
            }

            statementOfFaithBtn.setOnClickListener {
                val intent = Intent(activity, LegalDocumentActivity::class.java)
                intent.putExtra(LegalDocumentActivity.ARG_RESOURCE, R.string.statement_of_faith)
                startActivity(intent)
            }

            translationGuidelinesBtn.setOnClickListener {
                val intent = Intent(activity, LegalDocumentActivity::class.java)
                intent.putExtra(LegalDocumentActivity.ARG_RESOURCE, R.string.translation_guidlines)
                startActivity(intent)
            }

            cancelButton.setOnClickListener {
                dismiss()
            }

            deleteButton.setOnClickListener {
                AlertDialog.Builder(requireActivity(), R.style.AppTheme_Dialog)
                    .setTitle(R.string.delete_translator_title)
                    .setMessage(Html.fromHtml(getString(R.string.confirm_delete_translator)))
                    .setPositiveButton(
                        R.string.confirm
                    ) { _, _ ->
                        targetTranslation!!.removeContributor(nativeSpeaker)
                        if (listener != null) {
                            listener!!.onClick(deleteButton)
                        }
                        dismiss()
                    }
                    .setNegativeButton(R.string.title_cancel, null)
                    .show()
            }

            saveButton.setOnClickListener {
                val name = name.text.toString()
                if (agreementCheck.isChecked && name.isNotEmpty()) {
                    val duplicate = targetTranslation!!.getContributor(name)
                    if (duplicate != null) {
                        if (nativeSpeaker != null && nativeSpeaker == duplicate) {
                            // no change
                            dismiss()
                        } else {
                            val snack = Snackbar.make(
                                saveButton,
                                R.string.duplicate_native_speaker,
                                Snackbar.LENGTH_SHORT
                            )
                            ViewUtil.setSnackBarTextColor(
                                snack,
                                resources.getColor(R.color.light_primary_text)
                            )
                            snack.show()
                        }
                    } else {
                        targetTranslation!!.removeContributor(nativeSpeaker) // remove old name
                        targetTranslation!!.addContributor(NativeSpeaker(name))
                        listener?.onClick(saveButton)
                        dismiss()
                    }
                } else {
                    val snack = Snackbar.make(saveButton, R.string.complete_required_fields, Snackbar.LENGTH_SHORT)
                    ViewUtil.setSnackBarTextColor(snack, resources.getColor(R.color.light_primary_text))
                    snack.show()
                }
            }
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * Sets the listener to be called when the dialog is submitted
     * @param listener
     */
    fun setOnClickListener(listener: View.OnClickListener?) {
        this.listener = listener
    }

    companion object {
        const val ARG_NATIVE_SPEAKER: String = "native_speaker_name"
        const val ARG_TARGET_TRANSLATION: String = "target_translation_id"
    }
}
