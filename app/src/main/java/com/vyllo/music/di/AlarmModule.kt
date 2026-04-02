package com.vyllo.music.di

import android.content.Context
import com.vyllo.music.data.alarm.AlarmDao
import com.vyllo.music.data.download.DownloadDatabase
import com.vyllo.music.data.manager.AlarmSchedulerManager
import com.vyllo.music.data.repository.AlarmRepositoryImpl
import com.vyllo.music.domain.repository.AlarmRepository
import com.vyllo.music.domain.usecase.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for alarm feature dependency injection.
 */
@Module
@InstallIn(SingletonComponent::class)
object AlarmModule {

    @Provides
    @Singleton
    fun provideAlarmDao(database: DownloadDatabase): AlarmDao {
        return database.alarmDao()
    }

    @Provides
    @Singleton
    fun provideAlarmRepository(
        alarmDao: AlarmDao
    ): AlarmRepository {
        return AlarmRepositoryImpl(alarmDao)
    }

    @Provides
    @Singleton
    fun provideAlarmSchedulerManager(
        @ApplicationContext context: Context
    ): AlarmSchedulerManager {
        return AlarmSchedulerManager(context)
    }

    @Provides
    @Singleton
    fun provideGetAllAlarmsUseCase(
        alarmRepository: AlarmRepository
    ): GetAllAlarmsUseCase {
        return GetAllAlarmsUseCase(alarmRepository)
    }

    @Provides
    @Singleton
    fun provideCreateAlarmUseCase(
        alarmRepository: AlarmRepository
    ): CreateAlarmUseCase {
        return CreateAlarmUseCase(alarmRepository)
    }

    @Provides
    @Singleton
    fun provideUpdateAlarmUseCase(
        alarmRepository: AlarmRepository
    ): UpdateAlarmUseCase {
        return UpdateAlarmUseCase(alarmRepository)
    }

    @Provides
    @Singleton
    fun provideDeleteAlarmUseCase(
        alarmRepository: AlarmRepository
    ): DeleteAlarmUseCase {
        return DeleteAlarmUseCase(alarmRepository)
    }

    @Provides
    @Singleton
    fun provideToggleAlarmUseCase(
        alarmRepository: AlarmRepository
    ): ToggleAlarmUseCase {
        return ToggleAlarmUseCase(alarmRepository)
    }
}
