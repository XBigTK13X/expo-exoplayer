import * as React from 'react'
import { View, Pressable, TouchableOpacity, AppState, Text } from 'react-native'
import ExoPlayerView from 'expo-exoplayer'

const circularReplacer = () => {
  const seen = new WeakSet()
  return (keyName, elementValue) => {
    if (typeof elementValue === 'object' && elementValue !== null) {
      if (seen.has(elementValue)) {
        return
      }
      seen.add(elementValue)
    }
    return elementValue
  }
}

const DEBUG_EVENTS = true
const TRACK_DISABLED = -1
const SEEK_START_SECONDS = 400

const animeUrl = 'http://juggernaut.9914.us/tv/anime/collection/precure/Star ☆ Twinkle Precure/Season 1/S01E006 - An Imagination of Darkness! The Dark Pen Appears!.mkv'
const cartoonSubbedUrl = 'http://juggernaut.9914.us/tv/cartoon/k/King of the Hill/Season 14/S14E003 - Chore Money, Chore Problems.mkv'
const srtSubbedAnime = 'http://juggernaut.9914.us/tv/anime/m/My Hero Academia/Season 8/S08E008 - Izuku Midoriya - Rising.mkv'
const srtSubbedLiveAction = 'http://juggernaut.9914.us/tv/live-action/h/Heartland (2007) (CA)/Season 19/S19E009 - Revenge.mkv'

const videoUrl = cartoonSubbedUrl
let audioTrack = 0
let subtitleTrack = 0

const resolutions = {
  ultraHd: {
    width: 3840,
    height: 2160
  },
  fullHd: {
    width: 1920,
    height: 1080
  }
}

const styles = {
  homePage: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'black'
  },
  homeButton: {
    width: '75%'
  },
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'black'
  },
  box: {
    width: 60,
    height: 60,
    marginVertical: 20
  },
  video: {
    flex: 1
  },
  button: {
    height: 250,
    backgroundColor: 'blue',
    textAlign: 'center',
    alignItems: 'center',
    justifyContent: 'center'
  },
  buttonText: {
    color: 'white',
    fontSize: 60
  }
}

function LandingPage({ setPage }) {
  return (
    <View style={styles.homePage}>
      <View style={styles.homeButton}>
        <Pressable style={styles.button} onPress={() => { setPage('video') }}>
          <Text style={styles.buttonText}>Play Video</Text>
          <Text style={{ color: 'white' }}> File: {videoUrl}</Text>
        </Pressable>
      </View>
    </View>
  )
}

function VideoPage({ setPage }) {
  const [isPlaying, setIsPlaying] = React.useState(true)
  const [loadError, setError] = React.useState('')
  const [playbackSpeed, setPlaybackSpeed] = React.useState(1.0)

  const seekSeconds = SEEK_START_SECONDS
  const nativeRef = React.useRef(null)

  React.useEffect(() => {
    const appStateSubscription = AppState.addEventListener('change', currentAppState => {
      if (currentAppState === 'background') {
        console.log('Cleanup background')
        setPage('home')
      }
    })

    return () => {
      appStateSubscription.remove()
    }
  }, [])

  React.useEffect(() => {
    if (!nativeRef.current) return

    const animationFrame = requestAnimationFrame(() => {
      nativeRef.current?.runCommand(`speed|${playbackSpeed}`)
    })

    return () => cancelAnimationFrame(animationFrame)
  }, [playbackSpeed])

  if (loadError) {
    return <Text>{loadError}</Text>
  }

  function onExoPlayerEvent(exoPlayerEvent) {
    if (DEBUG_EVENTS) {
      if (exoPlayerEvent?.property !== 'demuxer-cache-time') {
        console.log(JSON.stringify({ exoPlayerEvent }, circularReplacer(), 4))
      }
    }
  }

  function onExoPlayerLog(exoPlayerLog) {
    if (DEBUG_EVENTS) {
      if (Object.prototype.hasOwnProperty.call(exoPlayerLog, 'method')) {
        console.log('=-=-=-=-=-=-==- NATIVE METHOD =-=-=-=--==-=-')
      }
    }
    if (exoPlayerLog.text && exoPlayerLog.text.indexOf('Playback failure') !== -1) {
      setError('Unable to open file.')
    }
    if (DEBUG_EVENTS) {
      console.log(JSON.stringify({ exoPlayerLog }, circularReplacer(), 4))
    }
  }

  const onPress = async () => {
    setIsPlaying(!isPlaying)
    if (nativeRef.current) {
      console.log('=-=-=-=-=-=-=- Running command =-=-=-=-=-=-')
      setPlaybackSpeed(previousSpeed => (previousSpeed === 1.0 ? 1.25 : 1.0))
    }
  }

  return (
    <View style={styles.container}>
      <TouchableOpacity
        activeOpacity={1}
        style={{ flex: 1, width: '100%', height: '100%' }}
        onPress={onPress}
      >
        <ExoPlayerView
          ref={nativeRef}
          style={{ flex: 1, width: '100%', height: '100%' }}
          isPlaying={isPlaying}
          playUrl={encodeURI(videoUrl)}
          surfaceWidth={resolutions.fullHd.width}
          surfaceHeight={resolutions.fullHd.height}
          selectedAudioTrack={audioTrack}
          selectedSubtitleTrack={subtitleTrack}
          seekToSeconds={seekSeconds}
          onExoPlayerEvent={onExoPlayerEvent}
          onExoPlayerLog={onExoPlayerLog}
        />
      </TouchableOpacity>
    </View>
  )
}

export default function App() {
  const [page, setPage] = React.useState('home')

  if (page === 'home') {
    return <LandingPage setPage={setPage} />
  }

  return <VideoPage setPage={setPage} />
}