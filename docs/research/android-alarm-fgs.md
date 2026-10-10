# Modern Android Background Alarm Triggering & Foreground Services (Android 12 to 15, API 31–35)

## Overview & Executive Summary

For an alarm clock application targeting modern Android versions (Android 12 / API 31 through Android 15 / API 35), guaranteeing 100% reliable alarm triggering and audio playback over the lock screen requires navigating major platform changes:
1. Exact alarm restrictions (`SCHEDULE_EXACT_ALARM` vs `USE_EXACT_ALARM`).
2. Exact timing API choice (`AlarmManager.setAlarmClock()`).
3. Lock screen display and notification permissions (`USE_FULL_SCREEN_INTENT` and `POST_NOTIFICATIONS`).
4. Foreground Service execution restrictions (`ForegroundServiceStartNotAllowedException`) and type declaration (`foregroundServiceType="mediaPlayback"`).
5. Audio routing, `AudioAttributes` (`USAGE_ALARM`, `CONTENT_TYPE_SONIFICATION`), stream volume mapping, and audio focus management.

---

## 1. Exact Alarm Permissions: `USE_EXACT_ALARM` vs `SCHEDULE_EXACT_ALARM` & Google Play Policy

### Primary Sources
- [Android Developers - Schedule exact alarms](https://developer.android.com/develop/background-work/scheduling/alarms)
- [Android 14 Behavior changes: Changes to scheduling exact alarms](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms)
- [Google Play Console Help - Exact alarm permissions policy](https://support.google.com/googleplay/android-developer/answer/13161072)
- [Android Developers Reference: Manifest.permission.USE_EXACT_ALARM](https://developer.android.com/reference/android/Manifest.permission#USE_EXACT_ALARM)
- [Android Developers Reference: Manifest.permission.SCHEDULE_EXACT_ALARM](https://developer.android.com/reference/android/Manifest.permission#SCHEDULE_EXACT_ALARM)

### Background & Evolution
- **Android 12 (API 31)** introduced `SCHEDULE_EXACT_ALARM` as an App Ops permission. Newly installed apps were granted it by default, but users could revoke it in system settings (`Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM`).
- **Android 13 (API 33)** introduced `USE_EXACT_ALARM`. It is a normal permission (granted automatically at install time without user prompt) specifically intended for core alarm clock and timer apps.
- **Android 14 (API 34)** flipped the default for `SCHEDULE_EXACT_ALARM`: apps targeting Android 13+ newly installed on Android 14+ are **denied** `SCHEDULE_EXACT_ALARM` by default.

### `USE_EXACT_ALARM` vs `SCHEDULE_EXACT_ALARM` Comparison

| Attribute | `USE_EXACT_ALARM` | `SCHEDULE_EXACT_ALARM` |
| :--- | :--- | :--- |
| **Introduced** | Android 13 (API 33) | Android 12 (API 31) |
| **Permission Type** | Normal permission (`android:protectionLevel="normal"`) | AppOp / Special App Access |
| **Grant Behavior** | **Automatically granted** at install time | Denied by default on Android 14+; requires user prompt to settings |
| **User Revocation** | User cannot toggle it off directly in Special App Access | User can revoke anytime in system settings |
| **Target Apps** | **Alarm clocks**, timers, calendar apps whose core feature requires exact alarms | General apps needing precision (e.g. reminders, medication tracking) |
| **Google Play Policy** | Highly restricted; must qualify under strict core functional category | Permitted for broader apps with user consent |

### Google Play Policy Requirements for `USE_EXACT_ALARM`
Google Play enforces an explicit policy for `USE_EXACT_ALARM`:
- **Eligible Core Use Cases:**
  1. **Alarm clock** or timer application (the core, primary purpose of the app is setting alarms or timers).
  2. Calendar application displaying upcoming events and scheduled appointments.
- **Audit & Rejection:** Apps using `USE_EXACT_ALARM` must complete a declaration in Google Play Console. If the app is not primarily an alarm clock, timer, or calendar, the submission is rejected.
- **Application to Despertador:** Because Despertador is fundamentally a dedicated alarm clock application, it qualifies directly for `USE_EXACT_ALARM`.

### Recommended Manifest Declaration & Checks
```xml
<!-- For Android 13+ (API 33+) -->
<uses-permission android:name="android.permission.USE_EXACT_ALARM" />
<!-- For Android 12 (API 31-32) backwards compatibility -->
<uses-permission
    android:name="android.permission.SCHEDULE_EXACT_ALARM"
    android:maxSdkVersion="32" />
```
At runtime:
```kotlin
val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    val canSchedule = alarmManager.canScheduleExactAlarms()
    // Always returns true if USE_EXACT_ALARM is granted (API 33+)
    // or SCHEDULE_EXACT_ALARM is granted (API 31-32)
}
```

---

## 2. AlarmManager.setAlarmClock() Requirements

### Primary Sources
- [Android Developers Reference: AlarmManager.setAlarmClock()](https://developer.android.com/reference/android/app/AlarmManager#setAlarmClock(android.app.AlarmManager.AlarmClockInfo,%20android.app.PendingIntent))
- [Android Developers Reference: AlarmManager.AlarmClockInfo](https://developer.android.com/reference/android/app/AlarmManager.AlarmClockInfo)
- [Android Developers - Optimize for Doze and App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby)

### Why `setAlarmClock()` is Essential
1. **Immunity to Doze & App Standby:** Alarms scheduled via `setAlarmClock()` are treated as user-visible clock alarms. The platform brings the system out of deep Doze mode temporarily and never batches or defers the alarm time.
2. **System UI Visibility:** It registers the alarm with the system lock screen, status bar, and Quick Settings clock tile, displaying the next alarm time to the user.
3. **Execution Delivery:** When triggered, the system invokes the `PendingIntent` operation with high priority.

### API Signature & Parameters
```kotlin
fun setAlarmClock(
    info: AlarmManager.AlarmClockInfo,
    operation: PendingIntent
)
```

1. **`AlarmManager.AlarmClockInfo(triggerTime: Long, showIntent: PendingIntent)`**:
   - `triggerTime`: UTC timestamp in milliseconds (`System.currentTimeMillis() + offset`). Must use epoch wall-clock time (`RTC_WAKEUP` semantics), NOT elapsed real time.
   - `showIntent`: A `PendingIntent` that targets an Activity in the app showing alarm details (e.g., the alarm editing or overview screen). The Android system fires this `showIntent` when the user clicks the alarm clock icon or timestamp in the lock screen / notification shade.
2. **`operation: PendingIntent`**:
   - The intent fired when the alarm triggers.
   - Must be an explicit broadcast receiver (`PendingIntent.getBroadcast`) or activity.
   - For Android 12+ (API 31+), mutability flags must be explicitly set:
     ```kotlin
     val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
     ```

### Device Reboot & Time Adjustments
- Alarms scheduled in `AlarmManager` are cleared upon device shutdown or restart.
- An alarm clock app must register a `BroadcastReceiver` listening for `Intent.ACTION_BOOT_COMPLETED` (and `Intent.ACTION_TIME_CHANGED`, `Intent.ACTION_TIMEZONE_CHANGED`) with the permission `<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />` to reschedule pending alarms.

---

## 3. Full-Screen Intents and Notification Permissions (Android 13/14+)

### Primary Sources
- [Android Developers - Display time-sensitive notifications](https://developer.android.com/develop/ui/views/notifications/time-sensitive)
- [Android Developers - Notification runtime permission (`POST_NOTIFICATIONS`)](https://developer.android.com/develop/ui/views/notifications/notification-permission)
- [Android 14 Behavior changes: Full-screen intent permission changes](https://developer.android.com/about/versions/14/changes/full-screen-intents)
- [Android Developers Reference: NotificationManager.canUseFullScreenIntent()](https://developer.android.com/reference/android/app/NotificationManager#canUseFullScreenIntent())
- [Android Developers Reference: Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT](https://developer.android.com/reference/android/provider/Settings#ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)

### `POST_NOTIFICATIONS` (Android 13+, API 33)
- Required to display status bar notifications and heads-up notifications.
- Declared in Manifest:
  ```xml
  <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
  ```
- Must be requested at runtime using the standard runtime permission request flow (`ActivityResultContracts.RequestPermission()`).
- If denied, the app cannot display notifications or notification actions (e.g. Snooze / Dismiss buttons in the notification tray), though foreground services can still run in a restricted state.

### `USE_FULL_SCREEN_INTENT` (Android 14+, API 34)
- **Purpose:** Allows a high-priority notification to launch a full-screen Activity immediately when the device screen is off or locked.
- **Android 14 Restriction:** In Android 14, `android.permission.USE_FULL_SCREEN_INTENT` is **restricted exclusively to calling and alarm apps**. Google Play revokes this permission on install for apps outside these categories.
- **Runtime Verification:** Android 14 introduced `NotificationManager.canUseFullScreenIntent()`.
  ```kotlin
  val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      if (!notificationManager.canUseFullScreenIntent()) {
          // Guide user to Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT
          val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
              data = Uri.fromParts("package", context.packageName, null)
          }
          context.startActivity(intent)
      }
  }
  ```

### Notification Configuration for Full-Screen Launch
To fire the full-screen activity over the lock screen:
1. **NotificationChannel Importance:** Must be `NotificationManager.IMPORTANCE_HIGH`.
2. **Category:** Must be `Notification.CATEGORY_ALARM`.
3. **`setFullScreenIntent(pendingIntent, true)`**:
   ```kotlin
   val fullScreenIntent = Intent(context, AlarmRingingActivity::class.java).apply {
       flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION
   }
   val fullScreenPendingIntent = PendingIntent.getActivity(
       context,
       requestCode,
       fullScreenIntent,
       PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
   )

   val notification = NotificationCompat.Builder(context, CHANNEL_ID)
       .setSmallIcon(R.drawable.ic_alarm)
       .setContentTitle("Alarm")
       .setContentText("Ringing...")
       .setPriority(NotificationCompat.PRIORITY_MAX)
       .setCategory(NotificationCompat.CATEGORY_ALARM)
       .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
       .setOngoing(true)
       .setFullScreenIntent(fullScreenPendingIntent, true)
       .build()
   ```

### Lock Screen Activity Display (`setShowWhenLocked` & `setTurnScreenOn`)
In `AlarmRingingActivity`:
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        setShowWhenLocked(true)
        setTurnScreenOn(true)
    } else {
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
    }
    // Optional: dismiss keyguard if user interacts
    val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        keyguardManager.requestDismissKeyguard(this, null)
    }
}
```

---

## 4. Foreground Service Types: `mediaPlayback` vs `specialUse`

### Primary Sources
- [Android Developers - Foreground service types (Android 14)](https://developer.android.com/about/versions/14/changes/fgs-types-required)
- [Android Developers - Foreground service restrictions](https://developer.android.com/guide/components/foreground-services#background-start-restrictions)
- [Android 15 Behavior changes: All apps - Foreground services](https://developer.android.com/about/versions/15/behavior-changes-15#foreground-services)
- [Google Play Policy - Foreground services](https://support.google.com/googleplay/android-developer/answer/13392821)

### Android 14 FGS Type Enforcement
Starting in Android 14 (API 34), any app calling `startForeground()` must:
1. Declare the `android:foregroundServiceType` attribute in the manifest.
2. Hold the corresponding permission (e.g. `FOREGROUND_SERVICE_MEDIA_PLAYBACK`).
Failure to do so throws a runtime `MissingForegroundServiceTypeException` or `SecurityException`.

### Comparison: `mediaPlayback` vs `specialUse`

| Feature | `mediaPlayback` | `specialUse` |
| :--- | :--- | :--- |
| **Manifest attribute** | `android:foregroundServiceType="mediaPlayback"` | `android:foregroundServiceType="specialUse"` |
| **Permission** | `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | `FOREGROUND_SERVICE_SPECIAL_USE` |
| **Purpose** | Ongoing audio/video playback from the background (e.g. audio players, media streams). | Fallback category for valid FGS use cases that do not fit any other type. |
| **Metadata requirements** | None | Requires `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" .../>` |
| **Play Console Scrutiny** | Standard policy review for media players. | Extremely strict; high risk of rejection if another type can describe the work. |
| **Suitability for Alarm Audio** | **Recommended & Ideal**. Playing continuous MP3 audio media tracks directly matches the audio playback contract. | Not recommended unless no media playback is involved. |

### Background Launch Exemption via Exact Alarms
Android 12 (API 31+) restricts starting foreground services from the background, throwing `ForegroundServiceStartNotAllowedException`.
However, the Android platform explicitly specifies an exemption:
> *"Your app invokes an exact alarm to complete an action that the user requests."*

When `AlarmManager.setAlarmClock()` fires, the broadcast receiver receives a temporary system grace period allowing it to call `ContextCompat.startForegroundService()` without throwing `ForegroundServiceStartNotAllowedException`. Within `Service.onCreate()` or `onStartCommand()`, `startForeground(id, notification, FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)` must be invoked within seconds.

### Critical Android 15 (API 35) Nuance: `BOOT_COMPLETED` Restriction
In Android 15, `BOOT_COMPLETED` broadcast receivers are **explicitly forbidden** from launching foreground services of type `mediaPlayback` (along with `dataSync`, `camera`, `phoneCall`).
- **Impact on Alarm Clock:** The `BOOT_COMPLETED` receiver must **only reschedule alarms** in `AlarmManager` via `setAlarmClock()`. It must **never** start the media playback foreground service directly at boot. The foreground service is started only when the actual alarm triggers via its exact alarm pending intent.

---

## 5. Audio Stream Routing, `AudioAttributes`, and Audio Focus

### Primary Sources
- [Android Developers Reference: AudioAttributes](https://developer.android.com/reference/android/media/AudioAttributes)
- [Android Developers Reference: AudioAttributes.Builder](https://developer.android.com/reference/android/media/AudioAttributes.Builder)
- [Android Developers Reference: AudioFocusRequest](https://developer.android.com/reference/android/media/AudioFocusRequest)
- [Android Developers - Manage audio focus](https://developer.android.com/media/optimize/audio-focus)
- [Android 15 Behavior changes: Audio focus](https://developer.android.com/about/versions/15/behavior-changes-15#audio-focus)

### `AudioAttributes` Configuration
Legacy Android used stream constants like `AudioManager.STREAM_ALARM`. Modern Android requires configuring `AudioAttributes`:
```kotlin
val audioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_ALARM)
    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
    .build()
```

#### Why `USAGE_ALARM`?
1. **Physical Volume Routing:** Routes audio to the hardware **Alarm volume stream**, which is independent of the Media and Ring/Notification volume sliders.
2. **Do Not Disturb (DND) Bypass:** Alarms with `USAGE_ALARM` are treated by the Android notification and sound policy as high-priority alarms, allowing them to sound even when the device is in Do Not Disturb / Priority Only mode (unless the user explicitly mutes alarms in DND settings).
3. **Silent / Vibrate Switch Bypass:** Bypasses standard ringer muting on devices where the physical switch only mutes ringers and notifications.

#### Why `CONTENT_TYPE_SONIFICATION`?
- Identifies the sound as an alerting sound or sonification (accompaniment to an event) rather than ambient speech or soundtrack music. If playing long music MP3 tracks, `CONTENT_TYPE_MUSIC` combined with `USAGE_ALARM` is also valid, but Android framework guidelines recommend `USAGE_ALARM` with `CONTENT_TYPE_SONIFICATION` for alarm signals.

### Audio Focus Management (`AudioFocusRequest`)
Audio focus ensures other media (podcasts, music, YouTube) pauses or lowers volume when the alarm goes off.

#### Focus Gain Type
Use `AudioManager.AUDIOFOCUS_GAIN_TRANSIENT` (or `AUDIOFOCUS_GAIN`):
- `AUDIOFOCUS_GAIN_TRANSIENT`: Appropriate because the alarm is a temporary interruption; when the alarm is dismissed or snoozed, the previous audio session can resume.

#### Constructing `AudioFocusRequest` (API 26+)
```kotlin
val audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
    .setAudioAttributes(audioAttributes)
    .setAcceptsDelayedFocusGain(false)
    .setWillPauseWhenDucked(false)
    .setOnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Permanent loss: another alarm or phone call took over
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Temporary loss: incoming phone call
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Regained focus
            }
        }
    }
    .build()

val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
val result = audioManager.requestAudioFocus(audioFocusRequest)
```

#### Android 15 Audio Focus Enforcement
In Android 15 (API 35):
- An app must be the **top app** (visible activity) or **running an active foreground service** when requesting audio focus.
- Calling `audioManager.requestAudioFocus()` from background components without an active foreground service returns `AUDIOFOCUS_REQUEST_FAILED`.
- Therefore, the alarm playback service must execute `startForeground()` **before** requesting audio focus.

---

## 6. End-to-End Architectural Recipe for Despertador

1. **Manifest Declarations**:
   ```xml
   <uses-permission android:name="android.permission.USE_EXACT_ALARM" />
   <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" android:maxSdkVersion="32" />
   <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
   <uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />
   <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
   <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />
   <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
   <uses-permission android:name="android.permission.WAKE_LOCK" />

   <service
       android:name=".playback.AlarmPlaybackService"
       android:foregroundServiceType="mediaPlayback"
       android:exported="false" />

   <receiver
       android:name=".trigger.AlarmTriggerReceiver"
       android:exported="false" />

   <receiver
       android:name=".trigger.BootReceiver"
       android:exported="true">
       <intent-filter>
           <action android:name="android.intent.action.BOOT_COMPLETED" />
           <action android:name="android.intent.action.TIME_SET" />
           <action android:name="android.intent.action.TIMEZONE_CHANGED" />
       </intent-filter>
   </receiver>
   ```

2. **Scheduling**:
   - `AlarmManager.setAlarmClock(AlarmClockInfo(triggerTime, showPendingIntent), triggerPendingIntent)`

3. **Trigger Delivery (`AlarmTriggerReceiver`)**:
   - On receive: Start `AlarmPlaybackService` via `ContextCompat.startForegroundService()`.

4. **Service Execution (`AlarmPlaybackService`)**:
   - Create notification on channel with `IMPORTANCE_HIGH` and `AudioAttributes.USAGE_ALARM`.
   - Attach `fullScreenIntent` pointing to `AlarmRingingActivity`.
   - Call `ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)`.
   - Request audio focus via `AudioManager.requestAudioFocus(focusRequest)`.
   - Initialize audio player (e.g., `MediaPlayer` or `ExoPlayer/Media3`) with `AudioAttributes(USAGE_ALARM, CONTENT_TYPE_SONIFICATION)`.
   - Loop audio playback.

5. **Lock Screen Presentation (`AlarmRingingActivity`)**:
   - Call `setShowWhenLocked(true)` and `setTurnScreenOn(true)`.
   - Provide Snooze and Dismiss actions that communicate with `AlarmPlaybackService`.
