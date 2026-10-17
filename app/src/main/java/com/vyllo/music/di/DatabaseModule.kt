package com.vyllo.music.di

import android.content.Context
import com.vyllo.music.data.download.DownloadDao
import com.vyllo.music.data.download.DownloadDatabase
import com.vyllo.music.data.download.FavoritesDao
import com.vyllo.music.data.download.HistoryDao
import com.vyllo.music.data.download.PlaylistDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DownloadDatabase {
        return DownloadDatabase.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideDownloadDao(database: DownloadDatabase): DownloadDao {
        return database.downloadDao()
    }

    @Provides
    @Singleton
    fun providePlaylistDao(database: DownloadDatabase): PlaylistDao {
        return database.playlistDao()
    }

    @Provides
    @Singleton
    fun provideHistoryDao(database: DownloadDatabase): HistoryDao {
        return database.historyDao()
    }

    @Provides
    @Singleton
    fun provideFavoritesDao(database: DownloadDatabase): FavoritesDao {
        return database.favoritesDao()
    }
}
