import { ViewProps } from 'react-native'

export interface TextTrack {
    index: number
    language: string
    label: string
    isSelected: boolean
}

export interface NativeExoPlayer {
    load(uri: string, subUri?: string | null, subMime?: string | null): void
    play(): void
    pause(): void
    seekTo(seconds: number): void
    getTextTracks(): TextTrack[]
    selectTextTrack(index: number): void
    release(): void
}

export interface ExpoExoPlayerViewProps extends ViewProps {
    player?: NativeExoPlayer | null
    resizeMode?: 'contain' | 'cover' | 'fill'
}