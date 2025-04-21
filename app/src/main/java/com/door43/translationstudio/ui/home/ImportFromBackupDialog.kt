package com.door43.translationstudio.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.AdapterView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import com.door43.translationstudio.databinding.DialogImportFromBackupBinding
import com.door43.translationstudio.ui.viewmodels.ImportViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import kotlin.math.min

/**
 * Created by joel on 5/10/16.
 */
@AndroidEntryPoint
class ImportFromBackupDialog: DialogFragment() {
    private val viewModel: ImportViewModel by viewModels()

    private val adapter by lazy { BackupItemsAdapter() }

    private var _binding: DialogImportFromBackupBinding? = null
    private val binding get() = _binding!!

    private var onItemSelected: (File) -> Unit = {}

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        dialog?.requestWindowFeature(Window.FEATURE_NO_TITLE)
        _binding = DialogImportFromBackupBinding.inflate(inflater, container, false)

        with(binding) {
            dismissButton.setOnClickListener {
                dismiss()
            }

            backupItems.adapter = adapter
            backupItems.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
                doImportBackup(position)
            }

            adapter.setBackupItems(viewModel.getBackupTranslations())
        }

        return binding.root
    }

    override fun onResume() {
        super.onResume()

        // widen dialog to accommodate more text
        val desiredWidth = 750
        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels
        val density = displayMetrics.density
        val correctedWidth = width / density
        var screenWidthFactor = desiredWidth / correctedWidth
        screenWidthFactor = min(screenWidthFactor.toDouble(), 1.0).toFloat() // sanity check
        dialog?.window?.setLayout(
            (width * screenWidthFactor).toInt(),
            WindowManager.LayoutParams.MATCH_PARENT
        )
    }

    fun setOnItemSelected(callback: (File) -> Unit) {
        onItemSelected = callback
    }

    /**
     * do import of project at position
     * @param position
     */
    private fun doImportBackup(position: Int) {
        val backup = adapter.getItem(position)
        onItemSelected(backup)
        dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "ImportFromBackupDialog"
    }
}
