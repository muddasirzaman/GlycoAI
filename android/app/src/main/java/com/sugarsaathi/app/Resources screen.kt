package com.sugarsaathi.app

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Trusted medical resources and clinical research links.
 *
 * Every URL here is a real organisation homepage, hard-coded and never
 * constructed at runtime. Deep links into specific articles are deliberately
 * avoided: they rot, and a dead link on a health app reads as carelessness
 * about the whole thing. Homepages stay valid for decades.
 *
 * Organisation names are NOT translated - they are registered institutional
 * names. The descriptions ARE translated, since those are ordinary prose.
 *
 * This screen deliberately carries no medical content of its own. It points
 * outward. That keeps GlycoAI out of the business of authoring clinical
 * claims it cannot review or maintain.
 */

private data class ResourceItem(
    val name: String,
    val url: String,
    val descRes: Int
)

// Official homepages. Verified organisation domains, not deep links.
private val TRUSTED_ORGS = listOf(
    ResourceItem(
        "International Diabetes Federation (IDF)",
        "https://idf.org",
        R.string.res_idf_desc
    ),
    ResourceItem(
        "World Health Organization (WHO)",
        "https://www.who.int",
        R.string.res_who_desc
    ),
    ResourceItem(
        "American Diabetes Association (ADA)",
        "https://diabetes.org",
        R.string.res_ada_desc
    ),
    ResourceItem(
        "Centers for Disease Control and Prevention (CDC)",
        "https://www.cdc.gov/diabetes",
        R.string.res_cdc_desc
    ),
    ResourceItem(
        "Mayo Clinic",
        "https://www.mayoclinic.org",
        R.string.res_mayo_desc
    )
)

private val RESEARCH_SOURCES = listOf(
    ResourceItem(
        "PubMed",
        "https://pubmed.ncbi.nlm.nih.gov",
        R.string.res_pubmed_desc
    ),
    ResourceItem(
        "Cochrane Library",
        "https://www.cochranelibrary.com",
        R.string.res_cochrane_desc
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResourcesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val cannotOpen = stringResource(R.string.res_cannot_open)

    fun open(url: String) {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        } catch (e: Exception) {
            // No browser installed, or the intent was blocked. Tell the user
            // rather than failing silently - a tap that does nothing looks
            // like the app is broken.
            Toast.makeText(context, cannotOpen, Toast.LENGTH_SHORT).show()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        TopAppBar(
            title = { Text(stringResource(R.string.resources_title), fontSize = 18.sp) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = TealGreen,
                titleContentColor = Color.White,
                navigationIconContentColor = Color.White
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {

            Text(
                stringResource(R.string.resources_intro),
                fontSize = 13.sp,
                color = Color.DarkGray,
                lineHeight = 19.sp
            )

            Spacer(Modifier.height(20.dp))

            // ── Trusted medical organisations ──
            SectionHeading("🌐", stringResource(R.string.resources_trusted_heading))
            Spacer(Modifier.height(10.dp))

            TRUSTED_ORGS.forEach { item ->
                ResourceCard(
                    name = item.name,
                    description = stringResource(item.descRes),
                    url = item.url,
                    onClick = { open(item.url) }
                )
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(14.dp))

            // ── Clinical research ──
            SectionHeading("📚", stringResource(R.string.resources_research_heading))
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.resources_research_intro),
                fontSize = 12.sp,
                color = Color.Gray,
                lineHeight = 17.sp
            )
            Spacer(Modifier.height(10.dp))

            RESEARCH_SOURCES.forEach { item ->
                ResourceCard(
                    name = item.name,
                    description = stringResource(item.descRes),
                    url = item.url,
                    onClick = { open(item.url) }
                )
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(16.dp))

            // Disclaimer. Placed at the end rather than the top so it reads
            // as a closing note, not a warning gate the user must get past.
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    stringResource(R.string.resources_disclaimer),
                    fontSize = 12.sp,
                    color = Color(0xFF616161),
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(14.dp)
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionHeading(emoji: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 18.sp)
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0D5A44)
        )
    }
}

/**
 * One tappable resource. The whole card is the touch target rather than just
 * the URL text - a small link is hard to hit accurately, and older patients
 * are a real part of this app's audience.
 */
@Composable
private fun ResourceCard(
    name: String,
    description: String,
    url: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.5.dp, Color.LightGray),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0D5A44),
                    lineHeight = 19.sp
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    description,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    lineHeight = 17.sp
                )
                Spacer(Modifier.height(5.dp))
                // Showing the URL matters: a patient can verify where a tap
                // will take them before tapping, and can type it manually on
                // another device if they prefer.
                Text(
                    url.removePrefix("https://"),
                    fontSize = 11.sp,
                    color = TealGreen,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.width(10.dp))
            Icon(
                Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = TealGreen,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}