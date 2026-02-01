# MusicPiped - Music Streaming App

A Music Streaming Android App using NewPipe Extractor v0.24.8 and Media3 (ExoPlayer).

## Features

- YouTube Music search and streaming
- Background playback with notifications
- Modern Jetpack Compose UI
- Robust error handling
- Audio-only stream optimization

## Dependencies

- NewPipe Extractor v0.24.8
- Media3 (ExoPlayer) v1.3.0
- Jetpack Compose
- OkHttp for network requests
- Coil for image loading

## Setup

1. Make sure you have Android Studio installed
2. Clone or import this project
3. Build and run on an Android device or emulator

## Architecture

- Network: OkHttpDownloader bridges NewPipe with OkHttp
- Repository: MusicRepository handles search and stream extraction
- Service: MusicService manages background playback with Media3
- UI: MainActivity with Jetpack Compose interface