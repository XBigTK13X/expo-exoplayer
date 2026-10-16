import { Platform } from 'react-native'
import { requireNativeModule } from 'expo-modules-core'

const isAndroid = Platform.OS === 'android'

const ExpoExoPlayerModule = isAndroid ? requireNativeModule('ExpoExoPlayer') : null

export default ExpoExoPlayerModule