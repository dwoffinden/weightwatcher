package dev.daw.weightwatcher

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import androidx.health.connect.client.permission.HealthPermission
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ManifestPermissionsTest {

  @Test
  fun manifest_declaresExactHealthPermissionsForAllDataTypes() {
    val context: Context = ApplicationProvider.getApplicationContext()
    val packageInfo =
      context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)

    val healthPermissions: List<String> =
      packageInfo.requestedPermissions.orEmpty().filter {
        it.startsWith("android.permission.health.")
      }

    assertThat(healthPermissions)
      .containsExactlyElementsIn(
        DataType.entries.map { it.readPermission } +
          HealthPermission.PERMISSION_READ_HEALTH_DATA_IN_BACKGROUND
      )
  }
}
