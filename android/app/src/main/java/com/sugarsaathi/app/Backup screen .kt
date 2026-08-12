package com.sugarsaathi.app

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Backup and Restore screen.
 *
 * Two buttons, one warning, no autopilot. Manual only, per the design choice
 * the patient made when setting this up. Chat history is deliberately NOT
 * in the backup - the warning below reflects only what the file DOES carry.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var busy by remember { mutableStateOf(false) }
    var pendingImport by remember {
        mutableStateOf<Pair<BackupEnvelope, BackupPreview>?>(null)
    }

    // File picker for import. Any file type; we only trust the JSON shape,
    // not the extension - so this is the loose filter.
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val parsed = withContext(Dispatchers.IO) {
                BackupManager.parse(context, uri)
            }
            busy = false
            if (parsed == null) {
                Toast.makeText(
                    context,
                    context.getString(R.string.backup_import_bad_file),
                    Toast.LENGTH_LONG
                ).show()
            } else {
                pendingImport = parsed
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        TopAppBar(
            title = { Text(stringResource(R.string.backup_title), fontSize = 18.sp) },
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
                .padding(20.dp)
        ) {
            Text(
                stringResource(R.string.backup_intro),
                fontSize = 14.sp,
                color = Color.DarkGray,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(16.dp))

            // The privacy warning is not decoration. A backup file will be
            // readable by anything on the phone that can read Downloads -
            // WhatsApp forwards, cloud sync to a shared Google account, a
            // borrowed phone. The patient needs to see this BEFORE tapping.
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFFEF6C00))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFEF6C00)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            stringResource(R.string.backup_warning_title),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFEF6C00)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.backup_warning_body),
                            fontSize = 12.sp,
                            color = Color(0xFF6D4C41),
                            lineHeight = 17.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // EXPORT
            Button(
                onClick = {
                    scope.launch {
                        busy = true
                        val ok = withContext(Dispatchers.IO) {
                            val env = BackupManager.buildEnvelope(context)
                            val uri = BackupManager.writeToDownloads(context, env)
                            uri to (env.glucose.size + env.medications.size + env.hba1c.size)
                        }
                        busy = false
                        val (uri, count) = ok
                        if (uri != null) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.backup_saved, count),
                                Toast.LENGTH_LONG
                            ).show()
                            BackupManager.share(context, uri)
                        } else {
                            Toast.makeText(
                                context,
                                context.getString(R.string.backup_save_failed),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TealGreen),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.backup_export_button),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(Modifier.height(12.dp))

            // IMPORT
            OutlinedButton(
                onClick = {
                    // Any MIME; the parser is the real gate. Using
                    // "application/json" alone would reject files a phone
                    // labelled as "text/plain" - a common surprise on Android.
                    importLauncher.launch(arrayOf("*/*"))
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TealGreen),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.5.dp, TealGreen)
            ) {
                Icon(Icons.Default.CloudDownload, contentDescription = null, tint = TealGreen)
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.backup_import_button),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (busy) {
                Spacer(Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = TealGreen
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.backup_working),
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }

    // The preview + Merge/Replace/Cancel dialog. Nothing on disk changes
    // until the patient picks one of Merge or Replace.
    pendingImport?.let { (env, preview) ->
        ImportChoiceDialog(
            preview = preview,
            onCancel = { pendingImport = null },
            onChoose = { merge ->
                pendingImport = null
                scope.launch {
                    busy = true
                    val result = withContext(Dispatchers.IO) {
                        BackupManager.apply(context, env, merge = merge)
                    }
                    busy = false
                    val msg = context.getString(
                        R.string.backup_import_done,
                        result.glucoseAdded,
                        result.hba1cAdded,
                        result.medsAdded
                    )
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            }
        )
    }
}

@Composable
private fun ImportChoiceDialog(
    preview: BackupPreview,
    onCancel: () -> Unit,
    onChoose: (Boolean) -> Unit
) {
    val fmt = remember { SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.backup_import_found_title)) },
        text = {
            Column {
                Text(
                    stringResource(
                        R.string.backup_import_preview,
                        preview.glucoseCount,
                        preview.hba1cCount,
                        preview.medicationCount
                    ),
                    fontSize = 14.sp
                )
                if (preview.hasProfile) {
                    Text(
                        stringResource(R.string.backup_import_includes_profile),
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                }
                if (preview.exportedAt > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(
                            R.string.backup_import_exported_at,
                            fmt.format(Date(preview.exportedAt))
                        ),
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.backup_import_prompt),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.backup_import_merge_help),
                    fontSize = 12.sp,
                    color = Color.Gray,
                    lineHeight = 17.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.backup_import_replace_help),
                    fontSize = 12.sp,
                    color = Color(0xFFC62828),
                    lineHeight = 17.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onChoose(true) }) {
                Text(stringResource(R.string.backup_import_merge), color = TealGreen)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onChoose(false) }) {
                    Text(
                        stringResource(R.string.backup_import_replace),
                        color = Color(0xFFC62828)
                    )
                }
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.backup_cancel), color = Color.Gray)
                }
            }
        }
    )
}