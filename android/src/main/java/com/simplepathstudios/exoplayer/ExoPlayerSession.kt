package com.simplepathstudios.exoplayer

import java.util.EnumSet

class ExoPlayerSession {
    var playUrl: String? = null
        set(value) {
            if (field != value) {
                field = value
                resetForNewMedia()
            }
        }
    var hasFileLoaded: Boolean = false

    var seekToSeconds: Double? = null
    var selectedAudioTrack: Int? = null
    var selectedSubtitleTrack: Int? = null

    var isPlaying: Boolean = true

    var surfaceWidth: Int? = null
    var surfaceHeight: Int? = null

    enum class PlayerIntent {
        SEEK,
        AUDIO_TRACK,
        SUBTITLE_TRACK,
    }

    private val applied = EnumSet.noneOf(PlayerIntent::class.java)

    fun needsApply(intent: PlayerIntent): Boolean = !applied.contains(intent)

    fun markApplied(intent: PlayerIntent) {
        applied.add(intent)
    }

    fun markDirty(intent: PlayerIntent) {
        applied.remove(intent)
    }

    fun resetForNewMedia() {
        hasFileLoaded = false
        applied.clear()
    }
}
