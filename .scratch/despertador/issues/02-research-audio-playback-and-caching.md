# Research In-App MP3 Caching and ExoPlayer/MediaPlayer Lifecycle

Type: research
Status: unclaimed
Blocked by:

## Question
What is the optimal implementation pattern to safely copy selected audio files from Android Storage Access Framework (`ACTION_OPEN_DOCUMENT`) into `context.filesDir`, validate MP3 format/headers, prune unreferenced files when alarms are deleted, and decide between lightweight `android.media.MediaPlayer` vs Jetpack `androidx.media3.exoplayer` for reliable background looping playback?
