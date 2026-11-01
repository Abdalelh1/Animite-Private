package com.imashnake.animite.social.ui.auth

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlin.math.roundToInt

private data class HeroPoster(
    val title: String,
    val colors: List<Color>,
)

private val heroColumns: List<List<HeroPoster>> = listOf(
    listOf(
        HeroPoster("بلاك كلوفر", listOf(Color(0xFF27407A), Color(0xFF7A2E1F))),
        HeroPoster("مذكرات الصيدلانية", listOf(Color(0xFF0E3B2E), Color(0xFF1F7A5E))),
        HeroPoster("فرييرين", listOf(Color(0xFF3B1D5E), Color(0xFF8B5CF6))),
        HeroPoster("المحقق كونان", listOf(Color(0xFF123B5E), Color(0xFFD64545))),
    ),
    listOf(
        HeroPoster("هجوم العمالقة", listOf(Color(0xFF5E1D1D), Color(0xFFD64545))),
        HeroPoster("سولو ليفلينغ", listOf(Color(0xFF0B2A5E), Color(0xFF1F6AD6))),
        HeroPoster("هنتر × هنتر", listOf(Color(0xFF3A2A0E), Color(0xFFB8860B))),
        HeroPoster("دراغون بول", listOf(Color(0xFF7A2E1F), Color(0xFFF59E0B))),
    ),
    listOf(
        HeroPoster("ون بيس", listOf(Color(0xFF123B5E), Color(0xFF38BDF8))),
        HeroPoster("جوجوتسو كايسن", listOf(Color(0xFF4A1030), Color(0xFFE14A8A))),
        HeroPoster("ناروتو", listOf(Color(0xFF20301A), Color(0xFF7AB648))),
        HeroPoster("ون بنش مان", listOf(Color(0xFF2E2A4A), Color(0xFFE1C84A))),
    ),
)

private val heroPosterWidth = 96.dp
private val heroPosterHeight = 132.dp
private val heroSpacing = 10.dp

/**
 * Animated welcome hero: three tilted columns of anime posters slowly
 * scrolling in alternating directions, fading into the background at the
 * bottom, with the app logo on top — AniSync-style welcome header.
 *
 * When [coverUrls] are provided they are used as poster images (in order);
 * otherwise gradient placeholders with titles are drawn.
 */
@Composable
fun WelcomeCollageHero(
    coverUrls: List<String> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val background = MaterialTheme.colorScheme.background

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(340.dp)
            .background(background)
            .clipToBounds(),
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    rotationZ = -6f
                    scaleX = 1.22f
                    scaleY = 1.22f
                },
            horizontalArrangement = Arrangement.spacedBy(heroSpacing),
        ) {
            heroColumns.forEachIndexed { columnIndex, column ->
                HeroColumn(
                    posters = column,
                    coverUrls = column.indices.map { rowIndex ->
                        coverUrls.getOrNull(columnIndex * column.size + rowIndex)
                    },
                    reverse = columnIndex % 2 == 1,
                )
            }
        }

        // Bottom fade into the screen background.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(150.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, background),
                    ),
                ),
        )

        // App logo over the collage.
        Surface(
            shape = RoundedCornerShape(percent = 50),
            color = Color.Black.copy(alpha = 0.45f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(9.dp),
                    color = Color.White,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = Color(0xFF0A0C10),
                        modifier = Modifier
                            .padding(5.dp)
                            .size(18.dp),
                    )
                }
                Text(
                    text = "Animite",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}

@Composable
private fun HeroColumn(
    posters: List<HeroPoster>,
    coverUrls: List<String?>,
    reverse: Boolean,
    modifier: Modifier = Modifier,
) {
    val loopHeight = (heroPosterHeight + heroSpacing) * posters.size
    val loopPx = with(LocalDensity.current) { loopHeight.toPx() }

    val transition = rememberInfiniteTransition(label = "welcomeHeroColumn")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 34_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "welcomeHeroProgress",
    )
    val offsetPx = if (reverse) -loopPx * (1f - progress) else -loopPx * progress

    Column(
        modifier = modifier.offset { IntOffset(0, offsetPx.roundToInt()) },
        verticalArrangement = Arrangement.spacedBy(heroSpacing),
    ) {
        (posters + posters).forEachIndexed { index, poster ->
            HeroPosterCard(
                poster = poster,
                coverUrl = coverUrls[index % posters.size],
            )
        }
    }
}

@Composable
private fun HeroPosterCard(
    poster: HeroPoster,
    coverUrl: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(width = heroPosterWidth, height = heroPosterHeight)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.verticalGradient(poster.colors)),
    ) {
        if (coverUrl != null) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = poster.title,
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
            )
        }
    }
}
