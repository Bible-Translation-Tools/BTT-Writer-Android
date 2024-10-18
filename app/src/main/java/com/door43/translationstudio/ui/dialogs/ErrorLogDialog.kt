package com.door43.translationstudio.ui.dialogs

import android.app.Dialog
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.door43.translationstudio.databinding.DialogErrorLogBinding
import com.door43.translationstudio.ui.viewmodels.DeveloperViewModel
import org.unfoldingword.tools.logger.Logger

/**
 * This dialog display a list of all the error logs
 */
class ErrorLogDialog : DialogFragment() {
    private val adapter by lazy { LogAdapter() }

    private var _binding: DialogErrorLogBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DeveloperViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        dialog?.setTitle("Logs")

        _binding = DialogErrorLogBinding.inflate(inflater, container, false)

        with(binding) {
            errorLogListView.adapter = adapter
            errorLogListView.onItemClickListener = AdapterView.OnItemClickListener { _, _, i, _ ->
                val details = adapter.getItem(i).details
                if (activity != null && details != null && details.isNotEmpty()) {
                    val dialog = Dialog(requireActivity())
                    val text = TextView(activity)
                    text.text = details
                    text.isVerticalScrollBarEnabled = true
                    text.setPadding(32, 32, 32, 32)
                    text.movementMethod = ScrollingMovementMethod.getInstance()
                    text.canScrollVertically(View.SCROLL_AXIS_VERTICAL)
                    dialog.setContentView(text)
                    dialog.setTitle("Log Details")
                    dialog.show()
                }
            }

            dismissButton.setOnClickListener { dismiss() }

            emptyLogButton.setOnClickListener {
                Logger.flush()
                dismiss()
            }
        }

        setupObservers()

        viewModel.readErrorLog()

        return binding.root
    }

    private fun setupObservers() {
        viewModel.logs.observe(viewLifecycleOwner) {
            it?.let { logs ->
                if (logs.isNotEmpty()) {
                    adapter.setItems(logs)
                } else {
                    dismiss()
                    val toast = Toast.makeText(requireActivity(), "There are no logs", Toast.LENGTH_LONG)
                    toast.setGravity(Gravity.TOP, 0, 0)
                    toast.show()
                }
            }
        }
    }
}
