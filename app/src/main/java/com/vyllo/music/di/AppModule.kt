package com.vyllo.music.di

import android.content.Context
import com.vyllo.music.core.security.SecurityConfig
import com.vyllo.music.core.security.SecurePreferenceManager
import com.vyllo.music.data.manager.PlaybackQueueManager
import com.vyllo.music.data.manager.PreferenceManager
import com.vyllo.music.data.manager.DownloadManager
import com.vyllo.music.domain.manager.PlaybackManager
import com.vyllo.music.domain.manager.PermissionHandler
import com.vyllo.music.domain.manager.FloatingPlayerManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSecurityConfig(): SecurityConfig {
        return SecurityConfig()
    }

    @Provides
    @Singleton
    fun provideSecurePreferenceManager(@ApplicationContext context: Context): SecurePreferenceManager {
        return SecurePreferenceManager(context)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(securityConfig: SecurityConfig): OkHttpClient {
        return securityConfig.createSecureHttpClient()
    }

    @Provides
    @Singleton
    fun providePreferenceManager(@ApplicationContext context: Context): PreferenceManager {
        return PreferenceManager(context)
    }

    @Provides
    @Singleton
    fun providePlaybackManager(
        @ApplicationContext context: Context,
        playbackQueueManager: PlaybackQueueManager
    ): PlaybackManager {
        return PlaybackManager(context, playbackQueueManager)
    }

    @Provides
    @Singleton
    fun providePermissionHandler(@ApplicationContext context: Context): PermissionHandler {
        return PermissionHandler(context)
    }

    @Provides
    @Singleton
    fun provideFloatingPlayerManager(
        @ApplicationContext context: Context,
        preferenceManager: PreferenceManager
    ): FloatingPlayerManager {
        return FloatingPlayerManager(context, preferenceManager)
    }

    @Provides
    @Singleton
    fun provideDownloadManager(@ApplicationContext context: Context): DownloadManager {
        return DownloadManager(context)
    }
}
