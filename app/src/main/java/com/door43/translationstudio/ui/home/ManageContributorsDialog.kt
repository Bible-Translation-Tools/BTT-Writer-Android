package com.door43.translationstudio.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import com.door43.translationstudio.R
import com.door43.translationstudio.core.NativeSpeaker
import com.door43.translationstudio.core.Profile
import com.door43.translationstudio.core.TargetTranslation
import com.door43.translationstudio.core.Translator
import com.door43.translationstudio.databinding.FragmentContributorsBinding
import com.door43.translationstudio.ui.ContributorsAdapter
import com.door43.translationstudio.ui.dialogs.ContributorDialog
import org.koin.android.ext.android.inject
import kotlin.getValue

/**
 * Created by joel on 2/22/2016.
 */
class ManageContributorsDialog : DialogFragment(), ContributorsAdapter.OnClickListener {
    val translator: Translator by inject()
    val profile: Profile by inject()

    interface ContributorEventListener {
        fun onDismiss()
    }

    private lateinit var targetTranslation: TargetTranslation
    private val adapter by lazy { ContributorsAdapter() }
    private var onNativeSpeakerDialogClick: View.OnClickListener? = null
    private var eventListener: ContributorEventListener? = null

    private var _binding: FragmentContributorsBinding? = null
    val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, 0)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentContributorsBinding.inflate(inflater, container, false)

        val args = requireArguments()
        val targetTranslationId = args.getString(EXTRA_TARGET_TRANSLATION_ID)

        targetTranslation = targetTranslationId?.let {
            translator.getTargetTranslation(it)
        }!!

        // auto add profile
        targetTranslation.addContributor(profile.nativeSpeaker)

        with (binding) {
            adapter.setDisplayNext(false)
            adapter.setContributors(targetTranslation.getContributors())
            adapter.setOnClickListener(this@ManageContributorsDialog)

            recyclerView.layoutManager = LinearLayoutManager(activity)
            recyclerView.itemAnimator = DefaultItemAnimator()
            recyclerView.adapter = adapter
        }

        onNativeSpeakerDialogClick = View.OnClickListener {
            adapter.setContributors(targetTranslation.getContributors())
        }

        // re-attach to dialogs
        val prevEditDialog = parentFragmentManager.findFragmentByTag("edit-native-speaker")
        if (prevEditDialog != null) {
            (prevEditDialog as ContributorDialog).setOnClickListener(onNativeSpeakerDialogClick)
        }
        val prevAddDialog = parentFragmentManager.findFragmentByTag("add-native-speaker")
        if (prevAddDialog != null) {
            (prevAddDialog as ContributorDialog).setOnClickListener(onNativeSpeakerDialogClick)
        }

        return binding.root
    }

    override fun onEditNativeSpeaker(speaker: NativeSpeaker) {
        val ft = parentFragmentManager.beginTransaction()
        val prev = parentFragmentManager.findFragmentByTag("edit-native-speaker")
        if (prev != null) {
            ft.remove(prev)
        }
        ft.addToBackStack(null)

        val dialog = ContributorDialog()
        val args = Bundle()
        args.putString(ContributorDialog.ARG_TARGET_TRANSLATION, targetTranslation.id)
        args.putString(ContributorDialog.ARG_NATIVE_SPEAKER, speaker.name)
        dialog.arguments = args
        dialog.setOnClickListener(onNativeSpeakerDialogClick)
        dialog.show(ft, "edit-native-speaker")
    }

    override fun onClickAddNativeSpeaker() {
        showAddNativeSpeakerDialog()
    }

    override fun onClickNext() {
    }

    override fun onClickPrivacyNotice() {
        showPrivacyNotice()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        eventListener?.onDismiss()
        eventListener = null
        onNativeSpeakerDialogClick = null
    }

    fun setEventListener(listener: ContributorEventListener) {
        eventListener = listener
    }

    private fun showAddNativeSpeakerDialog() {
        val ft = parentFragmentManager.beginTransaction()
        val prev = parentFragmentManager.findFragmentByTag("add-native-speaker")
        if (prev != null) {
            ft.remove(prev)
        }
        ft.addToBackStack(null)

        val dialog = ContributorDialog()
        val args = Bundle()
        args.putString(ContributorDialog.ARG_TARGET_TRANSLATION, targetTranslation.id)
        dialog.arguments = args
        dialog.setOnClickListener(onNativeSpeakerDialogClick)
        dialog.show(ft, "add-native-speaker")
    }

    /**
     * Displays the privacy notice
     */
    private fun showPrivacyNotice() {
        AlertDialog.Builder(requireActivity(), R.style.AppTheme_Dialog)
            .setTitle(R.string.privacy_notice)
            .setIcon(R.drawable.ic_info_secondary_24dp)
            .setMessage(R.string.publishing_privacy_notice)
            .setPositiveButton(R.string.dismiss, null)
            .show()
    }

    companion object {
        const val EXTRA_TARGET_TRANSLATION_ID: String = "target_translation_id"
    }
}
