import React from 'react'
import { Platform, View } from 'react-native'
import { requireNativeViewManager } from 'expo-modules-core'
import { ExpoExoPlayerViewProps } from './ExpoExoPlayer.types'

const NativeView: React.ComponentType<ExpoExoPlayerViewProps> = Platform.OS === 'android'
    ? requireNativeViewManager('ExpoExoPlayer')
    : View

export default function ExpoExoPlayerView(props: ExpoExoPlayerViewProps) {
    if (Platform.OS !== 'android') {
        return <View {...props} />
    }
    return <NativeView {...props} />
}