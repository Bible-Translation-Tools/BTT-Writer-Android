package com.door43.translationstudio.ui.dialogs

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.fragment.app.DialogFragment
import com.door43.translationstudio.App
import com.door43.translationstudio.databinding.DialogDeviceNetworkAliasBinding

/**
 * Created by joel on 11/25/2015.
 */
class DeviceNetworkAliasDialog : DialogFragment() {
    private var listener: OnDismissListener? = null

    private var _binding: DialogDeviceNetworkAliasBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        dialog?.requestWindowFeature(Window.FEATURE_NO_TITLE)
        _binding = DialogDeviceNetworkAliasBinding.inflate(inflater, container, false)

        with(binding) {
            deviceName.setText(App.deviceNetworkAlias)
            cancelButton.setOnClickListener { dismiss() }
            confirmButton.setOnClickListener {
                App.deviceNetworkAlias = deviceName.text.toString()
                dismiss()
            }
        }

        return binding.root
    }

    override fun onDismiss(dialogInterface: DialogInterface) {
        listener?.onDismiss()
        super.onDismiss(dialogInterface)
    }

    fun setOnDismissListener(listener: OnDismissListener?) {
        this.listener = listener
    }

    interface OnDismissListener {
        fun onDismiss()
    }

    override fun onDestroy() {
        listener = null
        super.onDestroy()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
