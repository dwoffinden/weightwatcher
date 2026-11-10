package dev.daw.weightwatcher

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import androidx.glance.appwidget.updateAll
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.InstantSource
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

  @Provides
  @Singleton
  fun provideSyncSettingsDataStore(@ApplicationContext context: Context): DataStore<SyncSettings> =
    DataStoreFactory.create(
      serializer = ProtoSerializer(SyncSettings.getDefaultInstance(), SyncSettings.parser()),
      produceFile = { context.dataStoreFile("sync_settings.pb") },
    )

  @Provides fun provideClock(): InstantSource = InstantSource.system()

  @Provides
  fun provideWidgetUpdater(@ApplicationContext context: Context): WidgetUpdater = WidgetUpdater {
    WeightGlanceWidget().updateAll(context)
  }

  @Provides
  fun provideActiveDataTypesProvider(
    @ApplicationContext context: Context
  ): ActiveDataTypesProvider = ActiveDataTypesProvider {
    WeightGlanceWidget.getActiveDataTypes(context)
  }
}
