# Despertador — Technical Specification & Architecture Spec

- **Project**: Despertador (Native Android Alarm Clock)
- **License**: GNU General Public License v3 (GPLv3)
- **Target OS**: Android 8.0 (API 26) through Android 15 (API 35)
- **Status**: Final Ready-to-Implement Specification

---

## 1. Domain & Core Capabilities

Despertador is a reliable, offline-first native Android alarm clock application designed to play custom local MP3 media tracks.

### Functional Requirements
1. **Dynamic Alarm Slots**: Users can configure up to **5 alarms**. An interactive FAB allows adding new alarms and automatically disables when 5 alarms exist.
2. **Alarm Scheduling**:
   - Time of day (Hour, Minute in 24h or AM/PM).
   - Recurrence by day of week (Monday through Sunday). Non-recurring alarms fire once and deactivate.
   - Custom alarm name/label.
3. **Custom MP3 Audio Tracks**:
   - Users pick local audio files via Android Storage Access Framework (`ACTION_OPEN_DOCUMENT`).
   - Files are copied into the app's internal sandbox (`context.filesDir/alarms/`) with SHA-256 content deduplication to guarantee playback even if the source file is deleted or moved.
4. **Reliable Alarm Execution**:
   - Uses `AlarmManager.setAlarmClock()` to guarantee exact execution across Android Doze mode and App Standby.
   - Triggers `AlarmPlaybackService` (`foregroundServiceType="mediaPlayback"`).
   - Launches `RingingActivity` as a full-screen intent (`USE_FULL_SCREEN_INTENT`) over the lock screen.
5. **Gentle Wake & Audio Playback**:
   - Looping playback via AndroidX Media3 ExoPlayer on `AudioAttributes.USAGE_ALARM`.
   - Linear volume escalation from 20% to 100% over the first 30 seconds.
   - Respects Android system hardware alarm stream volume (`AudioManager.STREAM_ALARM`).
6. **Snooze & Dismiss Lifecycle**:
   - Unlimited snoozing using a globally configurable snooze interval (default 10 minutes).
   - Dismiss stops playback, releases audio focus, and schedules the next recurrence.
7. **Boot & Timezone Resilience**:
   - Broadcast receiver listening for `ACTION_BOOT_COMPLETED`, `ACTION_TIMEZONE_CHANGED`, and `ACTION_TIME_CHANGED` to re-register alarms in `AlarmManager` directly from the Room database.

---

## 2. Technology Stack & Dependencies

| Layer | Technology | Rationale |
| :--- | :--- | :--- |
| **Language** | Kotlin 2.0+ with Coroutines & Flow | Modern Android standard, type safety, structured concurrency. |
| **UI Framework** | Jetpack Compose + Material 3 | Declarative UI, state hoisting, clean animations. |
| **Database** | Room Persistence Library | Local SQLite abstraction with compile-time query verification. |
| **Audio Engine** | AndroidX Media3 ExoPlayer | Flawless gapless looping, programmatic volume ramping, robust state machine. |
| **Preferences** | Jetpack DataStore (Preferences) | Typed asynchronous key-value storage for global settings. |
| **Build System** | Gradle (Kotlin DSL) with Version Catalogs | Standard Android Gradle Plugin (AGP 8.5+), JDK 21. |

---

## 3. Permissions & Android Manifest Declarations

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <!-- Exact Alarm scheduling (API 33+ normal permission for alarm clocks) -->
    <uses-permission android:name="android.permission.USE_EXACT_ALARM" />
    <!-- Backwards compatibility for Android 12 (API 31-32) -->
    <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" android:maxSdkVersion="32" />

    <!-- Lock screen wake-up -->
    <uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.VIBRATE" />

    <!-- Notifications & Foreground Service -->
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />

    <!-- Boot and system time recovery -->
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

    <application
        android:name=".DespertadorApp"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.Despertador">

        <!-- Main UI Activity -->
        <activity
            android:name=".ui.MainActivity"
            android:exported="true"
            android:theme="@style/Theme.Despertador">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- Full-Screen Ringing Activity (over lock screen) -->
        <activity
            android:name=".ui.ringing.RingingActivity"
            android:exported="false"
            android:showWhenLocked="true"
            android:turnScreenOn="true"
            android:excludeFromRecents="true"
            android:launchMode="singleTask"
            android:theme="@style/Theme.Despertador.Ringing" />

        <!-- Alarm Playback Foreground Service -->
        <service
            android:name=".service.AlarmPlaybackService"
            android:exported="false"
            android:foregroundServiceType="mediaPlayback" />

        <!-- Exact Alarm Trigger Receiver -->
        <receiver
            android:name=".receiver.AlarmReceiver"
            android:exported="false" />

        <!-- Boot & Timezone Resilience Receiver -->
        <receiver
            android:name=".receiver.BootReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.TIME_SET" />
                <action android:name="android.intent.action.TIMEZONE_CHANGED" />
            </intent-filter>
        </receiver>

    </application>
</manifest>
```

---

## 4. Architecture & Component Seams

### 4.1 Data Model (Room Database)

```kotlin
@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hour: Int,                // 0..23
    val minute: Int,              // 0..59
    val label: String,            // User name for alarm
    val daysOfWeekMask: Int,      // Bitmask (1 = Mon, 2 = Tue, ..., 64 = Sun)
    val isEnabled: Boolean,       // Toggle state
    val cachedAudioPath: String,  // Relative or absolute path in context.filesDir/alarms/
    val originalAudioName: String // Display name of MP3 file
)
```

### 4.2 Core Interfaces & Seams

1. **`AlarmScheduler`**:
   - `fun schedule(alarm: AlarmEntity)`
   - `fun cancel(alarmId: Long)`
   - `fun rescheduleAllActive()`
   - Implemented using `AlarmManager.setAlarmClock()`.

2. **`AudioStorageManager`**:
   - `suspend fun copyAndCache(contentUri: Uri): Result<CachedAudio>`
   - `suspend fun pruneOrphans(activePaths: Set<String>)`
   - Handles stream copying, SHA-256 hashing, size validation (max 50 MB), and atomic write.

3. **`AlarmAudioPlayer`**:
   - `fun startPlayback(audioPath: String, gentleWake: Boolean)`
   - `fun stopPlayback()`
   - Manages Media3 ExoPlayer instance, `USAGE_ALARM` audio attributes, and 30s linear volume ramp coroutine.

---

## 5. UI Architecture & Flow

### Screen Hierarchy
1. **`AlarmListScreen`**:
   - TopAppBar with app title and settings gear icon.
   - Dynamic list of `AlarmCard` items with toggle switch, time, label, recurrence chips, and audio track chip.
   - Floating Action Button (+) with active counter (`X / 5 alarms`). Disables when 5 alarms exist.
2. **`AlarmEditorBottomSheet`**:
   - TimePicker (Hour, Minute).
   - Alarm label input field.
   - Day-of-week recurrence toggle chips (M, T, W, T, F, S, S).
   - Audio track selector with SAF file picker launcher and track preview.
   - Save / Delete / Cancel controls.
3. **`RingingActivity`**:
   - Dedicated immersive activity waking up lock screen.
   - Displays ringing clock, label, and audio track name.
   - Visual Gentle Wake volume ramp progress indicator.
   - Primary **Dismiss** button and secondary **Snooze** button.
4. **`SettingsDialog`**:
   - Global snooze duration configuration (5, 10, 15, 20 minutes).

---

## 6. Implementation Task Backlog

The following backlog defines the step-by-step implementation plan for execution:

1. **Task 1 — Project Initialization & Gradle Scaffolding**:
   - Set up Android project structure (`app/` module), Gradle version catalog (`gradle/libs.versions.toml`), AndroidX Compose, Room, and Media3 dependencies.
2. **Task 2 — Storage Sandbox & Audio Management**:
   - Implement `AudioStorageManager` for SAF MP3 streaming, SHA-256 deduplication, header validation, and orphan pruning.
3. **Task 3 — Room Database & Alarm Repository**:
   - Implement `AlarmEntity`, `AlarmDao`, `DespertadorDatabase`, and `AlarmRepository` with Flow observation.
4. **Task 4 — Alarm Scheduling & Foreground Playback Service**:
   - Implement `AlarmScheduler` with `AlarmManager.setAlarmClock()`, `AlarmReceiver`, and `AlarmPlaybackService` (`mediaPlayback` FGS) with Media3 ExoPlayer Gentle Wake.
5. **Task 5 — Boot & Timezone Resilience**:
   - Implement `BootReceiver` to re-register alarms upon `BOOT_COMPLETED`, `TIME_SET`, and `TIMEZONE_CHANGED`.
6. **Task 6 — Jetpack Compose UI (List, Editor & Settings)**:
   - Implement `AlarmListScreen`, `AlarmEditorBottomSheet`, and `SettingsDialog` with Material 3 components and ViewModel.
7. **Task 7 — Ringing Activity & Lock Screen Integration**:
   - Implement `RingingActivity` with `showWhenLocked`, `turnScreenOn`, dismiss/snooze controls, and volume ramp visualization.
8. **Task 8 — End-to-End Verification & GitHub Release**:
   - Complete debug APK build, verify on device/emulator, write user documentation, and push initial repository release.
