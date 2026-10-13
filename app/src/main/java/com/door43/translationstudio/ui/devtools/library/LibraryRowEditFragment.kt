package com.door43.translationstudio.ui.devtools.library

import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.activityViewModels
import com.door43.translationstudio.App
import com.door43.translationstudio.R
import com.door43.translationstudio.databinding.FragmentLibraryRowEditBinding
import com.door43.translationstudio.ui.BaseFragment
import com.door43.translationstudio.ui.viewmodels.EditLibraryViewModel
import com.door43.util.ColorUtil

class LibraryRowEditFragment : BaseFragment() {
    private var _binding: FragmentLibraryRowEditBinding? = null
    private val binding get() = _binding!!

    private val viewModel: EditLibraryViewModel by activityViewModels()
    private lateinit var table: LibraryTable
    private lateinit var rowId: String

    /** Field key -> current value reader. Returns null when the value should stay unchanged. */
    private val valueReaders = linkedMapOf<String, () -> String?>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val args = requireArguments()
        table = LibraryTable.valueOf(args.getString(ARG_TABLE)!!)
        rowId = args.getString(ARG_ROW_ID)!!
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLibraryRowEditBinding.inflate(inflater, container, false)

        valueReaders.clear()
        for (field in viewModel.getFields(table, rowId)) {
            addField(field)
        }

        binding.saveButton.setOnClickListener { confirmSave() }

        viewModel.saveResult.observe(viewLifecycleOwner) { result ->
            result?.let {
                viewModel.clearSaveResult()
                showSaveResult(it)
            }
        }

        return binding.root
    }

    override fun onResume() {
        super.onResume()
        activity?.title = rowId
    }

    private fun addField(field: EditableField) {
        val context = requireContext()
        val group = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = resources.getDimensionPixelSize(R.dimen.dialog_controls_margin) }
        }

        if (field !is EditableField.Bool) {
            group.addView(TextView(context).apply {
                text = getString(field.labelRes)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.sub_title))
                setTextColor(ColorUtil.getColor(context, R.color.dark_secondary_text))
            })
        }

        when (field) {
            is EditableField.Text -> {
                val input = EditText(context).apply {
                    setText(field.value)
                    isSingleLine = true
                    isEnabled = !field.readOnly
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.body))
                    setTextColor(ColorUtil.getColor(context, R.color.dark_primary_text))
                }
                group.addView(input)
                if (!field.readOnly) {
                    valueReaders[field.key] = { input.text.toString() }
                }
            }
            is EditableField.Choice -> {
                val radioGroup = RadioGroup(context)
                field.options.forEach { option ->
                    radioGroup.addView(RadioButton(context).apply {
                        id = View.generateViewId()
                        text = option.label
                        tag = option.value
                        setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.body))
                        setTextColor(ColorUtil.getColor(context, R.color.dark_primary_text))
                        isChecked = option.value == field.value
                    })
                }
                group.addView(radioGroup)
                valueReaders[field.key] = {
                    radioGroup.findViewById<RadioButton>(radioGroup.checkedRadioButtonId)?.tag as? String
                }
            }
            is EditableField.Bool -> {
                val checkBox = CheckBox(context).apply {
                    text = getString(field.labelRes)
                    isChecked = field.value
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.body))
                    setTextColor(ColorUtil.getColor(context, R.color.dark_primary_text))
                }
                group.addView(checkBox)
                valueReaders[field.key] = { checkBox.isChecked.toString() }
            }
        }

        binding.fieldsContainer.addView(group)
    }

    private fun collectValues(): Map<String, String> {
        val values = linkedMapOf<String, String>()
        for ((key, reader) in valueReaders) {
            reader()?.let { values[key] = it }
        }
        return values
    }

    private fun confirmSave() {
        AlertDialog.Builder(requireContext(), R.style.AppTheme_Dialog)
            .setTitle(R.string.confirm)
            .setMessage(R.string.edit_library_confirm_save)
            .setPositiveButton(R.string.label_continue) { _, _ ->
                viewModel.save(table, rowId, collectValues())
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showSaveResult(success: Boolean) {
        if (success) {
            AlertDialog.Builder(requireContext(), R.style.AppTheme_Dialog)
                .setTitle(R.string.edit_library)
                .setMessage(R.string.edit_library_save_success)
                .setCancelable(false)
                .setPositiveButton(R.string.dismiss) { _, _ -> App.restart() }
                .setOnDismissListener { App.restart() }
                .show()
        } else {
            AlertDialog.Builder(requireContext(), R.style.AppTheme_Dialog)
                .setTitle(R.string.error)
                .setMessage(R.string.edit_library_save_failed)
                .setPositiveButton(R.string.label_ok, null)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_TABLE = "table"
        private const val ARG_ROW_ID = "row_id"

        fun newInstance(table: LibraryTable, rowId: String): LibraryRowEditFragment {
            return LibraryRowEditFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TABLE, table.name)
                    putString(ARG_ROW_ID, rowId)
                }
            }
        }
    }
}
