package com.door43.translationstudio.ui.dialogs

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.door43.translationstudio.databinding.FragmentProgressDialogBinding

object ProgressHelper {

    private var dialog: AlertDialog? = null
    private var max = 100

    data class Progress(
        val message: String? = null,
        val progress: Int = -1,
        val max: Int = ProgressHelper.max
    )

    class ProgressDialog(private val binding: FragmentProgressDialogBinding) {
        fun show() {
            if (dialog?.isShowing == false) {
                dialog?.show()
            }
        }

        fun dismiss() {
            if (dialog?.isShowing == true) {
                dialog?.dismiss()
            }
        }

        fun setMessage(message: String?) {
            binding.message.text = message
        }

        @SuppressLint("SetTextI18n")
        fun setProgress(progress: Int) {
            with(binding) {
                val percent = (progress / max.toFloat() * 100).toInt()

                progressPercent.text = "$percent%"
                progressNumber.text = "$progress/$max"

                progressBar.progress = progress
                progressBar.isIndeterminate = progress < 0

                if (progressBar.isIndeterminate) {
                    progressPercent.visibility = View.GONE
                    progressNumber.visibility = View.GONE
                } else {
                    progressPercent.visibility = View.VISIBLE
                    progressNumber.visibility = View.VISIBLE
                }
            }
        }

        fun setMax(value: Int) {
            max = value
            binding.progressBar.max = value
        }
    }

    @JvmStatic
    fun newInstance(context: Context, title: Int, cancelable: Boolean): ProgressDialog {
        val builder = AlertDialog.Builder(context)
        val binding = FragmentProgressDialogBinding.inflate(LayoutInflater.from(context))
        builder.setView(binding.root)

        dialog = builder.create().apply {
            setTitle(title)
            setCancelable(cancelable)
            setCanceledOnTouchOutside(cancelable)
            binding.progressBar.isIndeterminate = true

            binding.progressNumber.visibility = View.GONE
            binding.progressPercent.visibility = View.GONE
        }

        return ProgressDialog(binding)
    }
}