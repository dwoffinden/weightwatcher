package dev.daw.weightwatcher

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier

@Qualifier @Retention(AnnotationRetention.BINARY) annotation class HealthConnectSdkStatus

@Module
@InstallIn(SingletonComponent::class)
object HealthConnectModule {

  @Provides
  @HealthConnectSdkStatus
  fun provideHealthConnectSdkStatus(@ApplicationContext context: Context): Int =
    HealthConnectClient.getSdkStatus(context)

  @Provides
  fun provideHealthConnectClient(@ApplicationContext context: Context): HealthConnectClient =
    HealthConnectClient.getOrCreate(context)
}
