package com.imashnake.animite.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.unit.sp
import com.animeta.app.R

internal val fontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

internal val manropeFont = GoogleFont("Manrope")
internal val cairoFont = GoogleFont("Cairo")

/**
 * [Manrope](https://fonts.google.com/specimen/Manrope).
 */
val manropeFontFamily = FontFamily(
    Font(manropeFont, fontProvider, FontWeight.ExtraBold),
    Font(manropeFont, fontProvider, FontWeight.Bold),
    Font(manropeFont, fontProvider, FontWeight.SemiBold),
    Font(manropeFont, fontProvider, FontWeight.Medium),
    Font(manropeFont, fontProvider, FontWeight.Normal),
    Font(manropeFont, fontProvider, FontWeight.Light),
    Font(manropeFont, fontProvider, FontWeight.ExtraLight),
    Font(R.font.manrope_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.manrope_bold_italic, FontWeight.Bold, FontStyle.Italic),
    Font(R.font.manrope_extra_bold_italic, FontWeight.ExtraBold, FontStyle.Italic),
    Font(R.font.manrope_light_italic, FontWeight.Light, FontStyle.Italic),
    Font(R.font.manrope_medium_italic, FontWeight.Medium, FontStyle.Italic),
    Font(R.font.manrope_extra_light_italic, FontWeight.ExtraLight, FontStyle.Italic),
    Font(R.font.manrope_semi_bold_italic, FontWeight.SemiBold, FontStyle.Italic),
    Font(R.font.manrope) // fallback?
)

/**
 * [Cairo](https://fonts.google.com/specimen/Cairo).
 */
val cairoFontFamily = FontFamily(
    Font(cairoFont, fontProvider, FontWeight.ExtraBold),
    Font(cairoFont, fontProvider, FontWeight.Bold),
    Font(cairoFont, fontProvider, FontWeight.SemiBold),
    Font(cairoFont, fontProvider, FontWeight.Medium),
    Font(cairoFont, fontProvider, FontWeight.Normal),
    Font(cairoFont, fontProvider, FontWeight.Light),
    Font(cairoFont, fontProvider, FontWeight.ExtraLight),
)

const val BASELINE_SHIFT = 0.2f

fun createAnimiteTypography(
    fontFamily: FontFamily,
    baselineShift: Float = BASELINE_SHIFT,
    isArabic: Boolean = false,
) = Typography(
    // - [AnimeScreen, MangaScreen]: Media list headings.
    // - [MediaPage]: Media section headings.
    titleMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        letterSpacing = if (isArabic) 0.sp else 0.2.sp,
        lineHeight = if (isArabic) 22.sp else 20.sp,
        baselineShift = BaselineShift(baselineShift)
    ),
    // - [AnimeScreen, MangaScreen]: Media list item labels.
    // - [MediaPage]: Character names.
    labelLarge = TextStyle(
        fontSize = 12.sp,
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        lineHeight = if (isArabic) 20.sp else 18.sp,
        baselineShift = BaselineShift(baselineShift)
    ),
    // - [MediaPage]: Media title.
    titleLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        letterSpacing = if (isArabic) 0.sp else 0.2.sp,
        lineHeight = if (isArabic) 24.sp else 22.sp,
        baselineShift = BaselineShift(baselineShift)
    ),
    // - [MediaPage]: Media description.
    // - [SearchItem]: Search list item titles.
    // - [SearchItem]: Search list item studios.
    // - [SearchItem]: Search list item footer.
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        lineHeight = if (isArabic) 24.sp else 22.sp,
        baselineShift = BaselineShift(baselineShift)
    ),
    // - [MediaPage]: Stat label.
    // - [SearchItem]: Search list item season and year.
    labelSmall = TextStyle(
        fontSize = 12.sp,
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        lineHeight = if (isArabic) 18.sp else 16.sp,
        baselineShift = BaselineShift(baselineShift)
    ),
    // - [MediaPage]: Stat score.
    displaySmall = TextStyle(
        fontSize = 24.sp,
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        lineHeight = if (isArabic) 32.sp else 28.sp,
        baselineShift = BaselineShift(baselineShift)
    ),
)

val AnimiteTypography = createAnimiteTypography(manropeFontFamily, BASELINE_SHIFT, isArabic = false)
val CairoTypography = createAnimiteTypography(cairoFontFamily, 0.0f, isArabic = true)
