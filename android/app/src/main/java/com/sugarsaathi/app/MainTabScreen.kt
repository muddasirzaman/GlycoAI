package com.sugarsaathi.app

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainTabScreen(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    profile: UserProfileData,
    chatViewModel: ChatViewModel,
    glucoseViewModel: GlucoseViewModel,
    onAddReading: () -> Unit,
    onOpenSession: (ChatSession) -> Unit,
    onChatHistory: () -> Unit,
    onEditProfile: () -> Unit
) {
    Scaffold(
        // FIX: ALL bottom inset handling lives here, in exactly one place.
        // union() takes the LARGER of keyboard / nav-bar, never the sum, so the
        // whole Scaffold (tab bar included) sits directly on top of the keyboard.
        modifier = Modifier.windowInsetsPadding(
            WindowInsets.ime.union(WindowInsets.navigationBars)
        ),
        // FIX: Scaffold must not add insets of its own — the line above covers the
        // bottom, and each screen's TopAppBar covers the status bar at the top.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                // FIX: NavigationBar applies a navigation-bar inset by default.
                // The Scaffold modifier already did that, so zero it out here.
                windowInsets = WindowInsets(0, 0, 0, 0)
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { onTabSelected(0) },
                    icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Chat") },
                    label = { Text(stringResource(R.string.tab_chat)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = TealGreen,
                        selectedTextColor = TealGreen,
                        indicatorColor = Color(0xFFE1F5EE)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { onTabSelected(1) },
                    icon = { Icon(Icons.Default.Favorite, contentDescription = "Tracker") },
                    label = { Text(stringResource(R.string.tab_tracker)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = TealGreen,
                        selectedTextColor = TealGreen,
                        indicatorColor = Color(0xFFE1F5EE)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { onTabSelected(2) },
                    icon = { Icon(Icons.Default.History, contentDescription = "History") },
                    label = { Text(stringResource(R.string.tab_history)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = TealGreen,
                        selectedTextColor = TealGreen,
                        indicatorColor = Color(0xFFE1F5EE)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { onTabSelected(3) },
                    icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                    label = { Text(stringResource(R.string.tab_profile)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = TealGreen,
                        selectedTextColor = TealGreen,
                        indicatorColor = Color(0xFFE1F5EE)
                    )
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> ChatScreen(
                    userProfile = profile,
                    glucoseViewModel = glucoseViewModel,
                    onHistoryClick = onChatHistory,
                    chatViewModel = chatViewModel
                )

                1 -> GlucoseHubScreen(
                    onBack = { onTabSelected(0) },
                    onAddReading = onAddReading,
                    onViewReadings = { onTabSelected(2) }
                )
                2 -> GlucoseHistoryScreen(
                    glucoseViewModel = glucoseViewModel,
                    onBack = { onTabSelected(0) }
                )
                3 -> ProfileTabPlaceholder(
                    profile = profile,
                    onEditProfile = onEditProfile
                )
            }
        }
    }
}

@Composable
fun ProfileTabPlaceholder(profile: UserProfileData, onEditProfile: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val scope = rememberCoroutineScope()
    val profileRepo = remember { ProfileRepository(context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // FIX: this tab has no TopAppBar, so it needs its own status-bar padding
            // now that the Scaffold no longer supplies one.
            .statusBarsPadding()
            .padding(24.dp)
    ) {
        Text(stringResource(R.string.tab_profile), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.profile_name, profile.name))
        Text(stringResource(R.string.profile_age, profile.age))
        Text(stringResource(R.string.profile_diabetes, profile.diabetesType))

        Spacer(Modifier.height(24.dp))

        // Language toggle
        Text(stringResource(R.string.language_label), fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LanguageChip(
                label = stringResource(R.string.lang_english),
                selected = profile.language == "en",
                onClick = {
                    changeLanguage(context, profileRepo, scope, profile, "en", activity)
                }
            )
            LanguageChip(
                label = stringResource(R.string.lang_urdu),
                selected = profile.language == "ur",
                onClick = {
                    changeLanguage(context, profileRepo, scope, profile, "ur", activity)
                }
            )
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onEditProfile,
            colors = ButtonDefaults.buttonColors(containerColor = TealGreen)
        ) { Text(stringResource(R.string.edit_my_information)) }
    }
}

private fun changeLanguage(
    context: android.content.Context,
    profileRepo: ProfileRepository,
    scope: kotlinx.coroutines.CoroutineScope,
    profile: UserProfileData,
    newLang: String,
    activity: android.app.Activity?
) {
    if (profile.language == newLang) return
    LocaleHelper.saveLanguage(context, newLang)
    scope.launch {
        profileRepo.saveProfile(profile.copy(language = newLang))
        activity?.recreate()
    }
}

@Composable
fun LanguageChip(label: String, selected: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected) Color(0xFFE1F5EE) else Color.Transparent
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) TealGreen else Color.Gray
        )
    ) {
        Text(label, color = if (selected) TealGreen else Color.Gray)
    }
}