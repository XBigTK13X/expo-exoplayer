export type ExoPlayerViewProps = {
    ref: any
    style: any
    playUrl: string
    isPlaying: boolean
    selectedAudioTrack: number
    selectedSubtitleTrack: number
    seekToSeconds: number
    surfaceWidth: number
    surfaceHeight: number
    onExoPlayerEvent: (exoPlayerEvent: any) => void
    onExoPlayerLog: (exoPlayerLog: any) => void
}

export type ExoPlayerViewNativeMethods = {
    runCommand: (pipeDelimitedArguments: string) => void | Promise<void>
    setOptionString: (pipeDelimitedArguments: string) => void | Promise<void>
    cleanup: () => void | Promise<void>
}