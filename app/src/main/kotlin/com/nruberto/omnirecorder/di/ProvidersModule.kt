package com.nruberto.omnirecorder.di

import com.nruberto.omnirecorder.providers.DeepgramProvider
import com.nruberto.omnirecorder.providers.TranscriptionProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ProvidersModule {
    @Binds
    @Singleton
    abstract fun bindTranscriptionProvider(deepgram: DeepgramProvider): TranscriptionProvider
}
