package com.door43.translationstudio.rendering;

import android.content.Context;
import android.text.TextUtils;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.door43.translationstudio.ui.spannables.PassageLinkSpan;
import com.door43.translationstudio.ui.spannables.Span;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles rendering of internal links to html anchors
 */
public class LinkToHtmlRenderer extends RenderingEngine {
    public static final String SCHEME_CHUNK = "chunk";
    private final OnPreprocessLink preprocessCallback;

    /**
     *
     * @param preprocessor called to determine if link should be rendered
     */
    public LinkToHtmlRenderer(
            Context context,
            OnPreprocessLink preprocessor
    ) {
        this.context = context;
        preprocessCallback = preprocessor;
    }

    @Override
    public CharSequence render(CharSequence in) {
        CharSequence out = in;
        out = renderPassageLink(out);
        if(isStopped()) return in;
        return out;
    }

    /**
     * Renders links to other passages in the project
     * Example [[:en:bible:notes:gen:01:03|1:5]]
     * @param in
     * @return
     */
    private CharSequence renderPassageLink(CharSequence in) {
        return renderLink(in, SCHEME_CHUNK, PassageLinkSpan.PATTERN, new OnCreateLink() {
            @Override
            public Span onCreate(Matcher matcher) {
                return new PassageLinkSpan(matcher.group(3), matcher.group(2));
            }
        });
    }

    /**
     * A generic rendering method for rendering content links as html
     *
     * @param in
     * @param scheme
     * @param pattern
     * @param callback   @return
     */
    private CharSequence renderLink(CharSequence in, String scheme, Pattern pattern, OnCreateLink callback) {
        CharSequence out = "";
        Matcher matcher = pattern.matcher(in);
        int lastIndex = 0;
        while(matcher.find()) {
            if(isStopped()) return in;
            Span link = callback.onCreate(matcher);
            if(link != null) {
                if (preprocessCallback == null || preprocessCallback.onPreprocess(link)) {
                    // render clickable link
                    CharSequence title = link.getHumanReadable();
                    if(title == null || title.toString().isEmpty()) {
                        title = link.getMachineReadable();
                    }
                    String htmlLink = "<a href=\"" + scheme + "://" + link.getMachineReadable() + "\">" + title + "</a>";
                    out = TextUtils.concat(out, in.subSequence(lastIndex, matcher.start()), htmlLink);
                } else {
                    // render as plain text
                    out = TextUtils.concat(out, in.subSequence(lastIndex, matcher.start()), link.getHumanReadable());
                }
            } else {
                // ignore link
                out = TextUtils.concat(out, in.subSequence(lastIndex, matcher.end()));
            }
            lastIndex = matcher.end();
        }
        out = TextUtils.concat(out, in.subSequence(lastIndex, in.length()));
        return out;
    }

    private interface OnCreateLink {
        Span onCreate(Matcher matcher);
    }

    /**
     * Used to identify which links to render
     */
    public interface OnPreprocessLink {
        boolean onPreprocess(Span span);
    }

    /**
     * Provides custom link handling for webviews
     */
    public static abstract class CustomWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            String postfix = "://";
            if(url != null) {
                if (url.startsWith(SCHEME_CHUNK + postfix)) {
                    onOverriddenLinkClick(view, url, new PassageLinkSpan("", url.substring((SCHEME_CHUNK + postfix).length())));
                    return true;
                }
            }
            onLinkClick(view, url);
            return true;
        }

        /**
         * Called when a url was successfully overridden
         * @param view
         * @param url
         * @param span
         */
        public abstract void onOverriddenLinkClick(WebView view, String url, Span span);

        /**
         * Called when a normal link is clicked
         * @param view
         * @param url
         */
        public abstract void onLinkClick(WebView view, String url);
    }
}
