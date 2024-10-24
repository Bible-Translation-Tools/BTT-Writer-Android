package com.door43.translationstudio.ui.devtools

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import com.door43.translationstudio.R
import com.door43.translationstudio.databinding.FragmentToolItemBinding

/**
 * The tool adapter allows you to easily create a ListView full of tools
 */
class ToolAdapter : BaseAdapter() {
    private val tools = arrayListOf<ToolItem>()

    override fun getCount(): Int {
        return tools.size
    }

    override fun getItem(i: Int): ToolItem {
        return tools[i]
    }

    override fun getItemId(i: Int): Long {
        return 0
    }

    override fun getView(i: Int, recycledView: View?, viewGroup: ViewGroup): View {
        val context = viewGroup.context
        val binding = if (recycledView == null) {
            val inflater = LayoutInflater.from(context)
            FragmentToolItemBinding.inflate(inflater, viewGroup, false).apply {
                root.tag = this
            }
        } else {
            recycledView.tag as FragmentToolItemBinding
        }

        val item = getItem(i)

        // title
        binding.toolTitleText.text = item.name

        // description
        binding.toolDescriptionText.text = item.description

        if (item.description.isEmpty()) {
            binding.toolDescriptionText.visibility = View.GONE
        } else {
            binding.toolDescriptionText.visibility = View.VISIBLE
        }

        // image
        if (item.icon > 0) {
            binding.toolIconImageView.visibility = View.VISIBLE
            binding.toolIconImageView.setBackgroundResource(getItem(i).icon)
        } else {
            binding.toolIconImageView.visibility = View.GONE
        }

        // mark tool as disabled.
        if (!getItem(i).isEnabled) {
            binding.toolTitleText.setTextColor(context.resources.getColor(R.color.gray))
        } else {
            binding.toolTitleText.setTextColor(context.resources.getColor(R.color.dark_primary_text))
        }

        return binding.root
    }

    fun setTools(tools: List<ToolItem>) {
        this.tools.clear()
        this.tools.addAll(tools)
        notifyDataSetChanged()
    }
}
