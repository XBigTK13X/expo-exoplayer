package com.simplepathstudios.exoplayer

import android.content.Context
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import expo.modules.kotlin.AppContext
import expo.modules.kotlin.views.ExpoView

class ExpoExoPlayerView(
    context: Context,
    appContext: AppContext,
) : ExpoView(context, appContext) {
    val playerView =
        PlayerView(context).apply {
            useController = false
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        }

    init {
        addView(
            playerView,
            android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    fun setPlayer(instance: ExpoExoPlayerInstance?) {
        playerView.player = instance?.player
    }

    fun setResizeMode(mode: String?) {
        playerView.resizeMode =
            when (mode) {
                "cover" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                "fill" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
    }
}
