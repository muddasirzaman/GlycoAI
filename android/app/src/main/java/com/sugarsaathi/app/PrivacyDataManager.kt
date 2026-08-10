package com.sugarsaathi.app

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object PrivacyDataManager {

    sealed class DeleteResult {
        object Success : DeleteResult()

        // Local data (DataStore, Room, chat history) was wiped either way -
        // this only means Firebase refused the account deletion itself
        // because the sign-in credential is no longer "recent enough" for
        // that specific operation. The user's health data is already gone;
        // only the phone-number record on Firebase remains until they sign
        // in again and retry.
        object LocalDeletedNeedsReauth : DeleteResult()

        data class Failure(val message: String) : DeleteResult()
    }

    // ---------------------------------------------------------------
    // Export
    // ---------------------------------------------------------------

    data class ExportBundle(
        val exportedAt: String,
        val profile: UserProfileData,
        val glucoseReadings: List<GlucoseReading>,
        val chatSessions: List<ChatSession>
    )

    /**
     * Builds a single JSON file with everything stored about the user
     * locally and returns it, ready to hand to shareIntentFor().
     */
    suspend fun exportData(context: Context): File = withContext(Dispatchers.IO) {
        val profile = ProfileRepository(context).profileFlow.first()
        val readings = AppDatabase.getInstance(context).glucoseDao().getAllReadings().first()
        val sessions = ChatHistoryRepository(context).loadAllSessions()

        val bundle = ExportBundle(
            exportedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
                .format(Date()),
            profile = profile,
            glucoseReadings = readings,
            chatSessions = sessions
        )

        val json = GsonBuilder().setPrettyPrinting().create().toJson(bundle)

        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        // Clear anything left from a previous export so the share sheet
        // never offers a stale file.
        exportDir.listFiles()?.forEach { it.delete() }

        val file = File(exportDir, "sugarsaathi_my_data.json")
        file.writeText(json)
        file
    }

    fun shareIntentFor(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    // ---------------------------------------------------------------
    // Deletion
    // ---------------------------------------------------------------

    /**
     * Wipes DataStore, Room, and local chat history unconditionally, then
     * attempts to delete the Firebase Auth account. Local data is deleted
     * first and always, regardless of whether the Firebase step succeeds -
     * someone asking to delete their data should never end up with their
     * health data intact just because an auth token was stale.
     */
    suspend fun deleteAllData(context: Context): DeleteResult = withContext(Dispatchers.IO) {
        try {
            AppDatabase.getInstance(context).glucoseDao().deleteAll()
            ChatHistoryRepository(context).deleteAllSessions()
            ProfileRepository(context).clearProfile()
        } catch (e: Exception) {
            return@withContext DeleteResult.Failure(
                "Local data deletion failed: ${e.message}. Nothing was changed. Please try again."
            )
        }

        val user = FirebaseAuth.getInstance().currentUser
            ?: return@withContext DeleteResult.Success

        return@withContext try {
            Tasks.await(user.delete(), 15, TimeUnit.SECONDS)
            DeleteResult.Success
        } catch (e: Exception) {
            val cause = e.cause ?: e
            android.util.Log.w("GLYCOPRIVACY", "Account deletion incomplete: ${cause.message}")
            if (cause is FirebaseAuthRecentLoginRequiredException) {
                DeleteResult.LocalDeletedNeedsReauth
            } else {
                DeleteResult.LocalDeletedNeedsReauth
            }
        }
    }
}