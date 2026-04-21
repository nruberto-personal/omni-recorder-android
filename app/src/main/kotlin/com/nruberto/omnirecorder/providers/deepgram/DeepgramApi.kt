package com.nruberto.omnirecorder.providers.deepgram

import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

interface DeepgramApi {
    @POST("v1/listen")
    suspend fun listen(
        @Body audio: RequestBody,
        @Query("model") model: String = "nova-2-conversationalai",
        @Query("diarize") diarize: Boolean = true,
        @Query("smart_format") smartFormat: Boolean = true,
        @Query("paragraphs") paragraphs: Boolean = true,
        @Query("punctuate") punctuate: Boolean = true,
        @Query("utterances") utterances: Boolean = true,
        @Query("filler_words") fillerWords: Boolean = false,
        @Query("language") language: String = "en",
    ): DeepgramResponse
}
