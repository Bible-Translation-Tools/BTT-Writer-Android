package com.door43.translationstudio.ui.spannables;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.text.style.RelativeSizeSpan;
import android.view.LayoutInflater;

import com.door43.translationstudio.R;
import com.door43.translationstudio.databinding.FragmentVerseMarkerBinding;
import com.door43.widget.ViewUtil;

/**
 * Created by joel on 10/1/2015.
 */
public class USFMVersePinSpan extends USFMVerseSpan {

    private SpannableStringBuilder mSpannable;

    public USFMVersePinSpan(String verse) {
        super(verse);
    }

    public USFMVersePinSpan(int verse) {
        super(verse);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public SpannableStringBuilder render() {
        if(mSpannable == null) {
            mSpannable = super.render();
            // apply custom styles
            mSpannable.setSpan(new RelativeSizeSpan(0.8f), 0, mSpannable.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            mSpannable.setSpan(new ForegroundColorSpan(context.getResources().getColor(R.color.white)), 0, mSpannable.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            FragmentVerseMarkerBinding binding = FragmentVerseMarkerBinding.inflate(inflater);

            if(getEndVerseNumber() > 0) {
                binding.verse.setText(getStartVerseNumber() + "-" + getEndVerseNumber());
            } else {
                binding.verse.setText("" + getStartVerseNumber());
            }
            Bitmap image = ViewUtil.convertToBitmap(binding.getRoot());
            BitmapDrawable background = new BitmapDrawable(context.getResources(), image);
            background.setBounds(0, 0, background.getMinimumWidth(), background.getMinimumHeight());
            mSpannable.setSpan(new ImageSpan(background), 0, mSpannable.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

        }
        return mSpannable;
    }
}

