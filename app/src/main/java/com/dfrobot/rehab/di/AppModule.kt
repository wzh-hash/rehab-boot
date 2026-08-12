package com.dfrobot.rehab.di

import android.content.Context
import androidx.room.Room
import com.dfrobot.rehab.data.DeviceSettingsRepositoryImpl
import com.dfrobot.rehab.data.TrainingSessionRepositoryImpl
import com.dfrobot.rehab.data.local.DeviceSettingsStore
import com.dfrobot.rehab.data.local.RehabDatabase
import com.dfrobot.rehab.data.local.TrainingSessionDao
import com.dfrobot.rehab.core.mqtt.MqttConnectionManager
import com.dfrobot.rehab.core.sensor.StepCounter
import com.dfrobot.rehab.core.sensor.StepSensor
import com.dfrobot.rehab.data.mqtt.MqttTelemetryDataSource
import com.dfrobot.rehab.data.mqtt.TelemetryDataSource
import com.dfrobot.rehab.domain.ConnectionGateway
import com.dfrobot.rehab.domain.repository.DeviceSettingsRepository
import com.dfrobot.rehab.domain.repository.TrainingSessionRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindTrainingSessionRepository(
        impl: TrainingSessionRepositoryImpl,
    ): TrainingSessionRepository

    @Binds
    @Singleton
    abstract fun bindDeviceSettingsRepository(
        impl: DeviceSettingsRepositoryImpl,
    ): DeviceSettingsRepository

    @Binds
    @Singleton
    abstract fun bindTelemetryDataSource(
        impl: MqttTelemetryDataSource,
    ): TelemetryDataSource

    @Binds
    @Singleton
    abstract fun bindConnectionGateway(
        impl: MqttConnectionManager,
    ): ConnectionGateway

    @Binds
    @Singleton
    abstract fun bindStepCounter(
        impl: StepSensor,
    ): StepCounter

    companion object {
        @Provides
        @Singleton
        fun provideDatabase(@ApplicationContext context: Context): RehabDatabase =
            Room.databaseBuilder(context, RehabDatabase::class.java, "rehab.db").build()

        @Provides
        fun provideTrainingSessionDao(db: RehabDatabase): TrainingSessionDao =
            db.trainingSessionDao()

        @Provides
        @Singleton
        fun provideDeviceSettingsStore(@ApplicationContext context: Context): DeviceSettingsStore =
            DeviceSettingsStore(context)
    }
}
