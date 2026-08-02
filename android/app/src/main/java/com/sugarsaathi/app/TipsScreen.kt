package com.sugarsaathi.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TipsScreen(
    profile: UserProfileData,
    tipsViewModel: TipsViewModel = viewModel()
) {
    val uiState by tipsViewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        tipsViewModel.loadTipsIfNeeded(profile)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Diabetes Tips", fontWeight = FontWeight.Bold) }, // TODO: swap for stringResource once you add tips_title to strings.xml
            actions = {
                IconButton(onClick = { tipsViewModel.refreshTips(profile) }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh tips")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = TealGreen,
                titleContentColor = Color.White,
                actionIconContentColor = Color.White
            )
        )

        when {
            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = TealGreen)
                }
            }
            uiState.errorMessage != null -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(uiState.errorMessage ?: "", color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { tipsViewModel.refreshTips(profile) },
                            colors = ButtonDefaults.buttonColors(containerColor = TealGreen)
                        ) { Text("Try Again") }
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.tips) { tip ->
                        TipCard(tip)
                    }
                }
            }
        }
    }
}

@Composable
fun TipCard(tip: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFE1F5EE), shape = RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = TealGreen,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(text = tip, fontSize = 14.sp, lineHeight = 20.sp)
    }
}