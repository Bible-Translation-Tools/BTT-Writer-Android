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
import com.door43.translationstudio.App.Companion.getTranslator
import com.door43.translationstudio.R
import com.door43.translationstudio.core.NativeSpeaker
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.databinding.DialogNativeSpeakerBinding
import com.door43.translationstudio.ui.legal.LegalDocumentActivity
import com.door43.widget.ViewUtil
import com.google.android.material.snackbar.Snackbar
import java.security.InvalidParameterException

/**
 * Created by joel on 2/19/2016.
 */
class ContributorDialog : DialogFragment() {
    private var mTargetTranslation: TargetTranslation? = null
    private var mNativeSpeaker: NativeSpeaker? = null
    private var mListener: View.OnClickListener? = null

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
            mTargetTranslation = getTranslator().getTargetTranslation(targetTranslationId)
            if (nativeSpeakerName != null && mTargetTranslation != null) {
                mNativeSpeaker = mTargetTranslation!!.getContributor(nativeSpeakerName)
            }
        }
        if (mTargetTranslation == null) {
            throw InvalidParameterException("Missing the target translation parameter")
        }

        with(binding) {
            if (mNativeSpeaker != null) {
                name.setText(mNativeSpeaker!!.name)
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

            deleteButton.setOnClickListener {
                AlertDialog.Builder(requireActivity(), R.style.AppTheme_Dialog)
                    .setTitle(R.string.delete_translator_title)
                    .setMessage(Html.fromHtml(getString(R.string.confirm_delete_translator)))
                    .setPositiveButton(
                        R.string.confirm
                    ) { _, _ ->
                        mTargetTranslation!!.removeContributor(mNativeSpeaker)
                        if (mListener != null) {
                            mListener!!.onClick(deleteButton)
                        }
                        dismiss()
                    }
                    .setNegativeButton(R.string.title_cancel, null)
                    .show()
            }

            saveButton.setOnClickListener {
                val name = name.text.toString()
                if (agreementCheck.isChecked && name.isNotEmpty()) {
                    val duplicate = mTargetTranslation!!.getContributor(name)
                    if (duplicate != null) {
                        if (mNativeSpeaker != null && mNativeSpeaker == duplicate) {
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
                        mTargetTranslation!!.removeContributor(mNativeSpeaker) // remove old name
                        mTargetTranslation!!.addContributor(NativeSpeaker(name))
                        mListener?.onClick(saveButton)
                        dismiss()
                    }
                } else {
                    val snack =
                        Snackbar.make(saveButton, R.string.complete_required_fields, Snackbar.LENGTH_SHORT)
                    ViewUtil.setSnackBarTextColor(snack, resources.getColor(R.color.light_primary_text))
                    snack.show()
                }
            }
        }

        return binding.root
    }

    /**
     * Sets the listener to be called when the dialog is submitted
     * @param listener
     */
    fun setOnClickListener(listener: View.OnClickListener?) {
        mListener = listener
    }

    companion object {
        const val ARG_NATIVE_SPEAKER: String = "native_speaker_name"
        const val ARG_TARGET_TRANSLATION: String = "target_translation_id"
    }
}
