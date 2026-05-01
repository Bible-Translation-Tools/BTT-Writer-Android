package com.door43.translationstudio.core

import com.door43.data.IPreferenceRepository
import com.door43.data.getDefaultPref

/**
 * Created by mxaln on 2/25/2026.
 */
data class TextFormatConfig(
    val fontAssetPath: String,
    val fontSizeSp: Float,
    val isBold: Boolean = false,
    val directionString: String? = null
) {
    val isRtl: Boolean get() = directionString.equals("rtl", ignoreCase = true)
}

enum class TextStyleType(val sizeMultiplier: Float) {
    NORMAL(1.0f),
    TITLE(1.3f),
    SUB(0.7f),
    TAB(0.5f)
}

class Typography(
    private val prefRepository: IPreferenceRepository,
    private val defaultTranslationTypeface: String,
    private val defaultTypefaceSize: String
) {
    private val languageSubstituteFonts = mapOf(
        "default" to "NotoSansMultiLanguage-Regular.ttf"
        // "gu" to "NotoSansGuLanguage-Regular.ttf"
    )

    fun getFormatConfig(
        translationType: TranslationType,
        style: TextStyleType = TextStyleType.NORMAL,
        languageCode: String? = null,
        direction: String? = null
    ): TextFormatConfig {
        val baseFontSize = getFontSize(translationType)
        val fontName = languageSubstituteFonts[languageCode] ?: getFontName(translationType)

        return TextFormatConfig(
            fontAssetPath = "fonts/$fontName",
            fontSizeSp = baseFontSize * style.sizeMultiplier,
            isBold = style == TextStyleType.TITLE,
            directionString = direction
        )
    }

    fun getFontSize(translationType: TranslationType): Float {
        val prefKey = if (translationType == TranslationType.SOURCE) {
            IPreferenceRepository.KEY_PREF_SOURCE_TYPEFACE_SIZE
        } else {
            IPreferenceRepository.KEY_PREF_TRANSLATION_TYPEFACE_SIZE
        }
        return prefRepository.getDefaultPref(
            prefKey,
            defaultTypefaceSize
        ).toFloat()
    }

    private fun getFontName(translationType: TranslationType): String {
        val prefKey = if (translationType == TranslationType.SOURCE) {
            IPreferenceRepository.KEY_PREF_SOURCE_TYPEFACE
        } else {
            IPreferenceRepository.KEY_PREF_TRANSLATION_TYPEFACE
        }
        return prefRepository.getDefaultPref(prefKey, defaultTranslationTypeface)
    }

    fun getBestFontPathForLanguage(languageCode: String?): String {
        val fontName = languageSubstituteFonts[languageCode] ?: languageSubstituteFonts["default"]
        return "fonts/$fontName"
    }

    fun getAssetPath(translationType: TranslationType): String {
        return "assets/fonts/${getFontName(translationType)}"
    }

    fun getStyle(translationType: TranslationType): String {
        return "<style type=\"text/css\">body { font-size: ${getFontSize(translationType)}; }</style>"
    }
}
