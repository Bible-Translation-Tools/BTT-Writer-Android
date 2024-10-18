package com.door43.translationstudio.core

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.widget.TextView
import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref
import com.door43.translationstudio.R
import com.door43.translationstudio.ui.SettingsActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject

/**
 * Created by joel on 9/11/2015.
 */
class Typography @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefRepository: IPreferenceRepository
) {
    // If you would override font used in tabs and language lists for a specific language code
    // just add a language code and the font to the default configuration.
    // For example:
    // {
    //      "gu" : "NotoSansGuLanguage-Regular.ttf",
    //      "default" : "NotoSansMultiLanguage-Regular.ttf"
    // }
    private val languageSubstituteFontsJson = """
        {
            "default" : "NotoSansMultiLanguage-Regular.ttf"
        }
    """.trimIndent()

    private var languageSubstituteFonts: JSONObject? = null
    private var defaultLanguageTypeface: Typeface? = null

    /**
     * Formats the text in the text view using the users preferences
     * @param translationType
     * @param view
     * @param languageCode the spoken language of the text
     * @param direction the reading direction of the text
     */
    fun format(
        translationType: TranslationType,
        view: TextView?,
        languageCode: String?,
        direction: String?
    ) {
        if (view != null) {
            val typeface = getTypeface(translationType, languageCode, direction)
            val fontSize = getFontSize(translationType)

            view.setTypeface(typeface, Typeface.NORMAL)
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize)
        }
    }

    /**
     * Formats the text in the text view using the users preferences.
     * Titles are a little larger than normal text and bold
     *
     * @param translationType
     * @param view
     * @param languageCode the spoken language of the text
     * @param direction the reading direction of the text
     */
    fun formatTitle(
        translationType: TranslationType,
        view: TextView?,
        languageCode: String?,
        direction: String?
    ) {
        if (view != null) {
            val typeface = getTypeface(translationType, languageCode, direction)
            val fontSize = getFontSize(translationType) * 1.3f

            view.setTypeface(typeface, Typeface.BOLD)
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize)
        }
    }

    /**
     * Formats the text in the text view using the users preferences.
     * Sub text is a little smaller than normal text
     *
     * @param translationType
     * @param view
     * @param languageCode the spoken language of the text
     * @param direction the reading direction of the text
     */
    fun formatSub(
        translationType: TranslationType,
        view: TextView?,
        languageCode: String?,
        direction: String?
    ) {
        if (view != null) {
            val typeface = getTypeface(translationType, languageCode, direction)
            val fontSize = getFontSize(translationType) * .7f

            view.setTypeface(typeface, Typeface.NORMAL)
            view.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSize)
        }
    }

    /**
     * Returns a subset of user preferences (currently, just the size) as a CSS style tag.
     * @param translationType
     * @return Valid HTML, for prepending to unstyled HTML text
     */
    fun getStyle(translationType: TranslationType): CharSequence {
        return ("<style type=\"text/css\">"
                + "body {"
                + "  font-size: " + getFontSize(translationType) + ";"
                + "}"
                + "</style>")
    }

    /**
     * Returns the font size chosen by the user
     * @param translationType
     * @return
     */
    fun getFontSize(translationType: TranslationType): Float {
        val typefaceSize = if ((translationType == TranslationType.SOURCE)) {
            SettingsActivity.KEY_PREF_SOURCE_TYPEFACE_SIZE
        } else {
            SettingsActivity.KEY_PREF_TRANSLATION_TYPEFACE_SIZE
        }
        return prefRepository.getDefaultPref(
            typefaceSize,
            context.resources.getString(R.string.pref_default_typeface_size)
        ).toFloat()
    }

    /**
     * Returns the path to the font asset
     * @param translationType
     * @return
     */
    fun getAssetPath(translationType: TranslationType): String {
        val selectedTypeface = if ((translationType == TranslationType.SOURCE)) {
            SettingsActivity.KEY_PREF_SOURCE_TYPEFACE
        } else {
            SettingsActivity.KEY_PREF_TRANSLATION_TYPEFACE
        }
        val fontName = prefRepository.getDefaultPref(
            selectedTypeface,
            context.resources.getString(R.string.pref_default_translation_typeface)
        )
        return "assets/fonts/$fontName"
    }

    /**
     * Returns the typeface chosen by the user
     * @param translationType
     * @param languageCode the spoken language
     * @param direction the reading direction
     * @return
     */
    fun getTypeface(
        translationType: TranslationType,
        languageCode: String?,
        direction: String?
    ): Typeface {
        val selectedTypeface = if ((translationType == TranslationType.SOURCE)) {
            SettingsActivity.KEY_PREF_SOURCE_TYPEFACE
        } else {
            SettingsActivity.KEY_PREF_TRANSLATION_TYPEFACE
        }
        val fontName = prefRepository.getDefaultPref(
            selectedTypeface,
            context.resources.getString(R.string.pref_default_translation_typeface)
        )

        val typeface = getTypeface(translationType, fontName, languageCode, direction)
        return typeface
    }

    /**
     * Returns the typeface by font name
     * @param translationType
     * @param languageCode the spoken language
     * @param direction the reading direction
     * @return
     */
    fun getTypeface(
        translationType: TranslationType?,
        fontName: String?,
        languageCode: String?,
        direction: String?
    ): Typeface {
        // TODO: provide graphite support
//        File fontFile = new File(context.getCacheDir(), "assets/fonts" + fontName);
//        if(!fontFile.exists()) {
//            fontFile.getParentFile().mkdirs();
//            try {
//                Util.writeStream(context.getResourceSlugs().getAssets().open("fonts/" + fontName), fontFile);
//            } catch (Exception e) {
//                e.printStackTrace();
//                return;
//            }
//        }
//        if (sEnableGraphite) {
//            TTFAnalyzer analyzer = new TTFAnalyzer();
//            String fontname = analyzer.getTtfFontName(font.getAbsolutePath());
//            if (fontname != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO && Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
//                // assets container, font asset, font name, rtl, language, feats (what's this for????)
//                int translationRTL = l.getDirection() == Language.Direction.RightToLeft ? 1 : 0;
//                try {
//                            customTypeface = (Typeface) Graphite.addFontResource(mContext.getAssets(), "fonts/" + typeFace, fontname, translationRTL, l.getId(), "");
//                } catch (Exception e) {
//                    e.printStackTrace();
//                    customTypeface = Typeface.createFromFile(font);
//                }
//            } else {
//                customTypeface = Typeface.createFromFile(font);
//            }
//        }

        var typeface = Typeface.DEFAULT
        try {
            typeface = Typeface.createFromAsset(context.assets, "fonts/$fontName")
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return typeface
    }

    /**
     * get the font to use for language code. This is the font to be used in tabs and language lists.
     *
     * @param translationType
     * @param code
     * @param direction
     * @return Typeface for font, or Typeface.DEFAULT on error
     */
    fun getBestFontForLanguage(
        translationType: TranslationType?,
        code: String?,
        direction: String?
    ): Typeface? {
        // substitute language font by lookup
        if (languageSubstituteFonts == null) {
            try {
                languageSubstituteFonts = JSONObject(languageSubstituteFontsJson)
                val defaultSubstituteFont = languageSubstituteFonts!!.optString("default", null)
                defaultLanguageTypeface =
                    getTypeface(translationType, defaultSubstituteFont, code, direction)
            } catch (e: Exception) {
            }
        }
        if (languageSubstituteFonts != null) {
            val substituteFont = languageSubstituteFonts!!.optString(code, "")
            return if (substituteFont.isNotEmpty()) {
                getTypeface(translationType, substituteFont, code, direction)
            } else {
                defaultLanguageTypeface
            }
        }
        return Typeface.DEFAULT
    }
}
