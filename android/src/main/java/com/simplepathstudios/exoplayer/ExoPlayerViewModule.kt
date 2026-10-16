package com.simplepathstudios.exoplayer

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class ExoPlayerViewModule : Module() {
    override fun definition() =
        ModuleDefinition {
            Name("ExoPlayerView")

            View(ExoPlayerView::class) {
                Events(
                    "onExoPlayerLog",
                    "onExoPlayerEvent",
                )

                AsyncFunction("runCommand") { view: ExoPlayerView, orders: String ->
                    if (view.isAttachedToWindow) {
                        view.runCommand(orders)
                    }
                }

                AsyncFunction("setOptionString") { view: ExoPlayerView, options: String ->
                    if (view.isAttachedToWindow) {
                        view.setOptionString(options)
                    }
                }

                AsyncFunction("cleanup") { view: ExoPlayerView ->
                    view.cleanup()
                }

                Prop("playUrl") { view: ExoPlayerView, value: String ->
                    view.session.playUrl = value
                    view.onSessionUpdatedFromProps()
                }

                Prop("seekToSeconds") { view: ExoPlayerView, seconds: Double ->
                    view.session.seekToSeconds = seconds
                    view.session.markDirty(ExoPlayerSession.PlayerIntent.SEEK)
                    view.onSessionUpdatedFromProps()
                }

                Prop("selectedAudioTrack") { view: ExoPlayerView, index: Int ->
                    view.session.selectedAudioTrack = index
                    view.session.markDirty(ExoPlayerSession.PlayerIntent.AUDIO_TRACK)
                    view.onSessionUpdatedFromProps()
                }

                Prop("selectedSubtitleTrack") { view: ExoPlayerView, index: Int ->
                    view.session.selectedSubtitleTrack = index
                    view.session.markDirty(ExoPlayerSession.PlayerIntent.SUBTITLE_TRACK)
                    view.onSessionUpdatedFromProps()
                }

                Prop("isPlaying") { view: ExoPlayerView, playing: Boolean ->
                    view.session.isPlaying = playing
                    view.onSessionUpdatedFromProps()
                }

                Prop("surfaceWidth") { view: ExoPlayerView, value: Int ->
                    view.session.surfaceWidth = value
                }

                Prop("surfaceHeight") { view: ExoPlayerView, value: Int ->
                    view.session.surfaceHeight = value
                }

                OnViewDestroys { view: ExoPlayerView ->
                    view.cleanup()
                }
            }
        }
}
