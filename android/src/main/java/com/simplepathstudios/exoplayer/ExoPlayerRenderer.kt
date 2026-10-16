package com.simplepathstudios.exoplayer

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.SurfaceView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.exoplayer.ExoPlayer

class ExoPlayerRenderer(
    private val session: ExoPlayerSession,
    private val surfaceView: SurfaceView,
    private val onCuesUpdated: (List<Cue>) -> Unit,
    private val onAspectRatioChanged: (Float) -> Unit,
    private val onLog: (Map<String, Any>) -> Unit,
    private val onEvent: (Map<String, Any>) -> Unit,
) : Player.Listener {
    private enum class State {
        NEW,
        CREATED,
        INITIALIZED,
        ACTIVE,
        SHUTTING_DOWN,
        DESTROYED,
    }

    companion object {
        private const val TAG = "expo-exoplayer"
        private const val LOG_LEVEL_INFO = 20
        private const val LOG_LEVEL_WARN = 30
        private const val POSITION_POLL_INTERVAL_MS = 250L
    }

    private val stateLock = Any()

    @Volatile private var state: State = State.NEW

    @Volatile private var surfaceAttached = false

    @Volatile var surfaceReady = false

    @Volatile private var loadedUrl: String? = null

    @Volatile private var playbackStarted = false

    @Volatile private var destroyed = false

    @Volatile private var isPolling = false

    private var exoPlayer: ExoPlayer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioTrackGroups = ArrayList<Tracks.Group>()
    private val subtitleTrackGroups = ArrayList<Tracks.Group>()

    private val positionPollRunnable =
        object : Runnable {
            override fun run() {
                if (!playerAlive || shuttingDown || !isPolling) {
                    return
                }

                val activePlayer = exoPlayer
                if (activePlayer == null || !activePlayer.playWhenReady) {
                    isPolling = false
                    return
                }

                emitCurrentPosition()
                mainHandler.postDelayed(this, POSITION_POLL_INTERVAL_MS)
            }
        }

    private inline val playerAlive: Boolean
        get() {
            return when (state) {
                State.CREATED,
                State.INITIALIZED,
                State.ACTIVE,
                -> true

                else -> false
            }
        }

    private inline val shuttingDown: Boolean
        get() {
            return state == State.SHUTTING_DOWN || state == State.DESTROYED
        }

    private fun emitCurrentPosition() {
        val activePlayer = exoPlayer ?: return
        val currentPositionSeconds = activePlayer.currentPosition / 1000.0
        val bufferedPositionSeconds = activePlayer.bufferedPosition / 1000.0
        onEvent(mapOf("property" to "time-pos", "kind" to "double", "value" to currentPositionSeconds))
        onEvent(mapOf("property" to "demuxer-cache-time", "kind" to "double", "value" to bufferedPositionSeconds))
    }

    private fun startPositionPolling() {
        if (isPolling) {
            return
        }
        isPolling = true
        mainHandler.removeCallbacks(positionPollRunnable)
        mainHandler.post(positionPollRunnable)
    }

    private fun stopPositionPolling() {
        if (!isPolling) {
            return
        }
        isPolling = false
        mainHandler.removeCallbacks(positionPollRunnable)
        emitCurrentPosition()
    }

    fun start() {
        synchronized(stateLock) {
            try {
                val appContext = surfaceView.context.applicationContext
                mainHandler.post {
                    exoPlayer = ExoPlayer.Builder(appContext).build()
                    exoPlayer?.addListener(this)
                    state = State.CREATED
                    attachSurfaceIfNeeded()
                }
            } catch (exception: Exception) {
                logException(exception)
            }
        }
    }

    fun attachSurfaceIfNeeded() {
        if (!surfaceReady || surfaceAttached) {
            return
        }

        mainHandler.post {
            exoPlayer?.setVideoSurfaceView(surfaceView)
            surfaceAttached = true

            synchronized(stateLock) {
                state = State.ACTIVE
            }

            if (exoPlayer?.isPlaying == true) {
                startPositionPolling()
            }
            checkAndEmitVideoSize()
            maybeStartPlayback()
        }
    }

    fun runCommand(commandParts: Array<String>) {
        if (!playerAlive || shuttingDown || commandParts.isEmpty()) {
            return
        }

        mainHandler.post {
            val activePlayer = exoPlayer ?: return@post
            val action = commandParts[0]
            when (action) {
                "seek" -> {
                    if (commandParts.size > 1) {
                        val seconds = commandParts[1].toDoubleOrNull() ?: 0.0
                        activePlayer.seekTo((seconds * 1000).toLong())
                        if (!activePlayer.isPlaying) {
                            emitCurrentPosition()
                        }
                    }
                }

                "speed" -> {
                    if (commandParts.size > 1) {
                        val speedValue = commandParts[1].toFloatOrNull() ?: 1.0f
                        activePlayer.playbackParameters = PlaybackParameters(speedValue)
                    }
                }

                "stop" -> {
                    activePlayer.stop()
                }
            }
        }
    }

    fun setOptionString(
        option: String,
        value: String,
    ) {
        if (!playerAlive || shuttingDown) {
            return
        }

        mainHandler.post {
            val activePlayer = exoPlayer ?: return@post
            when (option) {
                "volume" -> {
                    val volumeFloat = value.toFloatOrNull() ?: 1.0f
                    activePlayer.volume = volumeFloat
                }

                "repeat" -> {
                    activePlayer.repeatMode =
                        if (value == "yes" || value == "1") {
                            Player.REPEAT_MODE_ONE
                        } else {
                            Player.REPEAT_MODE_OFF
                        }
                }
            }
        }
    }

    fun onSessionUpdated() {
        if (!playerAlive || shuttingDown) {
            return
        }
        mainHandler.post {
            maybeStartPlayback()
            applyDeferredState()
            applyContinuousState()
        }
    }

    fun destroy() {
        val doTeardown: Boolean
        synchronized(stateLock) {
            doTeardown =
                when (state) {
                    State.NEW,
                    State.SHUTTING_DOWN,
                    State.DESTROYED,
                    -> false

                    else -> true
                }
            if (doTeardown) {
                state = State.SHUTTING_DOWN
            }
        }
        if (!doTeardown) {
            return
        }

        destroyed = true
        isPolling = false
        mainHandler.removeCallbacks(positionPollRunnable)

        mainHandler.post {
            try {
                loadedUrl = null
                onCuesUpdated(emptyList())
                exoPlayer?.let { activePlayer ->
                    activePlayer.removeListener(this)
                    activePlayer.clearVideoSurfaceView(surfaceView)
                    activePlayer.stop()
                    activePlayer.release()
                }
                exoPlayer = null
                surfaceAttached = false
            } catch (exception: Exception) {
                logException(exception)
            } finally {
                synchronized(stateLock) {
                    state = State.DESTROYED
                }
            }
        }
    }

    private fun maybeStartPlayback() {
        if (state != State.ACTIVE || !surfaceAttached) {
            return
        }

        val url = session.playUrl ?: return
        if (loadedUrl == url) {
            return
        }

        try {
            loadedUrl = url
            playbackStarted = false

            val activePlayer = exoPlayer ?: return
            val mediaItem = MediaItem.fromUri(url)
            activePlayer.setMediaItem(mediaItem)

            val initialSeek = session.seekToSeconds
            if (initialSeek != null && session.needsApply(ExoPlayerSession.PlayerIntent.SEEK)) {
                activePlayer.seekTo((initialSeek * 1000).toLong())
                session.markApplied(ExoPlayerSession.PlayerIntent.SEEK)
            } else {
                activePlayer.seekTo(0L)
            }

            activePlayer.playWhenReady = session.isPlaying
            activePlayer.prepare()
        } catch (exception: Exception) {
            logException(exception)
        }
    }

    private fun applyContinuousState() {
        if (!playerAlive || shuttingDown || !session.hasFileLoaded) {
            return
        }

        val activePlayer = exoPlayer ?: return
        activePlayer.playWhenReady = session.isPlaying

        if (session.isPlaying) {
            startPositionPolling()
        } else {
            stopPositionPolling()
        }
    }

    private fun applyDeferredState() {
        if (!session.hasFileLoaded || !surfaceAttached || state != State.ACTIVE) {
            return
        }

        val activePlayer = exoPlayer ?: return

        session.seekToSeconds?.let { seekToSeconds ->
            if (session.needsApply(ExoPlayerSession.PlayerIntent.SEEK)) {
                activePlayer.seekTo((seekToSeconds * 1000).toLong())
                session.markApplied(ExoPlayerSession.PlayerIntent.SEEK)
                if (!activePlayer.isPlaying) {
                    emitCurrentPosition()
                }
            }
        }

        session.selectedAudioTrack?.let { selectedTrackIndex ->
            if (session.needsApply(ExoPlayerSession.PlayerIntent.AUDIO_TRACK)) {
                if (selectedTrackIndex == -1 || audioTrackGroups.isNotEmpty()) {
                    applyTrackSelection(C.TRACK_TYPE_AUDIO, selectedTrackIndex, audioTrackGroups)
                    session.markApplied(ExoPlayerSession.PlayerIntent.AUDIO_TRACK)
                }
            }
        }

        session.selectedSubtitleTrack?.let { selectedTrackIndex ->
            if (session.needsApply(ExoPlayerSession.PlayerIntent.SUBTITLE_TRACK)) {
                if (selectedTrackIndex == -1 || subtitleTrackGroups.isNotEmpty()) {
                    applyTrackSelection(C.TRACK_TYPE_TEXT, selectedTrackIndex, subtitleTrackGroups)
                    session.markApplied(ExoPlayerSession.PlayerIntent.SUBTITLE_TRACK)
                }
            }
        }
    }

    private fun applyTrackSelection(
        trackType: Int,
        trackIndex: Int,
        groupList: List<Tracks.Group>,
    ) {
        val activePlayer = exoPlayer ?: return
        val parametersBuilder = activePlayer.trackSelectionParameters.buildUpon()

        if (trackIndex < 0 || trackIndex >= groupList.size) {
            parametersBuilder.setTrackTypeDisabled(trackType, true)
            parametersBuilder.clearOverridesOfType(trackType)
            activePlayer.trackSelectionParameters = parametersBuilder.build()
            return
        }

        val targetGroup = groupList[trackIndex]
        if (!targetGroup.isTrackSupported(0)) {
            return
        }

        parametersBuilder.setTrackTypeDisabled(trackType, false)
        parametersBuilder.setOverrideForType(
            TrackSelectionOverride(targetGroup.mediaTrackGroup, 0),
        )
        activePlayer.trackSelectionParameters = parametersBuilder.build()
    }

    private fun checkAndEmitVideoSize() {
        val activePlayer = exoPlayer ?: return
        val videoSize = activePlayer.videoSize
        if (videoSize.width > 0 && videoSize.height > 0) {
            val pixelRatio = if (videoSize.pixelWidthHeightRatio > 0f) videoSize.pixelWidthHeightRatio else 1.0f
            val calculatedRatio = (videoSize.width.toFloat() * pixelRatio) / videoSize.height.toFloat()
            mainHandler.post {
                onAspectRatioChanged(calculatedRatio)
            }
        }
    }

    override fun onVideoSizeChanged(videoSize: VideoSize) {
        if (videoSize.width > 0 && videoSize.height > 0) {
            val pixelRatio = if (videoSize.pixelWidthHeightRatio > 0f) videoSize.pixelWidthHeightRatio else 1.0f
            val calculatedRatio = (videoSize.width.toFloat() * pixelRatio) / videoSize.height.toFloat()
            mainHandler.post {
                onAspectRatioChanged(calculatedRatio)
            }
        }
    }

    override fun onCues(cueGroup: CueGroup) {
        mainHandler.post {
            onCuesUpdated(cueGroup.cues)
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_READY -> {
                session.hasFileLoaded = true
                playbackStarted = true
                checkAndEmitVideoSize()
                val durationSeconds = (exoPlayer?.duration ?: 0L) / 1000.0
                onEvent(mapOf("property" to "duration", "kind" to "double", "value" to durationSeconds))
                onEvent(mapOf("property" to "seekable", "kind" to "boolean", "value" to true))
                onEvent(mapOf("property" to "paused-for-cache", "kind" to "boolean", "value" to false))
                applyDeferredState()
            }

            Player.STATE_BUFFERING -> {
                onEvent(mapOf("property" to "paused-for-cache", "kind" to "boolean", "value" to true))
            }

            Player.STATE_ENDED -> {
                if (playbackStarted) {
                    onEvent(mapOf("property" to "eof-reached", "kind" to "boolean", "value" to true))
                }
            }

            Player.STATE_IDLE -> {
                Unit
            }
        }
    }

    override fun onPlayWhenReadyChanged(
        playWhenReady: Boolean,
        reason: Int,
    ) {
        if (playWhenReady) {
            startPositionPolling()
        } else {
            stopPositionPolling()
        }
        onEvent(mapOf("property" to "pause", "kind" to "boolean", "value" to !playWhenReady))
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            startPositionPolling()
        } else if (exoPlayer?.playWhenReady == false) {
            stopPositionPolling()
        }
    }

    override fun onTracksChanged(tracks: Tracks) {
        audioTrackGroups.clear()
        subtitleTrackGroups.clear()

        val trackListPayload = ArrayList<Map<String, Any>>()
        var overallIndex = 0

        for (groupIndex in 0 until tracks.groups.size) {
            val trackGroup = tracks.groups[groupIndex]
            val trackType = trackGroup.type
            val mediaTrackGroup = trackGroup.mediaTrackGroup

            for (trackFormatIndex in 0 until mediaTrackGroup.length) {
                val format = mediaTrackGroup.getFormat(trackFormatIndex)
                val trackMap = HashMap<String, Any>()

                trackMap["id"] = overallIndex.toString()
                trackMap["type"] =
                    when (trackType) {
                        C.TRACK_TYPE_AUDIO -> "audio"
                        C.TRACK_TYPE_TEXT -> "sub"
                        C.TRACK_TYPE_VIDEO -> "video"
                        else -> "unknown"
                    }
                trackMap["title"] = format.label ?: ""
                trackMap["lang"] = format.language ?: ""
                trackMap["codec"] = format.sampleMimeType ?: ""
                trackMap["selected"] = trackGroup.isTrackSelected(trackFormatIndex)

                if (trackType == C.TRACK_TYPE_AUDIO) {
                    audioTrackGroups.add(trackGroup)
                } else if (trackType == C.TRACK_TYPE_TEXT) {
                    subtitleTrackGroups.add(trackGroup)
                }

                trackListPayload.add(trackMap)
                overallIndex += 1
            }
        }

        onEvent(
            mapOf(
                "property" to "track-list",
                "kind" to "track-list",
                "value" to trackListPayload,
            ),
        )

        applyDeferredState()
    }

    override fun onPlayerError(error: PlaybackException) {
        Log.e(TAG, "ExoPlayer playback failure", error)
        logMessage("ExoPlayerRenderer->onPlayerError", LOG_LEVEL_WARN, error.message ?: "Playback failure")
    }

    private fun logMessage(
        prefix: String,
        level: Int,
        text: String,
    ) {
        if (shuttingDown) {
            return
        }

        val safeText = if (text.length > 1000) text.substring(0, 1000) + "..." else text
        if (level <= LOG_LEVEL_WARN) {
            Log.w(TAG, "[$prefix] $safeText")
        }
        onLog(mapOf("prefix" to prefix, "level" to level, "text" to safeText))
    }

    private fun logException(exception: Exception) {
        if (shuttingDown) {
            return
        }
        Log.e(TAG, "ExoPlayer internal error", exception)
        logMessage("ExoPlayerRenderer->logException", LOG_LEVEL_WARN, exception.message ?: "Unknown ExoPlayer error")
    }
}
