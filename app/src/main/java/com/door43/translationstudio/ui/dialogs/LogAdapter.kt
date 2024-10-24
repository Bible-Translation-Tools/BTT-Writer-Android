package com.door43.translationstudio.ui.dialogs

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import com.door43.translationstudio.R
import com.door43.translationstudio.databinding.FragmentLogItemBinding
import com.door43.widget.ViewUtil
import org.unfoldingword.tools.logger.LogEntry
import org.unfoldingword.tools.logger.LogLevel

/**
 * Created by joel on 2/26/2015.
 */
class LogAdapter : BaseAdapter() {
    private var logs = arrayListOf<LogEntry>()

    /**
     * Adds an item to the adapter
     * @param logs
     */
    fun setItems(logs: List<LogEntry>) {
        this.logs.clear()
        this.logs.addAll(logs)
        notifyDataSetChanged()
    }

    override fun getCount(): Int {
        return logs.size
    }

    override fun getItem(i: Int): LogEntry {
        return logs[i]
    }

    override fun getItemId(i: Int): Long {
        return 0
    }

    override fun getView(i: Int, view: View?, viewGroup: ViewGroup): View {
        val logEntry = getItem(i)

        val binding = if (view == null) {
            FragmentLogItemBinding.inflate(
                LayoutInflater.from(viewGroup.context),
                viewGroup,
                false
            )
        } else {
            FragmentLogItemBinding.bind(view)
        }

        with(binding) {
            logTitle.text = logEntry.message
            logNamespace.text = logEntry.classPath

            when (logEntry.level) {
                LogLevel.Error -> {
                    logIcon.setBackgroundResource(R.drawable.ic_error_black_18dp)
                    ViewUtil.tintViewDrawable(
                        logIcon,
                        viewGroup.context.resources.getColor(R.color.danger)
                    )
                }
                LogLevel.Warning -> {
                    logIcon.setBackgroundResource(R.drawable.ic_warning_black_18dp)
                    ViewUtil.tintViewDrawable(
                        logIcon,
                        viewGroup.context.resources.getColor(R.color.warning)
                    )
                }
                LogLevel.Info -> {
                    logIcon.setBackgroundResource(R.drawable.ic_info_black_18dp)
                    ViewUtil.tintViewDrawable(
                        logIcon,
                        viewGroup.context.resources.getColor(R.color.info)
                    )
                }
                else -> {}
            }
        }

        return binding.root
    }
}
