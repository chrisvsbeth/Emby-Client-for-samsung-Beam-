# Emby Client for Android

A lightweight Emby/Jellyfin client for Android 2.3.1+ devices.

## Features

- Login to any Emby or Jellyfin server
- Browse movies and folders
- View movie details (year, duration, rating, genres, plot)
- Play videos with quality selection
- Resume playback support
- Recently added movies section
- Search functionality
- Modern dark theme UI

## Requirements

- Android 2.3.1 (Gingerbread) or higher
- Emby Server or Jellyfin Server
- Internet connection

## Building

### Prerequisites

- Android SDK
- Java JDK 8
- Gradle

### Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease
```

The APK will be generated at `app/build/outputs/apk/debug/app-debug.apk`

## Installation

1. Enable "Unknown Sources" in Android settings
2. Transfer the APK to your device
3. Open and install

## Usage

1. Enter your server URL (e.g., http://192.168.1.100:8096)
2. Enter your username and password
3. Browse your media library
4. Tap a movie to view details
5. Select quality and play

## Quality Options

- Original (Direct Play) - Best quality, direct stream
- 1080p HD (~2Mbps)
- 720p HD (~1.5Mbps)
- 576p SD (~1Mbps)
- 480p SD (~800Kbps)
- 360p Low (~400Kbps)

## Project Structure

```
github/
├── app/
│   └── src/main/
│       ├── java/com/emby/client/
│       │   ├── MainActivity.java      # Login screen
│       │   ├── ContentActivity.java   # Media browser
│       │   ├── FileDetailActivity.java # Movie details
│       │   ├── PlayerActivity.java    # Video player
│       │   ├── EmbyApiClient.java     # API wrapper
│       │   └── EmbyApp.java           # Application class
│       └── res/
│           ├── layout/               # XML layouts
│           ├── values/               # Colors, themes, strings
│           └── drawable/             # Icons, backgrounds
├─�� gradle/wrapper/                   # Gradle wrapper
├── build.gradle                      # Root build config
├── settings.gradle                   # Project settings
├── gradle.properties                 # Gradle properties
└── gradlew                            # Build script (Unix)
└── gradlew.bat                        # Build script (Windows)
```

## License

This project is for educational purposes. Use at your own risk.

## Notes

- This app is optimized for older Android devices
- Video seeking during playback has limitations on some streaming formats
- Some features may require specific Emby/Jellyfin server configurations