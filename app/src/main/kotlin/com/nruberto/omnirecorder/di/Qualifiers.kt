package com.nruberto.omnirecorder.di

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GroqClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DeepgramClient
