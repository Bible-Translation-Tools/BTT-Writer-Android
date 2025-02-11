package com.door43.translationstudio.ui.publish

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
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
import com.door43.widget.ViewUtil
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Created by joel on 9/20/2015.
 */
@AndroidEntryPoint
class TranslatorsFragment : PublishStepFragment(), ContributorsAdapter.OnClickListener {
    @Inject lateinit var translator: Translator
    @Inject lateinit var profile: Profile

    private lateinit var targetTranslation: TargetTranslation
    private val adapter by lazy { ContributorsAdapter() }
    private var onNativeSpeakerDialogClick: View.OnClickListener? = null

    private var _binding: FragmentContributorsBinding? = null
    val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentContributorsBinding.inflate(inflater, container, false)

        val args = requireArguments()
        val targetTranslationId = args.getString(PublishActivity.EXTRA_TARGET_TRANSLATION_ID)
        targetTranslation = translator.getTargetTranslation(targetTranslationId)!!

        // auto add profile
        targetTranslation.addContributor(profile.nativeSpeaker)

        with (binding) {
            adapter.setContributors(targetTranslation.contributors)
            adapter.setOnClickListener(this@TranslatorsFragment)

            recyclerView.layoutManager = LinearLayoutManager(activity)
            recyclerView.itemAnimator = DefaultItemAnimator()
            recyclerView.adapter = adapter
        }

        onNativeSpeakerDialogClick = View.OnClickListener {
            adapter.setContributors(targetTranslation.contributors)
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
        if (targetTranslation.contributors.isNotEmpty()) {
            listener?.nextStep()
        } else {
            val snack = Snackbar.make(
                requireActivity().findViewById(android.R.id.content),
                R.string.need_translator_notice,
                Snackbar.LENGTH_LONG
            )
            snack.setAction(R.string.add_contributor) { showAddNativeSpeakerDialog() }
            ViewUtil.setSnackBarTextColor(snack, resources.getColor(R.color.light_primary_text))
            snack.show()
        }
    }

    override fun onClickPrivacyNotice() {
        showPrivacyNotice()
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
