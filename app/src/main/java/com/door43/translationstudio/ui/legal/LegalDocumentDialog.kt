package com.door43.translationstudio.ui.legal

import android.content.DialogInterface
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.DialogFragment
import com.door43.translationstudio.databinding.DialogLicenseBinding

/**
 * Created by joel on 10/23/2015.
 */
class LegalDocumentDialog : DialogFragment() {
    private var dismissListener: DialogInterface.OnDismissListener? = null

    private var _binding: DialogLicenseBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val style = STYLE_NO_TITLE
        val theme = 0
        setStyle(style, theme)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val args = arguments
        var resourceId = 0
        if (args != null) {
            resourceId = args.getInt(LegalDocumentActivity.ARG_RESOURCE, 0)
        }
        if (resourceId == 0) {
            dismiss()
        }

        // inflate the views
        _binding = DialogLicenseBinding.inflate(inflater, container, false)

        // validate the arguments
        if (resourceId == 0) {
            dismiss()
        } else {
            // load the string
            val licenseString = resources.getString(resourceId)
            binding.licenseText.text = Html.fromHtml(licenseString)
            binding.licenseText.movementMethod = LinkMovementMethod.getInstance()
        }

        // enable button
        binding.dismissLicenseBtn.setOnClickListener { this@LegalDocumentDialog.dismiss() }
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        // safety check
        if (dialog == null) {
            return
        }
        dialog?.window?.setLayout(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )
        // scroll to the top
        binding.scrollView.smoothScrollTo(0, 0)
    }

    override fun onDismiss(dialogInterface: DialogInterface) {
        dismissListener?.onDismiss(dialogInterface)
        super.onDismiss(dialogInterface)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun setOnDismissListener(listener: DialogInterface.OnDismissListener?) {
        dismissListener = listener
    }
}
