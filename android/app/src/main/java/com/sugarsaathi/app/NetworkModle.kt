package com.sugarsaathi.app

import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

interface ApiService {

    @POST("api/v1/chat")
    suspend fun sendMessage(@Body request: ChatRequest): ChatResponse

    @POST("api/v1/extract-facts")
    suspend fun extractFacts(@Body request: ExtractRequest): ExtractResponse

}

/**
 * Attaches the signed-in user's Firebase ID token to every request.
 *
 * Runs on OkHttp's background thread, so the blocking Tasks.await is safe here -
 * it would deadlock on the main thread.
 *
 * Tokens last an hour. Firebase refreshes them automatically, but if the cached
 * one has just expired the server answers 401; we then force a refresh and retry
 * once. Without that retry a user would see a spurious failure roughly hourly.
 */
class FirebaseAuthInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()

        val response = chain.proceed(withToken(original, forceRefresh = false))

        if (response.code == 401) {
            response.close()
            return chain.proceed(withToken(original, forceRefresh = true))
        }

        return response
    }

    private fun withToken(
        request: okhttp3.Request,
        forceRefresh: Boolean
    ): okhttp3.Request {
        val token = idToken(forceRefresh) ?: return request
        return request.newBuilder()
            .addHeader("Authorization", "Bearer $token")
            .build()
    }

    private fun idToken(forceRefresh: Boolean): String? {
        val user = FirebaseAuth.getInstance().currentUser ?: return null
        return try {
            Tasks.await(user.getIdToken(forceRefresh), 15, TimeUnit.SECONDS).token
        } catch (e: Exception) {
            // No token: the request goes out unauthenticated and the server
            // decides. Never crash the request here.
            android.util.Log.w("GLYCOAUTH", "Could not get ID token: ${e.message}")
            null
        }
    }
}

object NetworkModule {

    private const val BASE_URL = "https://glycoai-production.up.railway.app/"

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(FirebaseAuthInterceptor())
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val apiService: ApiService = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(ApiService::class.java)
}