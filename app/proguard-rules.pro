# WristRadio keeps no reflection-based entry points of its own apart from the
# Media3 media-source factories, which DefaultMediaSourceFactory looks up by
# name at runtime; keep those from being stripped in a minified release build.
-dontwarn org.jetbrains.annotations.**
-keep class androidx.media3.exoplayer.hls.HlsMediaSource$Factory { *; }
-keep class androidx.media3.exoplayer.dash.DashMediaSource$Factory { *; }
-keep class androidx.media3.exoplayer.smoothstreaming.SsMediaSource$Factory { *; }
-keep class androidx.media3.exoplayer.rtsp.RtspMediaSource$Factory { *; }
-dontwarn androidx.media3.**
