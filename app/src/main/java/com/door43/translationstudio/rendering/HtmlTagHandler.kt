package com.door43.translationstudio.rendering

import android.content.Context
import android.text.Editable
import android.text.Html
import android.text.Layout
import android.text.Spannable
import android.text.style.AlignmentSpan
import android.text.style.BulletSpan
import android.text.style.LeadingMarginSpan
import android.text.style.TypefaceSpan
import android.util.Log
import com.door43.translationstudio.ui.spannables.LinkSpan
import com.door43.translationstudio.ui.spannables.Span
import org.xml.sax.XMLReader
import java.util.Vector

/**
 * Some parts of this code are based on android.text.Html
 */
class HtmlTagHandler(
    private val context: Context,
    private val clickListener: Span.OnClickListener
) : Html.TagHandler {

    private var listItemCount = 0
    private val listParents = Vector<String>()
    val attributes = HashMap<String, String>()

    private class Code
    private class Center
    private class AppLink

    /**
     * http://stackoverflow.com/questions/6952243/how-to-get-an-attribute-from-an-xmlreader
     * @param xmlReader
     */
    private fun processAttributes(xmlReader: XMLReader) {
        try {
            val elementField = xmlReader.javaClass.getDeclaredField("theNewElement")
            elementField.isAccessible = true
            val element = elementField.get(xmlReader)!!

            val attsField = element.javaClass.getDeclaredField("theAtts")
            attsField.isAccessible = true
            val atts = attsField.get(element)!!

            val dataField = atts.javaClass.getDeclaredField("data")
            dataField.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            val data = dataField.get(atts) as Array<String>

            val lengthField = atts.javaClass.getDeclaredField("length")
            lengthField.isAccessible = true
            val len = lengthField.get(atts) as Int

            /**
             * MSH: Look for supported attributes and add to hash map.
             * This is as tight as things can get :)
             * The data index is "just" where the keys and values are stored.
             */
            for (i in 0 until len) {
                attributes[data[i * 5 + 1]] = data[i * 5 + 4]
            }
        } catch (e: Exception) {
            Log.d(TAG, "Exception: $e")
        }
    }

    override fun handleTag(opening: Boolean, tag: String, output: Editable, xmlReader: XMLReader) {
        processAttributes(xmlReader)
        if (opening) {
            // opening tag
            if (DEBUG) {
                Log.d(TAG, "opening, output: $output")
            }

            when {
                tag.equals("ul", ignoreCase = true) ||
                        tag.equals("ol", ignoreCase = true) ||
                        tag.equals("dd", ignoreCase = true) -> {
                    listParents.add(tag)
                    listItemCount = 0
                }
                tag.equals("code", ignoreCase = true) -> start(output, Code())
                tag.equals("center", ignoreCase = true) -> start(output, Center())
                tag.equals("app-link", ignoreCase = true) -> start(output, AppLink())
            }
        } else {
            // closing tag
            if (DEBUG) {
                Log.d(TAG, "closing, output: $output")
            }

            when {
                tag.equals("ul", ignoreCase = true) ||
                        tag.equals("ol", ignoreCase = true) ||
                        tag.equals("dd", ignoreCase = true) -> {
                    listParents.remove(tag)
                    listItemCount = 0
                }
                tag.equals("li", ignoreCase = true) -> handleListTag(output)
                tag.equals("code", ignoreCase = true) -> end(output, Code::class.java, TypefaceSpan("monospace"), false)
                tag.equals("center", ignoreCase = true) -> end(output, Center::class.java, AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER), true)
                tag.equals("app-link", ignoreCase = true) -> handleAppLinkTag(output)
            }
        }
    }

    /**
     * Mark the opening tag by using private classes
     *
     * @param output
     * @param mark
     */
    private fun start(output: Editable, mark: Any) {
        val len = output.length
        output.setSpan(mark, len, len, Spannable.SPAN_MARK_MARK)

        if (DEBUG) {
            Log.d(TAG, "len: $len")
        }
    }

    private fun end(output: Editable, kind: Class<*>, repl: Any, paragraphStyle: Boolean) {
        val obj = getLast(output, kind) ?: return
        // start of the tag
        val where = output.getSpanStart(obj)
        // end of the tag
        var len = output.length

        output.removeSpan(obj)

        if (where != len) {
            // paragraph styles like AlignmentSpan need to end with a new line!
            if (paragraphStyle) {
                output.append("\n")
                len++
            }
            output.setSpan(repl, where, len, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        if (DEBUG) {
            Log.d(TAG, "where: $where")
            Log.d(TAG, "len: $len")
        }
    }

    /**
     * Get last marked position of a specific tag kind (private class)
     *
     * @param text
     * @param kind
     * @return
     */
    private fun getLast(text: Editable, kind: Class<*>): Any? {
        val objs = text.getSpans(0, text.length, kind)
        if (objs.isEmpty()) {
            return null
        } else {
            for (i in objs.size downTo 1) {
                if (text.getSpanFlags(objs[i - 1]) == Spannable.SPAN_MARK_MARK) {
                    return objs[i - 1]
                }
            }
            return null
        }
    }

    private fun handleAppLinkTag(output: Editable) {
        val obj = getLast(output, AppLink::class.java) ?: return
        // start of the tag
        val where = output.getSpanStart(obj)
        // end of the tag
        val len = output.length

        output.removeSpan(obj)

        val title = output.subSequence(where, len)
        val href = attributes["href"] ?: ""
        val type = attributes["type"] ?: ""

        val span = LinkSpan(title.toString(), href, type)
        span.onClickListener = this.clickListener

        if (where != len) {
            output.replace(where, len, span.toCharSequence(context))
        }

        if (DEBUG) {
            Log.d(TAG, "where: $where")
            Log.d(TAG, "len: $len")
        }
    }

    private fun handleListTag(output: Editable) {
        if (listParents.lastElement() == "ul") {
            output.append("\n")
            val split = output.toString().split("\n".toRegex()).toTypedArray()

            val lastIndex = split.size - 1
            val start = output.length - split[lastIndex].length - 1
            output.setSpan(BulletSpan(15 * listParents.size), start, output.length, 0)

        } else if (listParents.lastElement() == "ol") {
            listItemCount++

            output.append("\n")
            val split = output.toString().split("\n".toRegex()).toTypedArray()

            val lastIndex = split.size - 1
            val start = output.length - split[lastIndex].length - 1
            output.insert(start, "$listItemCount. ")
            output.setSpan(LeadingMarginSpan.Standard(15 * listParents.size), start, output.length, 0)
        }
    }

    companion object {
        const val TAG = "HtmlTagHandler"
        private const val DEBUG = true
    }
}