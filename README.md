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
  and error states, and volume.
- **Track titles** where the station offers ICY metadata.
- **Favourites** saved on the watch itself.
- **Keeps playing** with the screen off, behind a media notification with a
  play/pause action, driven by a foreground Media3 playback service that exists
  only while audio is actually playing.
- **Miserly when idle.** Pause the radio and the notification, the foreground
  service, the wake locks and the process itself all go away. Nothing wakes up
  to check anything.

## How it works

The directory is a round-robin of volunteer mirrors. WristRadio asks
`/json/servers` which ones are alive, then tries each in turn on every request,
so a single mirror going down is not an outage. The API asks for a descriptive
`User-Agent`, and the app sends one.

Playback is Media3. A `MediaSessionService` owns the `ExoPlayer`; the UI only
ever talks to a `MediaController` bound to that session, which is what lets
audio survive the screen going off or the app being swiped away.

That binding is deliberately temporary. The controller is created in `onStart`
and released again in `onStop`, so when no screen is showing there is no client
holding the service up. Audio that is genuinely playing keeps the service in the
foreground on its own; a paused or idle player is left with nothing, and Media3
then stops the service. `setShowNotificationForIdlePlayer(...NEVER)` makes
"playing" a hard requirement for both the notification and the foreground state.

Track titles come from the ICY side channel: most streams splice
`StreamTitle='...';` into the audio every `icy-metaint` bytes. Media3 strips
that off before the decoder sees it but does not hand it back, so WristRadio
makes its own short-lived request with `Icy-MetaData: 1` and parses it. It is
strictly best-effort — it costs one extra connection and is abandoned quietly if
the station does not offer metadata, and never retried for that station.

That reader lives in the *service*, not the UI, because it is playback state and
not screen state: it starts and stops with the player, follows the player's own
events, and there is no thread left reading a socket once playback stops. The
verdict is published over the session as extras, which the UI mirrors while it is
bound.

Nothing leaves the device except the directory queries and the audio streams
you ask for.

## Battery

WristRadio is built for a 512 mAh watch, where a resident background service is
the expensive thing, not the audio. Three rules shape the code:

1. **The foreground service exists only while audio is playing.** Media3 can be
   told that with `setShowNotificationForIdlePlayer(...NEVER)`, but "idle" to
   Media3 means a player in `STATE_IDLE` — a merely *paused* player still counts
   as active and would keep the notification and the foreground state forever.
   So pausing a stream stops it: the player goes idle and Media3 takes both the
   notification and the foreground state down. Nothing is lost by this. A live
   radio stream has no useful mid-stream resume position anyway.
2. **The media controller is bound only while a screen is on.** It is created in
   `onStart` and released in `onStop`. With no client bound and an idle player,
   Media3 shuts the service down; the process then becomes an ordinary cached
   process that the system can reclaim.
3. **Nothing polls.** There is no timer, no `postDelayed`, no `AlarmManager`, no
   `WorkManager` job, no reconnection loop and no periodic metadata refresh
   anywhere in the app. The ICY reader is a single blocking socket read that ends
   when playback does, and the directory mirror list is resolved once per process
   start. A paused app has nothing scheduled to wake up for.

Wake locks are ExoPlayer's, not ours: `ExoPlayer:WakeLockManager` and
`AudioMix` are held while a stream is being fed with the screen off, and released
by ExoPlayer on pause and on stop. That is the only wake lock in the app.

### Measured on a Xiaomi Watch 2 (Android 14, 512 mAh)

Read with `dumpsys media_session`, `dumpsys activity services`,
`dumpsys notification`, `dumpsys power`, `dumpsys batterystats` and
`dumpsys cpuinfo` over wireless ADB, with the app's own UID (`10000`).

| Situation | Before (permanently bound controller, no idle policy) | Now |
| --- | --- | --- |
| Playing, screen off | `isForeground=true`, `foregroundId=1001`, `types=00000002`; media notification; `ExoPlayer:WakeLockManager` + `AudioMix` + `ExoPlayer:WifiLockManager` held; ~26 % of one core | same, by design — this is the case the service is for |
| Paused, app off-screen | `isForeground=true` and notification `id=1001` **still resident**; UID still categorised `FGS`; process un-reclaimable | player goes idle, notification and foreground state drop, no client is bound, so the service and the process go away |
| App on screen, not playing | service resident | service bound (not foreground), no notification |
| App leaves the screen | service kept alive forever | no `ServiceRecord`, 0 notifications, UID leaves the `FGS` category |

Idle CPU of the paused process was ~0.11 s per 60 s (0.18 % of one core) — the
problem was never the CPU of an idle ExoPlayer, it was that the app never let go
of the foreground service and could not be reclaimed at all.

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
