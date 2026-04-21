package com.nruberto.omnirecorder.di

import com.nruberto.omnirecorder.BuildConfig
import com.nruberto.omnirecorder.providers.deepgram.DeepgramApi
import com.nruberto.omnirecorder.providers.groq.GroqApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Provides
    @Singleton
    @GroqClient
    fun provideGroqOkHttp(): OkHttpClient = baseOkHttp(
        authScheme = "Bearer",
        apiKey = BuildConfig.GROQ_API_KEY,
    )

    @Provides
    @Singleton
    @DeepgramClient
    fun provideDeepgramOkHttp(): OkHttpClient = baseOkHttp(
        authScheme = "Token",
        apiKey = BuildConfig.DEEPGRAM_API_KEY,
    )

    @Provides
    @Singleton
    fun provideGroqApi(@GroqClient client: OkHttpClient, json: Json): GroqApi =
        Retrofit.Builder()
            .baseUrl("https://api.groq.com/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GroqApi::class.java)

    @Provides
    @Singleton
    fun provideDeepgramApi(@DeepgramClient client: OkHttpClient, json: Json): DeepgramApi =
        Retrofit.Builder()
            .baseUrl("https://api.deepgram.com/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(DeepgramApi::class.java)

    private fun baseOkHttp(authScheme: String, apiKey: String): OkHttpClient {
        val auth = Interceptor { chain ->
            val req = chain.request().newBuilder()
                .addHeader("Authorization", "$authScheme $apiKey")
                .build()
            chain.proceed(req)
        }
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .addInterceptor(auth)
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .writeTimeout(5, TimeUnit.MINUTES)
            .build()
    }
}
