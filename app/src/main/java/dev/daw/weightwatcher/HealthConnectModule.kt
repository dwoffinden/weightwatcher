package dev.daw.weightwatcher

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface HealthConnectModule {
  @Binds fun bindHealthConnectAccess(access: SystemHealthConnectAccess): HealthConnectAccess
}
