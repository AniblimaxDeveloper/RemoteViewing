# Emotexware v1.0.1

Single-APK remote support prototype. The same APK can run as Controller or This Device (Target).

## Important platform behavior
- The Target must be installed and registered with the server.
- The target owner must approve every remote session.
- Screen sharing uses Android MediaProjection and requires user consent.
- Remote gestures use Android AccessibilityService and require the user to enable it.
- The device model is used for lookup, while a private per-install identifier prevents collisions. The model is not an access credential.
- Real device serial numbers are restricted on modern Android and should not be used as the login secret.

## Server
```bash
cd server
npm install
npm start
```

Then set `SERVER_URL` in `app/src/main/java/com/emotexware/app/MainActivity.java`.

## Build on 64-bit Linux / GitHub Actions
The Android module is a single app and can be built on a normal 64-bit Android build runner. Do not use the 32-bit Termux userspace for AAPT2.
