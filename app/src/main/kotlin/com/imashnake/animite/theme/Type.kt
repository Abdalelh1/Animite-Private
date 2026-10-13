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
import com.amimena.app.R

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

fun createAnimiteTypography(fontFamily: FontFamily) = Typography(
    // - [AnimeScreen, MangaScreen]: Media list headings.
    // - [MediaPage]: Media section headings.
    titleMedium = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        letterSpacing = 0.2.sp,
        baselineShift = BaselineShift(BASELINE_SHIFT)
    ),
    // - [AnimeScreen, MangaScreen]: Media list item labels.
    // - [MediaPage]: Character names.
    labelLarge = TextStyle(
        fontSize = 12.sp,
        fontFamily = fontFamily,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 18.sp,
        baselineShift = BaselineShift(BASELINE_SHIFT)
    ),
    // - [MediaPage]: Media title.
    titleLarge = TextStyle(
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        letterSpacing = 0.2.sp,
        baselineShift = BaselineShift(BASELINE_SHIFT)
    ),
    // - [MediaPage]: Media description.
    // - [SearchItem]: Search list item titles.
    // - [SearchItem]: Search list item studios.
    // - [SearchItem]: Search list item footer.
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        lineHeight = 22.sp,
        baselineShift = BaselineShift(BASELINE_SHIFT)
    ),
    // - [MediaPage]: Stat label.
    // - [SearchItem]: Search list item season and year.
    labelSmall = TextStyle(
        fontSize = 12.sp,
        fontFamily = fontFamily,
        fontWeight = FontWeight.Medium,
        baselineShift = BaselineShift(BASELINE_SHIFT)
    ),
    // - [MediaPage]: Stat score.
    displaySmall = TextStyle(
        fontSize = 24.sp,
        fontFamily = fontFamily,
        fontWeight = FontWeight.Bold,
        baselineShift = BaselineShift(BASELINE_SHIFT)
    ),
)

val AnimiteTypography = createAnimiteTypography(manropeFontFamily)
val CairoTypography = createAnimiteTypography(cairoFontFamily)
