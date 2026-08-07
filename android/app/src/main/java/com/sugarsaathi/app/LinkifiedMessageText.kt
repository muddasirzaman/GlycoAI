package com.sugarsaathi.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.TextUnit
import androidx.core.net.toUri

// Excludes Arabic/Urdu range so a URL followed by Urdu text does not swallow it.
private val URL_REGEX = Regex("""https?://[^\s<>()\[\]"'\u0600-\u06FF]+""")

/**
 * Renders text with any bare URLs turned into tappable links.
 *
 * Currently unused: MessageBubble routes assistant replies through MarkdownText
 * instead. Kept because the backend is allowed to name organisation homepages
 * (diabetes.org, who.int, idf.org and so on) - if MarkdownText does not make
 * those tappable, this is the component that should render them.
 * Either wire it up or delete the file; leaving it half-connected is the worst
 * of the three options.
 */
@Suppress("unused")
@Composable
fun LinkifiedMessageText(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = LocalContentColor.current,
    linkColor: Color = Color(0xFF1565C0),
    fontSize: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
) {
    val context = LocalContext.current
    val annotatedText = remember(text, linkColor) {
        buildAnnotatedString {
            var lastIndex = 0
            for (match in URL_REGEX.findAll(text)) {
                val url = match.value
                append(text.substring(lastIndex, match.range.first))
                withLink(
                    LinkAnnotation.Url(
                        url = url,
                        styles = TextLinkStyles(
                            style = SpanStyle(
                                color = linkColor,
                                textDecoration = TextDecoration.Underline
                            )
                        ),
                        linkInteractionListener = { openInBrowser(context, url) }
                    )
                ) {
                    append(url)
                }
                lastIndex = match.range.last + 1
            }
            append(text.substring(lastIndex))
        }
    }
    Text(
        text = annotatedText,
        modifier = modifier,
        color = textColor,
        fontSize = fontSize,
        lineHeight = lineHeight
    )
}

private fun openInBrowser(context: Context, url: String) {
    try {
        // toUri() is the androidx.core KTX extension for Uri.parse
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No app found to open this link.", Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {
        Toast.makeText(context, "This link couldn't be opened.", Toast.LENGTH_SHORT).show()
    }
}