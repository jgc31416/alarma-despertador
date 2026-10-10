## Destination

An end-to-end technical specification (`spec.md`), verified domain model (`CONTEXT.md`), and an ordered implementation ticket backlog for building and releasing the GPLv3 native Android alarm clock app ("Despertador").

## Notes

- Domain: Native Android Development (Kotlin, Jetpack Compose, Material 3, AndroidX Media3 / MediaPlayer, Room DB, AlarmManager, Foreground Services).
- Target OS: Min SDK 26 (Android 8.0) up to Target SDK 35 (Android 15).
- License: GNU General Public License v3 (GPLv3).
- Repository: Hosted on GitHub under user's account.
- Skills every session should consult: `grilling`, `domain-modeling`, `codebase-design`.

## Decisions so far

- [Resolved Destination and Core Architecture](issues/00-initial-decisions.md): Locked destination as spec + backlog; audio persistence via internal sandbox copy; dynamic list capped at 5 alarms; full-screen lockscreen ringing with Dismiss + Snooze; Kotlin + Compose + Room stack.
- [Research Android 14+ Foreground Service and Exact Alarm Restrictions](issues/01-research-android-alarm-fgs.md): Use `USE_EXACT_ALARM` with `setAlarmClock()`, `foregroundServiceType="mediaPlayback"`, `USAGE_ALARM` audio routing, and `USE_FULL_SCREEN_INTENT`.
- [Research In-App MP3 Caching and ExoPlayer/MediaPlayer Lifecycle](issues/02-research-audio-playback-and-caching.md): Copy SAF stream into `context.filesDir` with SHA-256 deduplication and header validation; use AndroidX Media3 ExoPlayer for seamless gapless looping.
- [Scaffold GPLv3 License and Git Baseline](issues/03-task-gplv3-license-and-git-scaffold.md): Added GPLv3 LICENSE, Android .gitignore, and project README.md.
- [Decide Snooze Duration and Volume Behavior](issues/04-grilling-snooze-and-volume-policy.md): Global configurable snooze interval; unlimited snooze count; 30s gentle volume ramp from 20%; respects system alarm stream volume.

## Not yet specified

- Boot & Timezone Resilience: Broadcast receivers and Room DB sync for re-registering alarms after device reboots or timezone changes.
- In-App Audio Preview & Format Validation: Handling corrupted or non-MP3 files gracefully during file selection.
- GitHub Remote & CI Pipeline: GitHub Actions workflow for linting, building, and generating debug APKs.

## Out of scope

- Cloud sync, user accounts, and remote backup (pure local app only).
- Streaming audio URLs or radio stations (local MP3 files only).
- Unlimited alarms (strictly capped at 5 alarm slots).
- Home screen app widgets (v1 is in-app only).
