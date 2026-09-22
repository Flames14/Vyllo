# Privacy Policy for Vyllo

Last updated: September 22, 2026

Vyllo ("we", "our", or "us") is an open-source, privacy-first music client. Your privacy is paramount to us. This Privacy Policy explains how information is handled in connection with your use of the Vyllo mobile application.

## 1. Information Collection and Use

**Vyllo does not collect, track, sell, or transmit any personal data or personally identifiable information (PII) to any proprietary backend.**

*   **No Accounts Required**: You do not need to create an account or provide personal credentials to stream music.
*   **No Analytics or Trackers**: We do not embed any analytics SDKs, advertising identifiers, telemetry trackers, or fingerprinting scripts.
*   **Encrypted Local Storage**: All preferences, playlists, playback history, and lyrics choices are stored locally on your device in hardware-backed encrypted storage.
*   **Permissions**: The app requests only the permissions strictly required for audio playback:
    *   **Internet Access**: To stream audio, lyrics, and metadata directly from public endpoints.
    *   **Storage Access**: To save downloaded music files locally for offline listening.
    *   **Microphone Access**: Used exclusively on-demand for the acoustic song recognition feature. Microphone audio is processed entirely locally in memory via FFT and is never uploaded or saved.
    *   **Foreground Service & Notifications**: To provide persistent media playback controls and background download notifications.

## 2. Third-Party Network Services

When using Vyllo, your device communicates directly with public content APIs to fulfill your playback and lyrics requests:
*   **YouTube Media Streaming**: Direct connections to YouTube CDN servers to retrieve audio and video streams.
*   **Lyrics Providers**: Direct HTTPS queries to open lyrics indexes (LRCLIB, BetterLyrics, Paxsenix, NetEase, KuGou) solely to fetch lyrics matching the playing song's metadata.
*   **Google OAuth (Optional)**: If you choose to sync your public YouTube playlists, authentication is conducted securely via Google PKCE OAuth, and access tokens remain strictly on your device.

## 3. Data Security

All local application state is stored securely on your device. Vyllo does not operate central servers that store your listening habits or personal information.

## 4. Contact & Open Source

For questions or to review the source code, visit our [GitHub Repository](https://github.com/Flames14/Vyllo).
