# Emotexware v1.0.1

Remote support Android MVP with a human-readable device-model lookup.

## Lookup model

The Target registers an identifier built from `Build.MANUFACTURER + Build.MODEL` (for example `OPPO CPH2477`). The Controller can use this value to find the online target. The model string is only an identifier; it is not a password or a way to bypass Android security.

Every remote session still requires approval on the Target device and Android screen-capture consent.

## Components

- `android-controller`: operator UI
- `android-target`: target device UI, MediaProjection and Accessibility consent
- `server`: WebSocket signaling/forwarding server

## Production notes

Use WSS/TLS, authenticated short-lived session tokens, rate limits, audit logging, and a unique user-controlled device alias before exposing the server to the public internet. Device model strings are not unique credentials and should not be used as authentication.
