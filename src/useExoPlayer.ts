import { useEffect, useRef } from 'react'
import { Platform } from 'react-native'
import ExpoExoPlayerModule from './ExpoExoPlayerModule'
import { NativeExoPlayer, TextTrack } from './ExpoExoPlayer.types'

class NoopExoPlayer implements NativeExoPlayer {
    load(_uri: string, _subUri?: string | null, _subMime?: string | null): void { }
    play(): void { }
    pause(): void { }
    seekTo(_seconds: number): void { }
    getTextTracks(): TextTrack[] {
        return []
    }
    selectTextTrack(_index: number): void { }
    release(): void { }
}

export function useExoPlayer(
    uri: string,
    setup?: (player: NativeExoPlayer) => void
): NativeExoPlayer {
    const playerRef = useRef<NativeExoPlayer | null>(null)

    if (!playerRef.current) {
        const instance: NativeExoPlayer =
            Platform.OS === 'android' && ExpoExoPlayerModule?.ExpoExoPlayerInstance
                ? new ExpoExoPlayerModule.ExpoExoPlayerInstance()
                : new NoopExoPlayer()

        playerRef.current = instance

        if (setup) {
            setup(instance)
        }
    }

    const player = playerRef.current

    useEffect(() => {
        if (uri) {
            player.load(uri)
        }
    }, [uri, player])

    useEffect(() => {
        return () => {
            player.release()
        }
    }, [player])

    return player
}