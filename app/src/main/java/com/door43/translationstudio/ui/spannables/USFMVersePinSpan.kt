package com.door43.translationstudio.ui.spannables

import android.annotation.SuppressLint
import android.graphics.drawable.BitmapDrawable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.text.style.RelativeSizeSpan
import android.view.LayoutInflater
import androidx.core.content.ContextCompat
import com.door43.translationstudio.R
import com.door43.translationstudio.databinding.FragmentVerseMarkerBinding
import com.door43.widget.ViewUtil

class USFMVersePinSpan : USFMVerseSpan {

    private var spannable: SpannableStringBuilder? = null

    constructor(verse: String) : super(verse)

    constructor(verse: Int) : super(verse)

    @SuppressLint("SetTextI18n")
    override fun render(): SpannableStringBuilder {
        if (spannable == null) {
            val s = super.render()

            context?.let { ctx ->
                // apply custom styles
                s.setSpan(RelativeSizeSpan(0.8f), 0, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                s.setSpan(
                    ForegroundColorSpan(ContextCompat.getColor(ctx, R.color.white)),
                    0,
                    s.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )

                val inflater = LayoutInflater.from(ctx)
                val binding = FragmentVerseMarkerBinding.inflate(inflater)

                if (endVerseNumber > 0) {
                    binding.verse.text = "$startVerseNumber-$endVerseNumber"
                } else {
                    binding.verse.text = "$startVerseNumber"
                }

                val image = ViewUtil.convertToBitmap(binding.root)
                val background = BitmapDrawable(ctx.resources, image)
                background.setBounds(0, 0, background.minimumWidth, background.minimumHeight)
                s.setSpan(ImageSpan(background), 0, s.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            spannable = s
        }
        return spannable!!
    }
}