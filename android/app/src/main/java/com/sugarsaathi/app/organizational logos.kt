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

/**
 * Institutional branding strip: PCSIR on the left, Ministry of Science and
 * Technology on the right, equally sized and evenly spaced.
 *
 * Both source images are white-background JPEGs, so they sit inside a single
 * white card. That keeps them legible on any background colour and stops the
 * two logos - which have very different visual weight - from looking mismatched.
 *
 * Sizing is responsive: the logo height scales with available width but is
 * clamped, so it stays elegant on a small phone and does not balloon on a
 * tablet. Aspect ratio is preserved via ContentScale.Fit; nothing is stretched.
 *
 * @param caption small label above the logos. Pass null to hide it.
 *                CHECK THE WORDING against your actual relationship with
 *                these organisations before shipping.
 */
@Composable
fun OrganizationLogos(
    modifier: Modifier = Modifier,
    caption: String? = stringResource(R.string.in_collaboration_with),
    minLogoSize: Dp = 40.dp,
    maxLogoSize: Dp = 60.dp
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {

        // Roughly 16% of available width, clamped to a sensible range.
        val logoSize = (maxWidth * 0.16f).coerceIn(minLogoSize, maxLogoSize)

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            tonalElevation = 0.dp,
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                if (!caption.isNullOrBlank()) {
                    Text(
                        text = caption,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(10.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Equal-size boxes guarantee both logos occupy identical
                    // space regardless of their own aspect ratios.
                    Image(
                        painter = painterResource(R.drawable.pcsir_logo),
                        contentDescription =
                            "Pakistan Council of Scientific and Industrial Research",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(logoSize)
                    )

                    Spacer(Modifier.width(28.dp))

                    // Hairline separator keeps the pairing deliberate rather
                    // than accidental. Remove if you prefer pure whitespace.
                    Box(
                        modifier = Modifier
                            .height(logoSize * 0.6f)
                            .width(1.dp)
                            .background(Color(0xFFE0E0E0))
                    )

                    Spacer(Modifier.width(28.dp))

                    Image(
                        painter = painterResource(R.drawable.ministry_logo),
                        contentDescription =
                            "Ministry of Science and Technology, Government of Pakistan",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(logoSize)
                    )
                }
            }
        }
    }
}

/**
 * Compact variant with no card or caption - for screens that already sit on a
 * white background and only need the two marks.
 */
@Composable
fun OrganizationLogosCompact(
    modifier: Modifier = Modifier,
    logoSize: Dp = 40.dp
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.pcsir_logo),
            contentDescription =
                "Pakistan Council of Scientific and Industrial Research",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(logoSize)
        )
        Spacer(Modifier.width(24.dp))
        Image(
            painter = painterResource(R.drawable.ministry_logo),
            contentDescription =
                "Ministry of Science and Technology, Government of Pakistan",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(logoSize)
        )
    }
}