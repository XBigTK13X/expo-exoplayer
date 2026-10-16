package com.simplepathstudios.exoplayer

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import expo.modules.kotlin.AppContext
import expo.modules.kotlin.sharedobjects.SharedRef

class ExpoExoPlayerInstance(
    appContext: AppContext,
) : SharedRef<ExoPlayer>(
        createPlayer(appContext.reactContext ?: throw IllegalStateException("ReactContext unavailable in AppContext")),
        appContext,
    ) {
    private val context: Context = appContext.reactContext ?: throw IllegalStateException("ReactContext unavailable in AppContext")
    val trackSelector: DefaultTrackSelector = (ref.trackSelector as? DefaultTrackSelector) ?: DefaultTrackSelector(context)

    companion object {
        private fun createPlayer(context: Context): ExoPlayer {
            val trackSelector = DefaultTrackSelector(context)
            val renderersFactory =
                DefaultRenderersFactory(context).apply {
                    setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
                    setEnableDecoderFallback(true)
                }

            return ExoPlayer
                .Builder(context, renderersFactory)
                .setTrackSelector(trackSelector)
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .build(),
                    true,
                ).build()
        }
    }

    val player: ExoPlayer get() = ref

    fun load(
        uri: String,
        subtitleUri: String?,
        subtitleMime: String?,
    ) {
        val item = MediaItem.Builder().setUri(uri)

        if (subtitleUri != null) {
            val mime = subtitleMime ?: MimeTypes.APPLICATION_SUBRIP
            val subConfig =
                MediaItem.SubtitleConfiguration
                    .Builder(android.net.Uri.parse(subtitleUri))
                    .setMimeType(mime)
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .build()
            item.setSubtitleConfigurations(listOf(subConfig))
        }

        player.setMediaItem(item.build())
        player.prepare()
    }

    fun getTextTracks(): List<Map<String, Any?>> {
        val trackList = mutableListOf<Map<String, Any?>>()
        val groups = player.currentTracks.groups
        var idx = 0

        for (group in groups) {
            if (group.type == C.TRACK_TYPE_TEXT) {
                val mediaGroup = group.mediaTrackGroup
                for (ii in 0 until mediaGroup.length) {
                    val format = mediaGroup.getFormat(ii)
                    trackList.add(
                        mapOf(
                            "index" to idx,
                            "language" to (format.language ?: "und"),
                            "label" to (format.label ?: format.language ?: "Track $idx"),
                            "isSelected" to group.isTrackSelected(ii),
                        ),
                    )
                    idx++
                }
            }
        }
        return trackList
    }

    fun selectTextTrack(targetIndex: Int) {
        if (targetIndex < 0) {
            trackSelector.parameters =
                trackSelector
                    .buildUponParameters()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
            return
        }

        var counter = 0
        for (group in player.currentTracks.groups) {
            if (group.type == C.TRACK_TYPE_TEXT) {
                val mediaGroup = group.mediaTrackGroup
                for (ii in 0 until mediaGroup.length) {
                    if (counter == targetIndex) {
                        trackSelector.parameters =
                            trackSelector
                                .buildUponParameters()
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                .setOverrideForType(TrackSelectionOverride(mediaGroup, ii))
                                .build()
                        return
                    }
                    counter++
                }
            }
        }
    }

    fun release() {
        player.release()
    }
}
