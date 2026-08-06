package com.sugarsaathi.app

import      android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sugarsaathi.app.ui.theme.SugarSaathiTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import java.util.Locale
import android.content.res.Configuration
import android.content.Context
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {

    companion object {
        private var splashShown = false
    }
    private var chatVM: ChatViewModel? = null
    private var currentProfile: UserProfileData? = null

    override fun attachBaseContext(newBase: Context) {
        val lang = LocaleHelper.savedLanguage(newBase)
        super.attachBaseContext(LocaleHelper.wrap(newBase, lang))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
            enableEdgeToEdge()          // <-- add this
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                100
            )
        }

        val reminderRequest =
            OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(2, TimeUnit.HOURS)
                .build()

        WorkManager.getInstance(this).enqueueUniqueWork(
            "sugar_saathi_reminder",
            ExistingWorkPolicy.REPLACE,
            reminderRequest
        )

        val profileRepo = ProfileRepository(this)

        setContent {
            SugarSaathiTheme {
                val authViewModel: AuthViewModel = viewModel()
                var languagePicked by remember { mutableStateOf(LocaleHelper.hasChosenLanguage(this@MainActivity)) }
                var isSignedIn by remember { mutableStateOf(authViewModel.isAlreadySignedIn()) }
                var profile by remember { mutableStateOf<UserProfileData?>(null) }
                var isLoading by remember { mutableStateOf(true) }
                var showSplash by remember { mutableStateOf(!splashShown) }
                var showOnboarding by remember { mutableStateOf(false) }

                // which tab is selected
                var selectedTab by remember { mutableIntStateOf(0) }

                // sub-screens that open ON TOP of a tab
                var showAddReading by remember { mutableStateOf(false) }
                var selectedSession by remember { mutableStateOf<ChatSession?>(null) }
                val chatViewModel: ChatViewModel = viewModel()
                val glucoseViewModel: GlucoseViewModel = viewModel()
                var showChatHistory by remember { mutableStateOf(false) }
                chatVM = chatViewModel

                LaunchedEffect(Unit) {
                    val saved = profileRepo.profileFlow.first()
                    profile = saved
                    currentProfile = saved
                    saved?.let {
                        LocaleHelper.saveLanguage(this@MainActivity, it.language)
                    }
                    isLoading = false
                    chatViewModel.initProfileRepo(this@MainActivity)
                    glucoseViewModel.init(this@MainActivity)
                }

                when {
                    showSplash -> SplashScreen(onFinished = {
                        showSplash = false
                        splashShown = true
                    })

                    isLoading -> LoadingScreen()

                    !languagePicked -> {
                        LanguagePickScreen(onLanguageChosen = { lang ->
                            LocaleHelper.saveLanguage(this@MainActivity, lang)
                            languagePicked = true
                            recreate()
                        })
                    }

                    !isSignedIn -> {
                        LoginScreen(
                            authViewModel = authViewModel,
                            onVerified = { isSignedIn = true }
                        )
                    }

                    profile?.onboardingDone != true || showOnboarding -> {
                        OnboardingScreen(
                            onComplete = { newProfile ->
                                lifecycleScope.launch {
                                    profileRepo.saveProfile(newProfile)
                                    LocaleHelper.saveLanguage(this@MainActivity, newProfile.language)
                                    recreate()
                                }
                            }
                        )
                    }

                    // A reading form opened on top of the Tracker tab
                    showAddReading -> {
                        AddReadingScreen(
                            defaultUnit = profile!!.glucoseUnit,
                            glucoseViewModel = glucoseViewModel,
                            onBack = { showAddReading = false },
                            onSaved = {
                                showAddReading = false
                                selectedTab = 2
                            }
                        )
                    }

                    // A past chat session opened on top of the History tab
                    selectedSession != null -> {
                        ChatDetailScreen(
                            session = selectedSession!!,
                            onBack = { selectedSession = null }
                        )
                    }

                    showChatHistory -> {
                        ChatHistoryScreen(
                            onBack = { showChatHistory = false },
                            onOpenSession = { session -> selectedSession = session }
                        )
                    }

                    // The main tabbed interface
                    else -> {
                        MainTabScreen(
                            selectedTab = selectedTab,
                            onTabSelected = { selectedTab = it },
                            profile = profile!!,
                            chatViewModel = chatViewModel,
                            glucoseViewModel = glucoseViewModel,
                            onAddReading = { showAddReading = true },
                            onOpenSession = { selectedSession = it },
                            onChatHistory = { showChatHistory = true },
                            onEditProfile = { showOnboarding = true }
                        )
                    }
                }
            }
        }
    }
    override fun onStop() {
        super.onStop()
        val vm = chatVM
        val prof = currentProfile
        if (vm != null && prof != null) {
            vm.saveCurrentSession()
            vm.extractAndSaveFacts(prof)
        }
    }
}