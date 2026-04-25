package com.vyllo.music.recognition.di

import com.vyllo.music.recognition.data.repository.ShazamRepositoryImpl
import com.vyllo.music.recognition.domain.repository.ShazamRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RecognitionModule {

    @Binds
    @Singleton
    abstract fun bindShazamRepository(
        shazamRepositoryImpl: ShazamRepositoryImpl
    ): ShazamRepository
}
