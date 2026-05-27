# Emby Client for Samsung GT-I8530 (Android 4.0.4)

A lightweight Emby/Jellyfin client for Android 4.0.4 (API 10), designed for the Samsung GT-I8530.

Target device: **Samsung GT-I8530** (Android 4.0.4, 480×800 hdpi, dual-core ARMv7).

## Features

- Login with credentials saved to SharedPreferences (server, username, password)
- Browse Movies, TV Shows (Series → Seasons → Episodes), Music, Collections
- In-app video playback with server-side seek and resume support
- Dedicated audio player with album track list, prev/play-pause/next
- Progress reporting back to the server
- Resume playback indicator (Continue Watching row)
- Kill transcoding on exit
- Dark theme with Emby-red color scheme
- All quality levels use TS progressive download (`RequireNonSegmentalStreaming=true`)

## Building

```bash
git clone <repo>
cd EmbyClient
# Ensure JAVA_HOME and ANDROID_HOME are set
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Requirements

- Java 17+
- Android SDK (platform android-10 or later)
- Gradle 7.5.1 (wrapper included)

## Architecture

The app uses only Android SDK APIs (API Level 10). No AndroidX, no third-party libraries.

- **MainActivity** – Login screen
- **ContentActivity** – Browse library views (movies, TV shows, music, collections)
- **FileDetailActivity** – Item details with play/resume buttons
- **PlayerActivity** – Video player with server-side seek via URL rebuild
- **MusicPlayerActivity** – Audio player with playlist
- **EmbyApiClient** – HTTP client for Emby REST API
- **HlsPlaybackEngine** – Local HLS proxy (unused, TS progressive download used instead)

## Notes

- This app targets API 10 (Android 2.3.6 Gingerbread) — the maximum supported by the Samsung GT-I8530.
- Video playback uses `VideoView` + `MediaPlayer` (not ExoPlayer or MediaCodec).
- Seeking is performed server-side: the URL is rebuilt with `StartTimeTicks` and `seekOffset`.
- Resume positions are stored locally in SharedPreferences (server-side position persistence may not work on all Emby versions).

## License

MIT
