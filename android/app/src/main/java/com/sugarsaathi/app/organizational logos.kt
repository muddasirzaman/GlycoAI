package com.sugarsaathi.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Official names, kept in English under both language settings - these are
// registered institutional names, not translatable prose.
private const val PCSIR_FULL =
    "Pakistan Council of Scientific and Industrial Research (PCSIR)"
private const val MOST_FULL =
    "Ministry of Science and Technology, Government of Pakistan"

// Matches the deep teal already used across the app's cards and headings.
private val BrandDeepTeal = Color(0xFF0D5A44)

/**
 * Institutional branding block: PCSIR on the left, Ministry of Science and
 * Technology on the right, with the collaboration statement beneath.
 *
 * Both source images are white-background JPEGs, so they sit inside a single
 * white card. That keeps them legible on any background color and stops the
 * two logos - which have very different visual weight - from looking mismatched.
 *
 * Sizing is responsive: logo height scales with available width but is clamped,
 * so it stays elegant on a small phone and does not balloon on a tablet.
 * Aspect ratio is preserved via ContentScale.Fit; nothing is stretched.
 *
 * @param showNames when false, only the logos and the short label appear. Use
 *                  on very short screens where the full statement would push
 *                  other content off the display.
 */
@Composable
fun OrganizationLogos(
    modifier: Modifier = Modifier,
    minLogoSize: Dp = 46.dp,
    maxLogoSize: Dp = 68.dp,
    showNames: Boolean = true
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {

        // Roughly 18% of available width, clamped to a sensible range.
        val logoSize = (maxWidth * 0.18f).coerceIn(minLogoSize, maxLogoSize)

        // Text scales gently with the card so the block stays proportionate.
        val nameSize = if (maxWidth < 340.dp) 10.5f else 11.5f
        val labelSize = nameSize - 1.5f

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            tonalElevation = 0.dp,
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Equal-size boxes guarantee both logos occupy identical
                    // space regardless of their own aspect ratios.
                    Image(
                        painter = painterResource(R.drawable.pcsir_logo),
                        contentDescription = PCSIR_FULL,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(logoSize)
                    )

                    Spacer(Modifier.width(26.dp))

                    Box(
                        modifier = Modifier
                            .height(logoSize * 0.6f)
                            .width(1.dp)
                            .background(Color(0xFFE0E0E0))
                    )

                    Spacer(Modifier.width(26.dp))

                    Image(
                        painter = painterResource(R.drawable.ministry_logo),
                        contentDescription = MOST_FULL,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(logoSize)
                    )
                }

                Spacer(Modifier.height(12.dp))

                // The label is deliberately the smallest element here - the
                // organization names carry the emphasis.
                Text(
                    text = stringResource(R.string.developed_in_collaboration),
                    fontSize = labelSize.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF757575),
                    textAlign = TextAlign.Center,
                    lineHeight = (labelSize + 4).sp
                )

                if (showNames) {
                    Spacer(Modifier.height(6.dp))

                    Text(
                        text = PCSIR_FULL,
                        fontSize = nameSize.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandDeepTeal,
                        textAlign = TextAlign.Center,
                        lineHeight = (nameSize + 4).sp
                    )

                    Spacer(Modifier.height(2.dp))

                    Text(
                        text = "&",
                        fontSize = labelSize.sp,
                        color = Color(0xFF9E9E9E),
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(2.dp))

                    Text(
                        text = MOST_FULL,
                        fontSize = nameSize.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = BrandDeepTeal,
                        textAlign = TextAlign.Center,
                        lineHeight = (nameSize + 4).sp
                    )
                }
            }
        }
    }
}