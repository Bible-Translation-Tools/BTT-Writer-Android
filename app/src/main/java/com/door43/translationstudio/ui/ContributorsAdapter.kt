package com.door43.translationstudio.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.door43.translationstudio.R
import com.door43.translationstudio.core.NativeSpeaker
import com.door43.translationstudio.databinding.FragmentPublishNativeSpeakerControlsBinding
import com.door43.translationstudio.databinding.FragmentPublishNativeSpeakerItemBinding
import com.door43.translationstudio.databinding.FragmentPublishNativeSpeakerSecurityNoticeBinding
import com.door43.translationstudio.ui.ContributorsAdapter.GenericViewHolder

/**
 * Created by joel on 2/19/2016.
 */
class ContributorsAdapter : RecyclerView.Adapter<GenericViewHolder>() {
    private val contributors = arrayListOf<NativeSpeaker>()
    private var listener: OnClickListener? = null
    private var displayNext = true

    /**
     * Loads a new set of native speakers
     * @param speakers
     */
    fun setContributors(speakers: List<NativeSpeaker>) {
        this.contributors.clear()
        this.contributors.addAll(speakers)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return when (position) {
            0 -> TYPE_SECURITY_NOTICE
            itemCount - 1 -> TYPE_CONTROLS
            else -> TYPE_SPEAKER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GenericViewHolder {
        when (viewType) {
            TYPE_SECURITY_NOTICE -> {
                val binding = FragmentPublishNativeSpeakerSecurityNoticeBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                return ViewHolderNotice(binding)
            }

            TYPE_CONTROLS -> {
                val binding = FragmentPublishNativeSpeakerControlsBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                return ViewHolderControls(binding)
            }

            TYPE_SPEAKER -> {
                val binding = FragmentPublishNativeSpeakerItemBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                return ViewHolderSpeaker(binding)
            }

            else -> {
                val binding = FragmentPublishNativeSpeakerItemBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
                return ViewHolderSpeaker(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: GenericViewHolder, position: Int) {
        // TRICKY: only the Native Speaker holder uses the position so we must account for the privacy notice
        val extras = Bundle()
        extras.putBoolean(EXTRA_DISPLAY_NEXT, displayNext)
        holder.putExtras(extras)
        holder.loadView(contributors, position - 1, listener)
    }

    override fun getItemCount(): Int {
        return if (contributors.size > 0) {
            contributors.size + 2 // add space for security info and controls
        } else {
            1 + 2 // display notice card explaining they must add a translator
        }
    }

    fun setOnClickListener(listener: OnClickListener?) {
        this.listener = listener
    }

    /**
     * Specifies if the next button should be displayed
     * @param display
     */
    fun setDisplayNext(display: Boolean) {
        displayNext = display
    }

    interface OnClickListener {
        fun onEditNativeSpeaker(speaker: NativeSpeaker)
        fun onClickAddNativeSpeaker()
        fun onClickNext()
        fun onClickPrivacyNotice()
    }

    abstract class GenericViewHolder(
        binding: ViewBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        abstract fun loadView(
            speakers: List<NativeSpeaker>,
            position: Int,
            listener: OnClickListener?
        )

        abstract fun putExtras(extras: Bundle)
    }

    class ViewHolderNotice(
        val binding: FragmentPublishNativeSpeakerSecurityNoticeBinding
    ) : GenericViewHolder(binding) {
        override fun loadView(
            speakers: List<NativeSpeaker>,
            position: Int,
            listener: OnClickListener?
        ) {
            binding.root.setOnClickListener {
                listener?.onClickPrivacyNotice()
            }
        }
        override fun putExtras(extras: Bundle) {
        }
    }

    class ViewHolderSpeaker(
        val binding: FragmentPublishNativeSpeakerItemBinding
    ) : GenericViewHolder(binding) {
        override fun loadView(
            speakers: List<NativeSpeaker>,
            position: Int,
            listener: OnClickListener?
        ) {
            if (speakers.isNotEmpty()) {
                binding.name.text = speakers[position].toString()
                binding.editButton.setOnClickListener {
                    listener?.onEditNativeSpeaker(speakers[position])
                }
            } else {
                // display notice a translator must be added
                binding.name.setText(R.string.who_translated_notice)
                binding.editButton.setOnClickListener {
                    listener?.onClickAddNativeSpeaker()
                }
            }
        }

        override fun putExtras(extras: Bundle) {
        }
    }

    class ViewHolderControls(
        val binding: FragmentPublishNativeSpeakerControlsBinding
    ) : GenericViewHolder(binding) {
        private var displayNext = true

        override fun loadView(
            speakers: List<NativeSpeaker>,
            position: Int,
            listener: OnClickListener?
        ) {
            if (displayNext) {
                binding.nextButton.visibility = View.VISIBLE
            } else {
                binding.nextButton.visibility = View.GONE
            }
            binding.nextButton.setOnClickListener { listener?.onClickNext() }
            binding.addButton.setOnClickListener { listener?.onClickAddNativeSpeaker() }
        }

        override fun putExtras(extras: Bundle) {
            this.displayNext = extras.getBoolean(EXTRA_DISPLAY_NEXT, true)
        }
    }

    companion object {
        private const val TYPE_SECURITY_NOTICE = 0
        private const val TYPE_SPEAKER = 1
        private const val TYPE_CONTROLS = 2
        const val EXTRA_DISPLAY_NEXT: String = "display_next"
    }
}
