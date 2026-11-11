package dev.daw.weightwatcher

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Health Connect's availability and client. */
interface HealthConnectAccess {
  /** Returns one of the [HealthConnectClient] `SDK_*` status constants. */
  suspend fun sdkStatus(): Int

  /** Returns the client. Throws if [sdkStatus] isn't [HealthConnectClient.SDK_AVAILABLE]. */
  suspend fun client(): HealthConnectClient
}

/**
 * The device's Health Connect.
 *
 * Checking availability and creating the client call system services in other processes, so they
 * run off the calling thread, once.
 */
@Singleton
class SystemHealthConnectAccess
@Inject
constructor(@param:ApplicationContext private val context: Context) : HealthConnectAccess {
  private val sdkStatus: Int by lazy { HealthConnectClient.getSdkStatus(context) }
  private val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

  override suspend fun sdkStatus(): Int = withContext(Dispatchers.IO) { sdkStatus }

  override suspend fun client(): HealthConnectClient = withContext(Dispatchers.IO) { client }
}
