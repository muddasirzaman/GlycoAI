package com.sugarsaathi.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
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


/**
 * Renders chat message text with any http(s) links turned into tappable,
 * underlined, coloured links that open the device browser directly.
 *
 * Drop-in replacement for wherever you currently do something like:
 *     Text(text = message.content)
 * inside your chat bubble composable. Just swap that line for:
 *     LinkifiedMessageText(text = message.content)
 *
 * Urdu / RTL: the URL pattern only matches Latin-script link characters,
 * so a URL sitting next to Urdu text won't accidentally "swallow" the
 * Urdu characters into the clickable span.
 */

// Matches http:// or https:// links. Stops at whitespace, brackets,
// quotes, and Urdu-range characters, so it doesn't over-match.
private val URL_REGEX = Regex("""https?://[^\s<>()\[\]"'\u0600-\u06FF]+""")

@Composable
fun LinkifiedMessageText(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = LocalContentColor.current,
    linkColor: Color = Color(0xFF1565C0), // swap this for your app's teal accent if you'd like
    fontSize: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
) {
    val context = LocalContext.current

    val annotatedText = remember(text, linkColor) {
        buildAnnotatedString {
            var lastIndex = 0

            for (match in URL_REGEX.findAll(text)) {
                val url = match.value

                // plain text before this link
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
                        linkInteractionListener = {
                            openInBrowser(context, url)
                        }
                    )
                ) {
                    append(url)
                }

                lastIndex = match.range.last + 1
            }

            // any remaining plain text after the last link
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

/**
 * Opens the link in the device's default browser.
 * If nothing can handle it (extremely rare — e.g. no browser installed),
 * shows a friendly message instead of crashing.
 */
private fun openInBrowser(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app found to open this link.", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "This link couldn't be opened.", Toast.LENGTH_SHORT).show()
    }
}

// --- About the "long-press: Copy / Open / Share" menu from your spec ---
//
// This isn't included above on purpose. Your docs mention chat messages
// are already selectable text (Section 9.1 — SelectionContainer, most
// likely). There's a known conflict in Compose where wrapping links in
// SelectionContainer makes long-press either open the browser immediately
// or fight with text-selection handles, instead of showing a custom menu.
//
// Two ways to handle it once I see your actual message composable:
//   1. Simple version (recommended): keep tap-to-open only. Reuse the
//      "copy" button you already built per message (Section 9.1) to cover
//      the copy need — no new UI, no gesture conflicts.
//   2. Full version: build a custom long-press menu, but it needs real
//      on-device testing against your existing SelectionContainer to make
//      sure the two don't fight each other.
//
// Let me know which you'd like once we're looking at the real file.