import React, { useState } from 'react'
import {
  Button,
  Pressable,
  StyleSheet,
  Text,
  View
} from 'react-native'
import { SafeAreaView } from 'react-native-safe-area-context'
import { ExoPlayerView, useExoPlayer } from 'expo-exoplayer'

const VIDEO_URL = encodeURI(
  'http://juggernaut.9914.us/tv/anime/s/Sentenced to Be a Hero/Season 1/S01E001 - Sentence - Support Retreat From Couveunge Forest.mkv'
)

export default function App() {
  const [isPlaying, setIsPlaying] = useState(false)
  const [isOverlayVisible, setIsOverlayVisible] = useState(false)

  const player = useExoPlayer(isPlaying ? VIDEO_URL : '', (instance) => {
    instance.selectTextTrack(0)
  })

  const handleStart = () => {
    setIsOverlayVisible(false)
    setIsPlaying(true)
    player.play()
    player.selectTextTrack(0)
  }

  const handleVideoPress = () => {
    player.pause()
    setIsOverlayVisible(true)
  }

  const handleContinue = () => {
    setIsOverlayVisible(false)
    player.play()
  }

  const handleStop = () => {
    player.pause()
    setIsOverlayVisible(false)
    setIsPlaying(false)
  }

  if (!isPlaying) {
    return (
      <SafeAreaView style={styles.container}>
        <View style={styles.centered}>
          <Text style={styles.header}>ExoPlayer Example</Text>
          <Button title="Begin Playback" onPress={handleStart} />
        </View>
      </SafeAreaView>
    )
  }

  return (
    <SafeAreaView style={styles.playerContainer}>
      <Pressable style={styles.videoWrapper} onPress={handleVideoPress}>
        <ExoPlayerView
          style={styles.video}
          player={player}
          resizeMode="contain"
        />

        {isOverlayVisible ? (
          <View style={styles.overlay}>
            <View style={styles.buttonRow}>
              <View style={styles.buttonWrapper}>
                <Button title="Continue" onPress={handleContinue} />
              </View>
              <View style={styles.buttonWrapper}>
                <Button title="Stop" color="#d32f2f" onPress={handleStop} />
              </View>
            </View>
          </View>
        ) : null}
      </Pressable>
    </SafeAreaView>
  )
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#eee'
  },
  playerContainer: {
    flex: 1,
    backgroundColor: '#000'
  },
  centered: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20
  },
  header: {
    fontSize: 24,
    fontWeight: 'bold',
    marginBottom: 24
  },
  videoWrapper: {
    flex: 1,
    width: '100%',
    height: '100%',
    justifyContent: 'center'
  },
  video: {
    position: 'absolute',
    top: 0,
    left: 0,
    bottom: 0,
    right: 0
  },
  overlay: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(0, 0, 0, 0.6)',
    justifyContent: 'center',
    alignItems: 'center',
    zIndex: 10
  },
  buttonRow: {
    flexDirection: 'row',
    gap: 16
  },
  buttonWrapper: {
    minWidth: 120
  }
})