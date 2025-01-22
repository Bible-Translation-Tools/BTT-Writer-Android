package com.door43.translationstudio.ui.dialogs

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.door43.translationstudio.databinding.FragmentProgressDialogBinding

object ProgressHelper {

    private var max = 100

    data class Progress(
        val message: String? = null,
        val progress: Int = -1,
        val max: Int = ProgressHelper.max
    )

    class ProgressDialog(
        private val fragmentManager: FragmentManager,
        private val title: Int,
        private val cancelable: Boolean
    ) {
        private val fragment: ProgressFragment?
            get() = fragmentManager.findFragmentByTag(TAG) as? ProgressFragment

        private val handler = Handler(Looper.getMainLooper())

        fun show() {
            handler.post {
                if (fragment == null) {
                    if (!fragmentManager.isDestroyed) {
                        val ft = fragmentManager.beginTransaction()
                        val fragment = ProgressFragment.newInstance(title, cancelable)
                        ft.add(fragment, TAG)
                        ft.commitNow()
                    }
                }
            }
        }

        fun dismiss() {
            handler.post {
                if (!fragmentManager.isDestroyed) {
                    fragment?.let {
                        val ft = fragmentManager.beginTransaction()
                        ft.remove(it)
                        ft.commitNow()
                    }
                }
            }
        }

        fun setMessage(message: String?) {
            handler.post { fragment?.setMessage(message) }
        }

        @SuppressLint("SetTextI18n")
        fun setProgress(progress: Int) {
            handler.post { fragment?.setProgress(progress) }
        }

        fun setMax(value: Int) {
            max = value
            handler.post { fragment?.setMax(value) }
        }

        private companion object {
            const val TAG = "progress_dialog"
        }
    }

    @JvmStatic
    fun newInstance(
        fragmentManager: FragmentManager,
        title: Int,
        cancelable: Boolean
    ): ProgressDialog {
        return ProgressDialog(fragmentManager, title, cancelable)
    }

    class ProgressFragment: DialogFragment() {
        private var binding: FragmentProgressDialogBinding? = null

        companion object {
            fun newInstance(
                title: Int,
                cancelable: Boolean
            ): ProgressFragment {
                val fragment = ProgressFragment()
                val args = Bundle()
                args.putInt("title", title)
                args.putBoolean("cancelable", cancelable)
                fragment.arguments = args
                return fragment
            }
        }

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val builder = AlertDialog.Builder(requireActivity())
            binding = FragmentProgressDialogBinding.inflate(layoutInflater)
            builder.setView(binding!!.root)

            val title = arguments?.getInt("title") ?: 0
            val cancelable = arguments?.getBoolean("cancelable") ?: true

            return builder.create().apply {
                setTitle(title)
                setCancelable(cancelable)
                setCanceledOnTouchOutside(cancelable)

                binding?.let { binding ->
                    with(binding) {
                        progressBar.isIndeterminate = true
                        progressNumber.visibility = View.GONE
                        progressPercent.visibility = View.GONE
                    }
                }
            }
        }

        fun setMessage(message: String?) {
            binding?.message?.text = message
        }

        @SuppressLint("SetTextI18n")
        fun setProgress(progress: Int) {
            binding?.let { binding ->
                with(binding) {
                    progressBar.isIndeterminate = progress < 0

                    if (progressBar.isIndeterminate) {
                        progressPercent.visibility = View.GONE
                        progressNumber.visibility = View.GONE
                    } else {
                        val percent = (progress / max.toFloat() * 100).toInt()

                        progressPercent.text = "$percent%"
                        progressNumber.text = "$progress/$max"

                        progressBar.progress = progress
                        progressPercent.visibility = View.VISIBLE
                        progressNumber.visibility = View.VISIBLE
                    }
                }
            }
        }

        fun setMax(value: Int) {
            binding?.progressBar?.max = value
        }

        override fun onDestroyView() {
            super.onDestroyView()
            binding = null
        }
    }
}