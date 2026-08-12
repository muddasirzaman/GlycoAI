package com.sugarsaathi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import android.content.Context

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
        enableEdgeToEdge()

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

        // FIX: this was a OneTimeWorkRequest re-enqueued with REPLACE on every
        // launch, so the 2-hour timer reset each time the app was opened - a
        // daily user would never have received a single reminder.
        //
        // PeriodicWorkRequest repeats; KEEP means an existing schedule is left
        // alone rather than restarted, which is what made the old version
        // silently do nothing.
        val reminderRequest =
            PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
                .setInitialDelay(2, TimeUnit.HOURS)
                .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "sugar_saathi_reminder",
            ExistingPeriodicWorkPolicy.KEEP,
            reminderRequest
        )

        val profileRepo = ProfileRepository(this)

        setContent {
            SugarSaathiTheme {
                val authViewModel: AuthViewModel = viewModel()
                var languagePicked by remember {
                    mutableStateOf(LocaleHelper.hasChosenLanguage(this@MainActivity))
                }
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
                var showPrivacy by remember { mutableStateOf(false) }
                var showReminders by remember { mutableStateOf(false) }
                // null = list view; a Reminder = edit that one; NEW_REMINDER = create
                var editingReminder by remember { mutableStateOf<Reminder?>(null) }
                var addingReminder by remember { mutableStateOf(false) }
                val reminderViewModel: ReminderViewModel = viewModel()

                // Structured medications. Same shape as the reminder state above:
                // showMedications drives the list; editing/adding drive the form
                // that sits on top of it so Back returns to the list.
                var showMedications by remember { mutableStateOf(false) }
                var editingMedication by remember { mutableStateOf<Medication?>(null) }
                var addingMedication by remember { mutableStateOf(false) }
                val medicationViewModel: MedicationViewModel = viewModel()

                // Sectioned profile (Stage 2). showProfileView opens the
                // read-only sectioned screen; editingSection (non-null) opens
                // the editor for one section on top of it, so Back returns to
                // the view rather than all the way out. This REPLACES the old
                // behaviour where "Edit my information" re-ran onboarding -
                // onboarding still runs for genuine first-launch below.
                var showProfileView by remember { mutableStateOf(false) }
                var editingSection by remember { mutableStateOf<ProfileSection?>(null) }

                chatVM = chatViewModel

                LaunchedEffect(Unit) {
                    val saved = profileRepo.profileFlow.first()
                    profile = saved
                    currentProfile = saved
                    // profileFlow always emits a UserProfileData (defaults when
                    // nothing is stored), so the safe call here was redundant.
                    LocaleHelper.saveLanguage(this@MainActivity, saved.language)
                    isLoading = false
                    chatViewModel.initProfileRepo(this@MainActivity)
                    glucoseViewModel.init(this@MainActivity)
                    reminderViewModel.init(this@MainActivity)
                    // Populates the medications table on first run by importing
                    // the plain names from onboarding. Safe to call every launch:
                    // the import only runs when the table is empty.
                    medicationViewModel.init(this@MainActivity)
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
                                    LocaleHelper.saveLanguage(
                                        this@MainActivity,
                                        newProfile.language
                                    )
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

                    // Add / edit sits above the list so Back returns to it.
                    addingReminder || editingReminder != null -> {
                        ReminderEditScreen(
                            existing = editingReminder,
                            reminderViewModel = reminderViewModel,
                            onDone = {
                                addingReminder = false
                                editingReminder = null
                            }
                        )
                    }

                    showReminders -> {
                        RemindersScreen(
                            reminderViewModel = reminderViewModel,
                            onBack = { showReminders = false },
                            onAdd = { addingReminder = true },
                            onEdit = { editingReminder = it }
                        )
                    }

                    // Section editor sits above the profile view, so Back
                    // returns to the sectioned view. Saving updates the
                    // in-memory profile immediately via onSaved, so the view
                    // shows the new value without waiting for a flow emission.
                    editingSection != null -> {
                        ProfileSectionEditScreen(
                            section = editingSection!!,
                            profile = profile!!,
                            onDone = { editingSection = null },
                            onSaved = { updated ->
                                profile = updated
                                currentProfile = updated
                            }
                        )
                    }

                    showProfileView -> {
                        ProfileViewScreen(
                            profile = profile!!,
                            onBack = { showProfileView = false },
                            onEditSection = { editingSection = it },
                            onOpenMedications = {
                                // Reuse the same medications list the Tracker
                                // hub opens - one editor for medicines, never two.
                                showProfileView = false
                                showMedications = true
                            }
                        )
                    }

                    // Medication add / edit sits above its list, same as
                    // reminders, so Back returns to the medicines list rather
                    // than all the way out to the Tracker tab.
                    addingMedication || editingMedication != null -> {
                        MedicationEditScreen(
                            existing = editingMedication,
                            medicationViewModel = medicationViewModel,
                            onDone = {
                                addingMedication = false
                                editingMedication = null
                            }
                        )
                    }

                    showMedications -> {
                        MedicationsScreen(
                            medicationViewModel = medicationViewModel,
                            onBack = { showMedications = false },
                            onAdd = { addingMedication = true },
                            onEdit = { editingMedication = it }
                        )
                    }

                    showPrivacy -> {
                        PrivacyScreen(
                            onBack = { showPrivacy = false },
                            onAccountDeleted = {
                                showPrivacy = false
                                isSignedIn = false
                                selectedTab = 0
                                showChatHistory = false
                                selectedSession = null
                            }
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
                            authViewModel = authViewModel,
                            onSignedOut = {
                                // Drop back to the login screen and clear the
                                // in-memory session so nothing leaks across users.
                                isSignedIn = false
                                selectedTab = 0
                                showChatHistory = false
                                selectedSession = null
                            },
                            onAddReading = { showAddReading = true },
                            // onOpenSession removed - MainTabScreen never used it.
                            onChatHistory = { showChatHistory = true },
                            // Was showOnboarding = true (re-ran the wizard).
                            // Now opens the sectioned view/edit screen instead.
                            // Onboarding still runs for genuine first-launch via
                            // the onboardingDone check above.
                            onEditProfile = { showProfileView = true },
                            onPrivacy = { showPrivacy = true },
                            onReminders = { showReminders = true },
                            onMedications = { showMedications = true }
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