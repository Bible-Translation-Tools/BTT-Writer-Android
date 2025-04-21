/*
https://github.com/danialgoodwin/android-widget-keyboardless-edittext/blob/master/KeyboardlessEditText2.java

The MIT License (MIT)
Copyright (c) 2014 Danial Goodwin (danialgoodwin.com)
Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:
The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.
THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/
package com.door43.widget

import android.content.Context
import android.graphics.Rect
import android.text.InputType
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View.OnClickListener
import android.view.View.OnLongClickListener
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.widget.AppCompatEditText
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * This is the same as a native EditText, except that no soft keyboard
 * will appear when user clicks on widget. All other normal operations
 * still work.
 * To use in XML, add a widget for <my.package.name>.KeyboardlessEditText
 * To use in Java, use one of the three constructors in this class
</my.package.name> */
class KeyboardlessEditText : AppCompatEditText {
    private val mOnClickListener = OnClickListener { isCursorVisible = false }

    private val mOnLongClickListener = OnLongClickListener {
        isCursorVisible = false
        false
    }

    constructor(context: Context) : super(context) {
        initialize()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initialize()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        initialize()
    }

    private fun initialize() {
        synchronized(this) {
            inputType = inputType or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            isFocusableInTouchMode = true
        }

        // Needed to show cursor when user interacts with EditText so that the edit operations
        // still work. Without the cursor, the edit operations won't appear.
        setOnClickListener(mOnClickListener)
        setOnLongClickListener(mOnLongClickListener)

        showSoftInputOnFocus = false // This is a hidden method in TextView.
        reflexSetShowSoftInputOnFocus(false) // Workaround.

        // Ensure that cursor is at the end of the input box when initialized. Without this, the
        // cursor may be at index 0 when there is text added via layout XML.
        text?.let { setSelection(it.length) }
    }

    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect)
        hideKeyboard()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val ret = super.onTouchEvent(event)
        // Must be done after super.onTouchEvent()
        hideKeyboard()
        return ret
    }

    private fun hideKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        if (imm != null && imm.isActive(this)) {
            imm.hideSoftInputFromWindow(applicationWindowToken, 0)
        }
    }

    private fun reflexSetShowSoftInputOnFocus(show: Boolean) {
        if (mShowSoftInputOnFocus != null) {
            invokeMethod(mShowSoftInputOnFocus, this, show)
        } else {
            // Use fallback method. Not tested.
            hideKeyboard()
        }
    }

    companion object {
        private val mShowSoftInputOnFocus = getMethod(
            AppCompatEditText::class.java,
            "setShowSoftInputOnFocus",
            Boolean::class.javaPrimitiveType
        )

        /**
         * Returns method if available in class or superclass (recursively),
         * otherwise returns null.
         */
        private fun getMethod(
            cls: Class<*>,
            methodName: String,
            vararg parametersType: Class<*>?
        ): Method? {
            var sCls = cls.superclass
            while (sCls != Any::class.java) {
                try {
                    return sCls.getDeclaredMethod(methodName, *parametersType)
                } catch (e: NoSuchMethodException) {
                    // Just super it again
                }
                sCls = sCls.superclass
            }
            return null
        }

        /**
         * Returns results if available, otherwise returns null.
         */
        fun invokeMethod(method: Method, receiver: Any?, vararg args: Any?) {
            try {
                method.invoke(receiver, *args)
            } catch (e: IllegalArgumentException) {
                Log.e("Safe invoke fail", "Invalid args", e)
            } catch (e: IllegalAccessException) {
                Log.e("Safe invoke fail", "Invalid access", e)
            } catch (e: InvocationTargetException) {
                Log.e("Safe invoke fail", "Invalid target", e)
            }
        }
    }
}