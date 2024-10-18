package com.door43.translationstudio.ui.home

import android.content.Intent
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.fragment.app.DialogFragment
import com.door43.translationstudio.databinding.DialogUpdateLibraryBinding
import org.unfoldingword.tools.eventbuffer.EventBuffer
import org.unfoldingword.tools.eventbuffer.EventBuffer.OnEventTalker

/**
 * Displays options for updating the app content
 */
class UpdateLibraryDialog : DialogFragment(), OnEventTalker {
    private val eventBuffer = EventBuffer()

    private var _binding: DialogUpdateLibraryBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        dialog?.requestWindowFeature(Window.FEATURE_NO_TITLE)
        _binding = DialogUpdateLibraryBinding.inflate(inflater, container, false)

        with(binding) {
            infoButton.setOnClickListener {
                val browserIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("http://help.door43.org/en/knowledgebase/9-translationstudio/docs/5-update-options")
                )
                startActivity(browserIntent)
            }
            updateLanguages.setOnClickListener {
                eventBuffer.write(
                    this@UpdateLibraryDialog, EVENT_UPDATE_LANGUAGES, null
                )
            }
            downloadSources.setOnClickListener {
                eventBuffer.write(
                    this@UpdateLibraryDialog, EVENT_SELECT_DOWNLOAD_SOURCES, null
                )
            }
            updateSource.setOnClickListener {
                eventBuffer.write(
                    this@UpdateLibraryDialog, EVENT_UPDATE_SOURCE, null
                )
            }
            downloadIndex.setOnClickListener {
                eventBuffer.write(
                    this@UpdateLibraryDialog, EVENT_DOWNLOAD_INDEX, null
                )
            }
            updateAppText.paintFlags = updateAppText.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            updateApp.setOnClickListener {
                eventBuffer.write(
                    this@UpdateLibraryDialog, EVENT_UPDATE_APP, null
                )
            }
            dismissButton.setOnClickListener { dismiss() }
        }

        return binding.root
    }

    override fun onDestroy() {
        eventBuffer.removeAllListeners()
        super.onDestroy()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun getEventBuffer(): EventBuffer {
        return eventBuffer
    }

    companion object {
        const val EVENT_UPDATE_LANGUAGES: Int = 1
        const val EVENT_UPDATE_SOURCE: Int = 2
        const val EVENT_UPDATE_ALL: Int = 3
        const val EVENT_SELECT_DOWNLOAD_SOURCES: Int = 4
        const val EVENT_UPDATE_APP: Int = 5
        const val EVENT_DOWNLOAD_INDEX: Int = 6
        const val TAG: String = "update-library-dialog"
    }
}
