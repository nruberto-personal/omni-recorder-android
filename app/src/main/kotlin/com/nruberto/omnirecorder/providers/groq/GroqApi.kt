package com.nruberto.omnirecorder.providers.groq

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface GroqApi {
    @Multipart
    @POST("openai/v1/audio/transcriptions")
    suspend fun transcribe(
        @Part file: MultipartBody.Part,
        @Part("model") model: RequestBody,
        @Part("response_format") responseFormat: RequestBody,
    ): GroqTranscriptionResponse

    @POST("openai/v1/chat/completions")
    suspend fun chat(@Body request: GroqChatRequest): GroqChatResponse
}
