# Despertador ⏰

An open-source, reliable native Android alarm clock application built with modern Kotlin, Jetpack Compose, Material 3, and AndroidX Media3.

## Features

- **Up to 5 Configurable Alarms**: Create and manage up to 5 alarm slots with distinct names and day-of-week recurrences.
- **Custom MP3 Audio Tracks**: Pick local MP3 files via Android's Storage Access Framework; tracks are securely cached into the application's internal private storage sandbox (`context.filesDir`) to guarantee playback reliability.
- **Full-Screen Ringing & Lock Screen Wakeup**: Immediate full-screen takeover over the device lock screen with prominent Dismiss and Snooze controls.
- **Fail-Safe Audio Playback**: Plays looping audio on the hardware `USAGE_ALARM` stream, safely bypassing Do Not Disturb and hardware silent switches using AndroidX Media3 ExoPlayer.
- **Reboot & Timezone Resilience**: Automatically restores scheduled alarms upon device reboot and handles system timezone shifts.

## Tech Stack

- **Language**: Kotlin 2.0+
- **UI Toolkit**: Jetpack Compose + Material Design 3
- **Database**: Room Persistence Library
- **Audio Engine**: AndroidX Media3 ExoPlayer
- **Alarm Scheduling**: `AlarmManager.setAlarmClock()` + Foreground Service (`mediaPlayback`)
- **Compatibility**: Android 8.0 (API 26) through Android 15 (API 35)

## Architecture

See the documentation in `docs/` and the Wayfinder planning artifacts in `.scratch/despertador/`:
- [Domain Glossary](CONTEXT.md)
- [Wayfinder Map](.scratch/despertador/map.md)
- [Foreground Service & Alarm Research](docs/research/android-alarm-fgs.md)
- [Audio Selection, Validation & Caching Research](docs/research/audio-playback-caching.md)

## License

This program is free software: you can redistribute it and/or modify it under the terms of the **GNU General Public License as published by the Free Software Foundation, version 3 (GPLv3)**.

See [LICENSE](LICENSE) for the full license text.
