import { requireNativeView } from 'expo'
import * as React from 'react'
import { type ExoPlayerViewProps, type ExoPlayerViewNativeMethods } from './ExoPlayerViewTypes'

const styles: any = {
    videoPlayer: {
        position: 'absolute',
        left: 0,
        bottom: 0,
        right: 0,
        top: 0
    }
}

const ExoPlayerViewNative: React.ComponentType<ExoPlayerViewProps> = requireNativeView('ExoPlayerView')

export const ExoPlayerView = React.forwardRef<ExoPlayerViewNativeMethods, ExoPlayerViewProps>((props: any, parentRef: any) => {
    const nativeRef = React.useRef<any>(null)

    React.useImperativeHandle(parentRef, () => ({
        runCommand: (orders: string) => nativeRef.current?.runCommand?.(orders),
        setOptionString: (options: string) => nativeRef.current?.setOptionString?.(options),
        cleanup: () => nativeRef.current?.cleanup?.()
    }))

    React.useEffect(() => {
        return () => {
            nativeRef.current?.cleanup?.()
        }
    }, [])

    const onLogEvent = (eventPayload: any) => {
        if (!props.onExoPlayerEvent) {
            return
        }

        const sourceData = eventPayload?.nativeEvent ?? eventPayload
        const formattedEvent = { ...sourceData }

        if (formattedEvent.kind === 'long' || formattedEvent.kind === 'double') {
            formattedEvent.value = Number(formattedEvent.value)
        } else if (formattedEvent.kind === 'boolean') {
            formattedEvent.value = formattedEvent.value === 'true' || formattedEvent.value === true
        }

        return props.onExoPlayerEvent(formattedEvent)
    }

    const onExoPlayerLog = (eventPayload: any) => {
        if (!props.onExoPlayerLog) {
            return
        }
        const sourceData = eventPayload?.nativeEvent ?? eventPayload
        return props.onExoPlayerLog({ ...sourceData })
    }

    return (
        <ExoPlayerViewNative
            ref={nativeRef}
            style={props.surfaceStyle ? props.surfaceStyle : styles.videoPlayer}
            playUrl={props.playUrl}
            isPlaying={props.isPlaying}
            surfaceWidth={props.surfaceWidth}
            surfaceHeight={props.surfaceHeight}
            seekToSeconds={props.seekToSeconds}
            selectedAudioTrack={props.selectedAudioTrack}
            selectedSubtitleTrack={props.selectedSubtitleTrack}
            onExoPlayerEvent={onLogEvent}
            onExoPlayerLog={onExoPlayerLog}
        />
    )
})

export default ExoPlayerView