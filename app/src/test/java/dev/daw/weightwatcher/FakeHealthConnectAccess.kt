package dev.daw.weightwatcher

import androidx.health.connect.client.HealthConnectClient

/** A [HealthConnectAccess] with a settable status and a given (usually fake) client. */
class FakeHealthConnectAccess(
  private val client: HealthConnectClient,
  var sdkStatus: Int = HealthConnectClient.SDK_AVAILABLE,
) : HealthConnectAccess {
  override suspend fun sdkStatus(): Int = sdkStatus

  override suspend fun client(): HealthConnectClient = client
}
