Status: ready-for-agent

# Specification: Despertador Native Android Alarm Clock

## Problem Statement

Android users frequently rely on default system clock applications that either lack support for custom MP3 audio files, require complex cloud subscriptions, or fail to wake the user gently. Furthermore, users often find that third-party alarm applications fail silently due to aggressive OEM background battery restrictions, Doze mode suspension, or lost file permissions when media tracks on external storage are moved or deleted. Users need a reliable, lightweight, privacy-respecting, and open-source alarm clock that guarantees custom audio playback, wake-up reliability across reboots and lock screens, and clean management of their wake-up routines.

## Solution

Despertador is a native, offline-first Android alarm clock released under the GNU General Public License v3 (GPLv3). It empowers users to manage up to five customizable alarms with individual names, recurrence days, and local MP3 audio files. When a user selects an MP3 track, Despertador copies the media into the application's private sandbox to prevent missing-file playback failures. When an alarm fires, the app takes over the lock screen with high-priority full-screen alerts and plays looping audio on the hardware alarm stream, starting softly at 20% volume and escalating linearly over 30 seconds (Gentle Wake). Users can snooze repeatedly or dismiss the alarm, with all schedules surviving device reboots and timezone shifts.

## User Stories

1. As a user, I want to see an initial screen displaying all my alarms with their times, names, recurrence days, and toggle switches, so that I can quickly review and control my alarms at a glance.
2. As a user, I want to activate or deactivate any alarm with a single toggle switch on the home screen, so that I can turn alarms on or off without opening an edit screen.
3. As a user, I want to create a new alarm by tapping an Add (+) button, so that I can set up a schedule for a new wake-up time.
4. As a user, I want the application to limit the total number of alarms to five, so that I have a focused set of wake-up times and avoid clutter.
5. As a user, I want the Add (+) button to visually disable when five alarms already exist, so that I immediately understand that the five-alarm capacity limit has been reached.
6. As a user, I want to assign a custom name to each alarm (e.g., "Workdays", "Gym Morning"), so that I can distinguish the purpose of each alarm.
7. As a user, I want to set the exact hour and minute for an alarm using an intuitive time picker, so that my alarm fires at the desired time.
8. As a user, I want to select which days of the week an alarm repeats (Monday through Sunday), so that I can tailor alarms to my weekday or weekend schedules.
9. As a user, I want an alarm with no repeating days selected to fire only once on the next upcoming matching time and then automatically deactivate, so that I am not woken up on subsequent days unintentionally.
10. As a user, I want an alarm with repeating days to automatically calculate and schedule the next recurring date after being dismissed, so that I don't have to manually re-arm it every week.
11. As a user, I want to select any local MP3 file from my device storage to use as the alarm tone, so that I can wake up to music I enjoy.
12. As a user, I want the app to copy the selected MP3 file into its internal private sandbox, so that the alarm will still play reliably even if I move, rename, or delete the original file on my storage.
13. As a user, I want the app to validate that selected files are genuine audio tracks with valid audio headers, so that corrupted or non-audio files are rejected with a clear error message instead of causing silent playback failures.
14. As a user, I want the app to automatically deduplicate identical MP3 files assigned across multiple alarms, so that storage space is not wasted by duplicate copies.
15. As a user, I want to preview and listen to the selected MP3 track while editing an alarm, so that I can confirm it sounds right before saving.
16. As a user, I want to delete an alarm I no longer need, so that I can free up an alarm slot for a new one.
17. As a user, I want orphaned audio files to be automatically deleted when the alarms referencing them are deleted, so that the app does not accumulate leftover files on my device.
18. As a user, I want the alarm to trigger reliably at the exact minute even when my device is in deep sleep or Doze mode, so that I never oversleep.
19. As a user, I want the alarm to wake up my screen and display a full-screen ringing interface over the lock screen, so that I can see the alarm name and controls without needing to unlock my device first.
20. As a user, I want the alarm audio to start softly at 20% volume and escalate gradually over 30 seconds to full volume (Gentle Wake), so that I am woken up smoothly rather than startled by sudden loud sound.
21. As a user, I want the alarm audio to play through the hardware alarm audio stream, so that it sounds even if my phone is on silent mode or Do Not Disturb is active.
22. As a user, I want the alarm audio to loop continuously and seamlessly until dismissed or snoozed, so that a short track continues playing until I wake up.
23. As a user, I want a prominent Dismiss button on the ringing screen, so that I can easily turn off the alarm when I am awake.
24. As a user, I want a Snooze button on the ringing screen, so that I can temporarily silence the alarm and get extra rest before it rings again.
25. As a user, I want to snooze an alarm unlimited times without a forced cut-off, so that I retain full control over when the alarm is finally dismissed.
26. As a user, I want to configure the global snooze duration (e.g., 5, 10, 15, or 20 minutes) in an app settings dialog, so that snooze timing matches my personal morning preference.
27. As a user, I want all active alarms to automatically reschedule themselves when my phone reboots, so that I do not miss an alarm after an overnight system restart or update.
28. As a user, I want all active alarms to adjust their trigger times automatically when the device timezone changes or daylight saving time shifts, so that alarms always go off at the correct local hour.
29. As a user, I want the alarm to automatically silence after a safety timeout (e.g., 10 minutes of unattended ringing), so that my battery is not completely drained if I leave my phone unattended.
30. As a user, I want the application to be fully open-source under GPLv3 and require zero account logins, advertising, or remote tracking, so that my personal privacy is fully respected.

## Implementation Decisions

### Modules & Architecture
The application is structured following Clean Architecture with reactive MVVM:
- **Core Domain & Entities**: Plain Kotlin data structures for alarms, recurrence days bitmask, and settings models. Devoid of Android framework dependencies.
- **Data & Storage Layer**:
  - `DespertadorDatabase` (Room SQLite) persisting `AlarmEntity`.
  - `AudioStorageManager` managing the copying of media from `content://` URIs via `ContentResolver` to `context.filesDir/alarms/` using atomic `.tmp` write-and-rename and SHA-256 deduplication.
  - `SettingsRepository` using Jetpack DataStore for global snooze duration preferences.
- **Alarm & Background Execution Layer**:
  - `AlarmScheduler` using Android `AlarmManager.setAlarmClock()` with `AlarmClockInfo` and a `showIntent` pointing to the main screen.
  - `AlarmReceiver` (`BroadcastReceiver`) receiving the exact alarm wake-up broadcast and starting the foreground service.
  - `AlarmPlaybackService` (`foregroundServiceType="mediaPlayback"`) holding a partial wake lock, acquiring audio focus (`AUDIOFOCUS_GAIN_TRANSIENT`), and managing AndroidX Media3 ExoPlayer.
  - `BootReceiver` listening for `ACTION_BOOT_COMPLETED`, `ACTION_TIME_SET`, and `ACTION_TIMEZONE_CHANGED` to reschedule all active alarms from the database.
- **Presentation Layer (Jetpack Compose + Material 3)**:
  - `AlarmListScreen`: Home screen displaying up to 5 alarm cards with inline activation toggles and capacity-capped FAB.
  - `AlarmEditorBottomSheet`: Modal sheet containing `TimePicker`, label input, day-of-week recurrence chips, and audio file picker.
  - `RingingActivity`: High-priority activity configured with `setShowWhenLocked(true)`, `setTurnScreenOn(true)`, and full-screen intent launch.
  - `SettingsDialog`: Dialog for selecting global snooze interval (5, 10, 15, 20 min).

### Key State Model (Validated by Prototype)
From the validated prototype on branch `prototype/compose-ui`:

```kotlin
data class Alarm(
    val id: Long = 0,
    val hour: Int,
    val minute: Int,
    val label: String,
    val daysOfWeek: Set<DayOfWeek>, // Monday..Sunday
    val isEnabled: Boolean,
    val cachedAudioPath: String,
    val audioFileName: String,
    val sha256Hash: String
)

data class AlarmListUiState(
    val alarms: List<Alarm> = emptyList(),
    val maxCapacity: Int = 5,
    val isCapacityReached: Boolean = false,
    val globalSnoozeMinutes: Int = 10
)
```

### Audio Validation & Pipeline Decisions
- When a user picks a file, the input stream is inspected before storage: ID3v2 container header (`0x49 0x44 0x33`) or raw MPEG frame sync word (`0xFF 0xE0` mask) must be present.
- Files exceeding 50 MB are rejected.
- Files are saved as `track_<sha256>.mp3` in `context.filesDir/alarms/`.
- Audio playback uses AndroidX Media3 ExoPlayer with `Player.REPEAT_MODE_ONE`.
- Gentle Wake coroutine scales ExoPlayer volume from 0.2f to 1.0f in 10 linear steps across 30 seconds.

## Testing Decisions

### What Makes a Good Test
Tests must verify external observable behavior across system seams rather than private implementation details. They must assert that state transitions, scheduling calculations, audio sandboxing invariants, and capacity constraints hold true without flakiness.

### Seams to be Tested
1. **Recurrence & Next-Trigger Seam (`AlarmScheduler` / `NextTriggerCalculator`)**:
   - Unit tests verifying exact millisecond calculation for:
     - Non-repeating alarm scheduled for later today.
     - Non-repeating alarm scheduled for a time that has already passed today (must schedule for tomorrow).
     - Repeating alarm for specific weekdays (e.g., Mon/Wed/Fri) calculating the exact next target day.
     - Leap years and daylight saving time transitions.
2. **Audio Storage & Ingestion Seam (`AudioStorageManager`)**:
   - Tests using mock input streams asserting:
     - Valid MP3 files are successfully saved with correct SHA-256 naming.
     - Identical content streams return the same cached path without creating duplicate files.
     - Corrupted or non-MP3 byte streams fail gracefully with an identifiable error.
     - Orphan pruning removes unreferenced files while preserving active tracks.
3. **Capacity & State Seam (`AlarmRepository` & `AlarmListViewModel`)**:
   - Tests asserting that adding alarms succeeds up to 5, and the 6th addition is strictly rejected.
   - Tests asserting that toggling an alarm updates its enabled status and invokes the scheduler correctly.
   - Tests asserting that deleting an alarm decrements the count and cancels the scheduled alarm.
4. **Snooze & Dismiss Logic Seam (`AlarmRingingController`)**:
   - Tests asserting that Snooze reschedules an alarm exactly `currentSnoozeMinutes` into the future.
   - Tests asserting that Dismiss deactivates non-recurring alarms and schedules the next cycle for recurring alarms.

## Out of Scope

- Remote cloud synchronization, accounts, and server-side backups (Despertador is 100% local and offline).
- Online streaming audio URLs, internet radio, or Spotify integration (local MP3 files only).
- Unlimited simultaneous alarms (strictly capped at 5 alarm slots to keep the application focused and simple).
- Home screen app widgets or Wear OS companion apps (out of scope for v1).
- Custom math puzzle challenges or shake-to-dismiss requirements.

## Further Notes

- The project repository is initialized with GPLv3 license terms in [LICENSE](file:///home/jesus/code/home/despertador/LICENSE) and git ignore rules in [.gitignore](file:///home/jesus/code/home/despertador/.gitignore).
- All architectural research findings and Android 14/15 permission nuances are documented in `docs/research/`.
- The interactive UI prototype is archived on branch `prototype/compose-ui` for layout and interaction reference.
