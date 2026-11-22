# WristRadio

A standalone internet radio for Wear OS. It browses the
[radio-browser.info](https://www.radio-browser.info/) community directory — tens
of thousands of stations, contributed and checked by volunteers — and plays them
straight off your watch's WiFi.

No phone companion. No account. No ads, no analytics, no tracking.

## Features

- **Browse without typing.** Genres and countries are one tap away, which is how
  a watch should work. Free-text search is there when you need it.
- **Popular now** — what other listeners are tuned to at this moment.
- **Play streams** with ExoPlayer over WiFi, including plain HTTP/MP3, AAC and
  HLS stations.
- **Now-playing face** — station name, bitrate, codec, country, live buffering
  and error states, and volume (the crown is the volume dial).
- **Track titles** where the station offers ICY metadata.
- **Favourites** saved on the watch itself.
- **Keeps playing** with the screen off, behind a media notification with a
  play/pause action, driven by a foreground Media3 playback service.

## How it works

The directory is a round-robin of volunteer mirrors. WristRadio asks
`/json/servers` which ones are alive, then tries each in turn on every request,
so a single mirror going down is not an outage. The API asks for a descriptive
`User-Agent`, and the app sends one.

Playback is Media3. A `MediaSessionService` owns the `ExoPlayer`; the UI only
ever talks to a `MediaController` bound to that session, which is what lets
audio survive the screen going off or the app being swiped away.

Track titles come from the ICY side channel: most streams splice
`StreamTitle='...';` into the audio every `icy-metaint` bytes. Media3 strips
that off before the decoder sees it but does not hand it back, so WristRadio
makes its own short-lived request with `Icy-MetaData: 1` and parses it. It is
strictly best-effort — it costs one extra connection and is abandoned quietly if
the station does not offer metadata, and never retried for that station.

Nothing leaves the device except the directory queries and the audio streams
you ask for.

## Install

Grab the APK and sideload it:

```sh
adb install -r app-debug.apk
adb shell am start -n com.notandrej.wristradio/.MainActivity
```

The APK targets 32-bit ARM (`armeabi-v7a`), which is what the Xiaomi Watch 2
and many other Wear OS 5 watches use. Minimum supported version is Wear OS 3
(API 30).

Cleartext HTTP is allowed: a large share of the stations in the directory still
broadcast over plain HTTP, and an internet radio player that refuses them is not
much of a radio. See `res/xml/network_security_config.xml`.

## Building

```sh
./gradlew :app:assembleDebug     # debug build
./gradlew :app:assembleRelease   # minified release build
```

A release build is signed only when a keystore is supplied out of band, via
either environment variables or Gradle properties:

| Env var                       | Gradle property                |
| ----------------------------- | ------------------------------ |
| `WRISTRADIO_STORE_FILE`       | `wristradio.storeFile`         |
| `WRISTRADIO_STORE_PASSWORD`   | `wristradio.storePassword`     |
| `WRISTRADIO_KEY_ALIAS`        | `wristradio.keyAlias`          |
| `WRISTRADIO_KEY_PASSWORD`     | `wristradio.keyPassword`       |

With no keystore configured the release APK is still produced, just unsigned —
which is what reproducible builds want.

Requires JDK 17+, and an Android SDK with platform 36.

## Tech

Kotlin, Jetpack Compose for Wear OS, OkHttp for the directory, Media3 ExoPlayer
for playback, `SharedPreferences` for favourites. No navigation library, no DI
framework, no JSON library beyond `org.json`. The launcher icon and every
in-app glyph are hand-authored vectors.

## Credits

Station data comes from the [radio-browser.info](https://www.radio-browser.info/)
project, which is run by volunteers. If you like this app, go vote for a station.

## License

GPL-3.0-or-later. See [LICENSE](LICENSE).
