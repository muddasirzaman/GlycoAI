package com.sugarsaathi.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationsScreen(
    medicationViewModel: MedicationViewModel,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Medication) -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { medicationViewModel.init(context) }

    val meds by medicationViewModel.medications.collectAsState()
    var pendingDelete by remember { mutableStateOf<Medication?>(null) }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.med_delete_title)) },
            text = { Text(stringResource(R.string.med_delete_confirm, target.name)) },
            confirmButton = {
                TextButton(onClick = {
                    medicationViewModel.delete(target)
                    pendingDelete = null
                }) { Text(stringResource(R.string.rem_delete), color = Color(0xFFD32F2F)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {

        TopAppBar(
            title = { Text(stringResource(R.string.meds_title), fontSize = 18.sp) },
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

        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.meds_intro),
                fontSize = 12.5.sp,
                color = Color.Gray,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onAdd,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TealGreen),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.med_add),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        if (meds.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("💊", fontSize = 44.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.med_empty),
                        fontSize = 14.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(meds, key = { it.id }) { med ->
                    MedicationCard(
                        med = med,
                        onToggle = { on -> medicationViewModel.setActive(med, on) },
                        onEdit = { onEdit(med) },
                        onDelete = { pendingDelete = med }
                    )
                }
            }
        }
    }
}

@Composable
private fun MedicationCard(
    med: Medication,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    // Stopped medicines stay visible but dimmed. Clinically, "used to take
    // this" is information worth keeping, not something to erase.
    val alpha = if (med.active) 1f else 0.5f

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (med.active) Color(0xFFE1F5EE)
            else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.dp,
            if (med.active) TealGreen.copy(alpha = 0.35f) else Color.LightGray
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onEdit() }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (med.isInsulin) "💉" else "💊", fontSize = 22.sp)
            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        med.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0D5A44).copy(alpha = alpha)
                    )
                    if (med.dose.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            med.dose,
                            fontSize = 13.sp,
                            color = Color(0xFF0D5A44).copy(alpha = alpha * 0.8f)
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    buildString {
                        append(stringResource(med.frequencyEnum.labelRes))
                        if (med.timingEnum != MedTiming.ANY_TIME) {
                            append("  •  ")
                            append(stringResource(med.timingEnum.labelRes))
                        }
                        if (med.timesOfDay.isNotBlank()) {
                            append("  •  ")
                            append(med.timesOfDay)
                        }
                    },
                    fontSize = 12.sp,
                    color = Color.Gray.copy(alpha = alpha)
                )
            }

            Switch(
                checked = med.active,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = TealGreen
                )
            )

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Gray)
            }
        }
    }
}