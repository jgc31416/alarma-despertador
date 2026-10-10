# Research Android 14+ Foreground Service and Exact Alarm Restrictions

Type: research
Status: resolved
Blocked by:

## Question
What are the exact permissions (`USE_EXACT_ALARM`, `USE_FULL_SCREEN_INTENT`, `POST_NOTIFICATIONS`), Foreground Service types (`foregroundServiceType="mediaPlayback"` vs `specialUse`), `AlarmManager.setAlarmClock()`, and `AudioAttributes` needed to ensure 100% reliable alarm triggering and audio playback over the lock screen on modern Android versions (Android 12 through Android 15)?

## Answer
The detailed research findings with primary source citations have been documented in [`docs/research/android-alarm-fgs.md`](../../docs/research/android-alarm-fgs.md).

Key answers summary:
1. **Permissions (`USE_EXACT_ALARM` vs `SCHEDULE_EXACT_ALARM`)**: `USE_EXACT_ALARM` is a normal permission granted automatically upon install in Android 13+ (API 33+) without user prompts. Dedicated alarm clocks qualify under Google Play policy. Use `USE_EXACT_ALARM` alongside `SCHEDULE_EXACT_ALARM` with `android:maxSdkVersion="32"` for backward compatibility on Android 12.
2. **AlarmManager.setAlarmClock()**: Requires `AlarmClockInfo(triggerTime, showIntent)`. Treats alarms as critical clock events that bypass Doze/App Standby without deferral, and displays next alarm in the system status bar and lock screen.
3. **Lock Screen & Notifications**: Declare `USE_FULL_SCREEN_INTENT` (granted automatically to alarm apps on Android 14+; verify with `NotificationManager.canUseFullScreenIntent()`) and `POST_NOTIFICATIONS` (runtime permission on Android 13+). Trigger full-screen ringing using `NotificationCompat.Builder.setFullScreenIntent()` paired with `setShowWhenLocked(true)` and `setTurnScreenOn(true)` on the activity.
4. **Foreground Service Type**: Use `foregroundServiceType="mediaPlayback"` with `FOREGROUND_SERVICE_MEDIA_PLAYBACK` permission. Exact alarms grant an exemption to `ForegroundServiceStartNotAllowedException`. Avoid `specialUse` as media playback directly describes looping audio tracks and does not risk Google Play rejection. In Android 15, `BOOT_COMPLETED` cannot start `mediaPlayback` services, so boot receivers should only reschedule alarms with `setAlarmClock()`.
5. **Audio Routing & Focus**: Configure `AudioAttributes` with `USAGE_ALARM` and `CONTENT_TYPE_SONIFICATION` to route to the alarm hardware stream and bypass Do Not Disturb / silent mode. Request transient audio focus (`AudioManager.AUDIOFOCUS_GAIN_TRANSIENT`) from within the active foreground service before playback.

