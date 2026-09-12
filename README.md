# RemoteViewing — consent-based remote support MVP

This project is a proof-of-concept for viewing/controlling an Android device only after the device owner explicitly approves a session.

## Components
- `server/` — Node.js WebSocket relay.
- `android-target/` — APK installed on the device being shared.
- `android-controller/` — APK installed on the device operated by the support user.

## Important
The prototype uses WebSocket (`ws://`) for simplicity. For Internet deployment, put the server behind TLS and use `wss://`. Do not expose the plain server to an untrusted public network.

Android's MediaProjection requires user approval, and modern Android requires the proper mediaProjection foreground-service declaration/permission. The target app also asks the user to enable its Accessibility Service before remote gestures are accepted.

## Quick server setup in Termux
```bash
pkg update
pkg install nodejs -y
cd server
npm install
npm start
```

Default port: 8080.

For a local Wi-Fi test, put the server on a reachable device and set the same WebSocket URL in both Android apps, for example:
`ws://192.168.1.10:8080`

## Android build requirements
The project uses Android Gradle Plugin 9.4.0 / Gradle 9.6 / JDK 17. If your local Android SDK is not installed in Termux, install/configure the SDK or build from Android Studio. The apps use Java, so no Kotlin compiler is required.
