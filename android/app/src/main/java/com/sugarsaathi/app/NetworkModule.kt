package com.sugarsaathi.app
import okhttp3.OkHttpClient
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

        @POST("api/v1/tips")
        suspend fun getTips(@Body request: TipsRequest): TipsResponse

}
object NetworkModule {

    private val BASE_URL: String
        get() {
            val isEmulator = android.os.Build.FINGERPRINT.contains("generic")
                    || android.os.Build.FINGERPRINT.startsWith("google/sdk_gphone")
                    || android.os.Build.MODEL.contains("Emulator")
                    || android.os.Build.MODEL.contains("Android SDK")
            return if (isEmulator) {
                "http://10.0.2.2:8000/"          // emulator → PC
            } else {
                //"http://192.168.0.105:8000/"     // phone → PC over WiFi
                "http://10.184.104.106:8000/"    // phone → PC over WiFi
            }
        }

    private val okHttpClient = OkHttpClient.Builder()
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