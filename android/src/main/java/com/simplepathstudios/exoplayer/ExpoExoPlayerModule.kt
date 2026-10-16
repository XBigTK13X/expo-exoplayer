package com.simplepathstudios.exoplayer

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class ExpoExoPlayerModule : Module() {
    override fun definition() =
        ModuleDefinition {
            Name("ExpoExoPlayer")

            Class(ExpoExoPlayerInstance::class) {
                Constructor {
                    ExpoExoPlayerInstance(appContext)
                }

                Function("load") { instance: ExpoExoPlayerInstance, uri: String, subUri: String?, subMime: String? ->
                    instance.load(uri, subUri, subMime)
                }

                Function("play") { instance: ExpoExoPlayerInstance ->
                    instance.player.play()
                }

                Function("pause") { instance: ExpoExoPlayerInstance ->
                    instance.player.pause()
                }

                Function("seekTo") { instance: ExpoExoPlayerInstance, seconds: Double ->
                    instance.player.seekTo((seconds * 1000).toLong())
                }

                Function("getTextTracks") { instance: ExpoExoPlayerInstance ->
                    instance.getTextTracks()
                }

                Function("selectTextTrack") { instance: ExpoExoPlayerInstance, index: Int ->
                    instance.selectTextTrack(index)
                }

                Function("release") { instance: ExpoExoPlayerInstance ->
                    instance.release()
                }
            }

            View(ExpoExoPlayerView::class) {
                Prop("player") { view: ExpoExoPlayerView, instance: ExpoExoPlayerInstance? ->
                    view.setPlayer(instance)
                }

                Prop("resizeMode") { view: ExpoExoPlayerView, mode: String? ->
                    view.setResizeMode(mode)
                }
            }
        }
}
