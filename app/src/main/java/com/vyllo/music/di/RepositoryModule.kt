package com.vyllo.music.di

import com.vyllo.music.domain.repository.HighResThumbnailResolver
import com.vyllo.music.domain.repository.IMusicRepository
import com.vyllo.music.domain.repository.LyricsSearchService
import com.vyllo.music.domain.repository.LyricsTranslator
import com.vyllo.music.domain.repository.PlayerPreferences
import com.vyllo.music.data.LyricsSearchAdapter
import com.vyllo.music.data.MusicRepositoryImpl
import com.vyllo.music.data.TranslationEngineAdapter
import com.vyllo.music.data.manager.PreferenceManager
import com.vyllo.music.data.network.YouTubeThumbnailResolver
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindMusicRepository(
        musicRepositoryImpl: MusicRepositoryImpl
    ): IMusicRepository

    @Binds
    @Singleton
    abstract fun bindLyricsTranslator(
        adapter: TranslationEngineAdapter
    ): LyricsTranslator

    @Binds
    @Singleton
    abstract fun bindHighResThumbnailResolver(
        resolver: YouTubeThumbnailResolver
    ): HighResThumbnailResolver

    @Binds
    @Singleton
    abstract fun bindPlayerPreferences(
        preferenceManager: PreferenceManager
    ): PlayerPreferences

    @Binds
    @Singleton
    abstract fun bindLyricsSearchService(
        adapter: LyricsSearchAdapter
    ): LyricsSearchService
}

