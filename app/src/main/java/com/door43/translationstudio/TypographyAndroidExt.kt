@file:JvmName("TypographyUtils")
package com.door43.translationstudio

import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import com.door43.data.AssetsProvider
import com.door43.translationstudio.core.TextStyleType
import com.door43.translationstudio.core.TranslationType
import com.door43.translationstudio.core.Typography

/**
 * Extension functions to apply pure KMP Typography configurations
 * to legacy Android TextViews.
 */

fun TextView.applyTypography(
    typography: Typography,
    assetsProvider: AssetsProvider,
    translationType: TranslationType,
    style: TextStyleType = TextStyleType.NORMAL,
    languageCode: String? = null,
    direction: String? = null
) {
    val config = typography.getFormatConfig(translationType, style, languageCode, direction)

    var typeface = Typeface.DEFAULT
    try {
        typeface = Typeface.createFromAsset(assetsProvider.manager, config.fontAssetPath)
    } catch (e: Exception) {
        e.printStackTrace()
    }

    this.setTypeface(typeface, if (config.isBold) Typeface.BOLD else Typeface.NORMAL)
    this.setTextSize(TypedValue.COMPLEX_UNIT_SP, config.fontSizeSp)

    if (config.isRtl) {
        this.textDirection = View.TEXT_DIRECTION_ANY_RTL
    } else {
        this.textDirection = View.TEXT_DIRECTION_LTR
    }
}

fun TextView.format(
    typography: Typography,
    provider: AssetsProvider,
    type: TranslationType,
    lang: String?,
    dir: String?
) = applyTypography(
    typography,
    provider,
    type,
    TextStyleType.NORMAL,
    lang,
    dir
)

fun TextView.formatTitle(
    typography: Typography,
    provider: AssetsProvider,
    type: TranslationType,
    lang: String?,
    dir: String?
) = applyTypography(
    typography,
    provider,
    type,
    TextStyleType.TITLE,
    lang,
    dir
)

fun TextView.formatSub(
    typography: Typography,
    provider: AssetsProvider,
    type: TranslationType,
    lang: String?,
    dir: String?
) = applyTypography(
    typography,
    provider,
    type,
    TextStyleType.SUB,
    lang,
    dir
)

fun getBestFontForLanguage(
    typography: Typography,
    assetsProvider: AssetsProvider,
    languageCode: String?
): Typeface {
    val fontPath = typography.getBestFontPathForLanguage(languageCode)

    return try {
        Typeface.createFromAsset(assetsProvider.manager, fontPath)
    } catch (e: Exception) {
        e.printStackTrace()
        Typeface.DEFAULT
    }
}