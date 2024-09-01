package com.door43.translationstudio.ui.dialogs

import android.app.Dialog
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.door43.translationstudio.databinding.FragmentProgressDialogBinding

object ProgressDialogFactory {
    private var fragmentManager: FragmentManager? = null

    data class Progress(val title: Int, val message: String? = null, val progress: Int = -1)

    class ProgressDialog {
        fun show(progress: Progress) {
            val progressDialog = getDialog()
            if (progressDialog == null) {
                ProgressDialogFragment.newInstance(progress).showNow(
                    fragmentManager!!, ProgressDialogFragment.TAG
                )
            }
        }

        fun dismiss() {
            getDialog()?.dismissNow()
        }

        fun setCancelable(cancelable: Boolean) {
            getDialog()?.setCancelable(cancelable)
        }

        fun setMax(max: Int) {
            getDialog()?.max = max
        }

        fun updateProgress(progress: Int) {
            getDialog()?.updateProgress(progress)
        }

        fun updateTitle(title: Int) {
            getDialog()?.updateTitle(title)
        }

        fun updateTitle(title: String) {
            getDialog()?.updateTitle(title)
        }

        fun updateMessage(message: String) {
            getDialog()?.updateMessage(message)
        }

        fun isShowing(): Boolean {
            return getDialog()?.isShowing == true
        }
    }

    fun newInstance(fragmentManager: FragmentManager): ProgressDialog {
        this.fragmentManager = fragmentManager
        return ProgressDialog()
    }

    private fun getDialog() : ProgressDialogFragment? {
        return fragmentManager
            ?.findFragmentByTag(ProgressDialogFragment.TAG) as? ProgressDialogFragment
    }

    class ProgressDialogFragment : DialogFragment() {

        private var _binding: FragmentProgressDialogBinding? = null
        private val binding get() = _binding!!

        val isShowing: Boolean
            get() = dialog?.isShowing == true

        var max: Int
            get() = binding.progress.max
            set(value) {
                binding.progress.max = value
            }

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            _binding = FragmentProgressDialogBinding.inflate(layoutInflater)
            val builder = AlertDialog.Builder(requireContext())
            builder.setView(binding.root)

            val title = arguments?.getInt("title")
            val message = arguments?.getString("message")

            val alert = builder.create()
            title?.let(alert::setTitle)
            alert.setMessage(message)
            alert.setCancelable(false)
            alert.setCanceledOnTouchOutside(false)

            binding.progress.isIndeterminate = true
            binding.progress.max = 100

            return alert
        }

        fun updateProgress(value: Int) {
            with(binding) {
                progress.progress = value
                progress.isIndeterminate = value < 0

                if (progress.isIndeterminate) {
                    progressPercent.visibility = View.GONE
                    progressNumber.visibility = View.GONE
                } else {
                    progressPercent.visibility = View.VISIBLE
                    progressNumber.visibility = View.VISIBLE
                }
            }
        }

        fun updateTitle(title: Int) {
            dialog?.setTitle(title)
        }

        fun updateTitle(title: String) {
            dialog?.setTitle(title)
        }

        fun updateMessage(message: String?) {
            (dialog as? AlertDialog)?.setMessage(message)
        }

        override fun setCancelable(cancelable: Boolean) {
            dialog?.setCancelable(cancelable)
            dialog?.setCanceledOnTouchOutside(cancelable)
        }

        override fun onDestroyView() {
            super.onDestroyView()
            _binding = null
        }

        companion object {
            const val TAG = "progress_dialog"
            fun newInstance(progress: Progress): ProgressDialogFragment {
                val fragment = ProgressDialogFragment()
                val args = Bundle()
                args.putInt("title", progress.title)
                args.putString("message", progress.message)
                fragment.arguments = args
                return fragment
            }
        }
    }
}