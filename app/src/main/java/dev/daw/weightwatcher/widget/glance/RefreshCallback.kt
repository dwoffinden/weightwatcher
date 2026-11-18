package dev.daw.weightwatcher.widget.glance

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import dev.daw.weightwatcher.WeightWorker

class RefreshCallback : ActionCallback {
  override suspend fun onAction(
    context: Context,
    glanceId: GlanceId,
    parameters: ActionParameters,
  ) {
    WeightWorker.enqueueOneTimeWork(context)
  }
}
