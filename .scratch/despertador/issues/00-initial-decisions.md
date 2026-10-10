# Initial Decisions (Round 1)

Type: task
Status: resolved

## Question
Settle the destination, audio persistence strategy, alarm slot model, ringing interaction, and tech stack.

## Answer
- **Destination**: End-to-end technical specification (`spec.md`), domain model (`CONTEXT.md`), and an ordered implementation ticket backlog.
- **Audio Persistence**: When a user selects an MP3 file via SAF picker, copy it into the app's internal sandbox (`context.filesDir`) to ensure reliability regardless of external file moves/deletions.
- **Alarm Capacity**: Dynamic list starting empty or with default alarm, addable via FAB, capped at a maximum of 5 alarms.
- **Ringing Interaction**: Full-screen takeover over the lock screen (`USE_FULL_SCREEN_INTENT`) with audio looped on the Alarm audio stream, providing prominent Dismiss and Snooze buttons with an auto-silence timeout.
- **Tech Stack**: Kotlin + Jetpack Compose + Material 3, AndroidX Media3 / MediaPlayer, Room DB, Min SDK 26 (Android 8.0) targeting API 35 (Android 15).
