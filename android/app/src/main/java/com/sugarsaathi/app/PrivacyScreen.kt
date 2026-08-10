package com.sugarsaathi.app

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyScreen(
    onBack: () -> Unit,
    onAccountDeleted: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isExporting by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showReauthNotice by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy & My Data") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {

            PrivacySection(
                title = "Where your data lives",
                body = "Your profile, glucose readings, and chat history are " +
                        "stored only on this device. GlycoAI's backend does not " +
                        "keep a database - each message is processed and forgotten " +
                        "once you get a reply. Your Firebase account only handles " +
                        "phone-number sign-in."
            )

            PrivacySection(
                title = "What gets sent to the AI",
                body = "Every message includes basic details (age, diabetes " +
                        "type, allergies) plus your 7-day glucose summary and " +
                        "anything you've told the assistant before. Details like " +
                        "your medications, other conditions, HbA1c, or complications " +
                        "are only included when your question is actually related " +
                        "to them - a general question like \"what is diabetes\" " +
                        "shares far less than \"can I eat this with my kidney " +
                        "condition\". Your doctor's name and emergency contact are " +
                        "never sent to the AI."
            )

            HorizontalDivider()

            Text("Export my data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Download everything stored about you as a single file you can keep or share with your doctor.",
                style = MaterialTheme.typography.bodyMedium
            )
            Button(
                onClick = {
                    isExporting = true
                    errorMessage = null
                    scope.launch {
                        try {
                            val file = PrivacyDataManager.exportData(context)
                            val intent = PrivacyDataManager.shareIntentFor(context, file)
                            context.startActivity(Intent.createChooser(intent, "Share my data"))
                        } catch (e: Exception) {
                            errorMessage = "Export failed: ${e.message}"
                        } finally {
                            isExporting = false
                        }
                    }
                },
                enabled = !isExporting,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isExporting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (isExporting) "Preparing export..." else "Export my data")
            }

            HorizontalDivider()

            Text(
                "Delete my account & data",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                "Permanently deletes your profile, glucose readings, chat history, " +
                        "and sign-in account from this device. This cannot be undone.",
                style = MaterialTheme.typography.bodyMedium
            )
            Button(
                onClick = { showDeleteConfirm = true },
                enabled = !isDeleting,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isDeleting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onError
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (isDeleting) "Deleting..." else "Delete my account & data")
            }

            errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete everything?") },
            text = {
                Text(
                    "This permanently deletes your profile, all glucose readings, " +
                            "your full chat history, and your account. There is no way " +
                            "to undo this."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    isDeleting = true
                    errorMessage = null
                    scope.launch {
                        when (val result = PrivacyDataManager.deleteAllData(context)) {
                            is PrivacyDataManager.DeleteResult.Success -> {
                                isDeleting = false
                                onAccountDeleted()
                            }
                            is PrivacyDataManager.DeleteResult.LocalDeletedNeedsReauth -> {
                                isDeleting = false
                                showReauthNotice = true
                            }
                            is PrivacyDataManager.DeleteResult.Failure -> {
                                isDeleting = false
                                errorMessage = result.message
                            }
                        }
                    }
                }) {
                    Text("Delete everything", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    if (showReauthNotice) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Data deleted") },
            text = {
                Text(
                    "Your profile, readings, and chat history are gone from this " +
                            "device. To finish removing your account, please sign in " +
                            "once more and delete again from this screen."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showReauthNotice = false
                    onAccountDeleted() // drop to sign-in; local data is already gone
                }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
private fun PrivacySection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}