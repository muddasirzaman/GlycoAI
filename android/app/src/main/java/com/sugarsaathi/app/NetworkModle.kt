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

}
object NetworkModule {

    private const val BASE_URL = "https://glycoai-production.up.railway.app/"

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