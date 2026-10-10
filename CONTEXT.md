# Despertador

A native Android alarm clock application for scheduling and triggering customizable alarms that play local MP3 audio media tracks.

## Language

**Alarm**:
A scheduled event defined by a time of day, a custom name, active recurrence days, and an associated audio media track.
_Avoid_: Reminder, alert, timer, notification.

**Alarm Slot**:
One of up to five distinct alarm instances managed by the user within the application.
_Avoid_: Preset, entry, profile.

**Audio Media Track**:
An MP3 file selected by the user and stored in the application's internal private storage for playback when an alarm triggers.
_Avoid_: Ringtone, sound effect, song, music.

**Recurrence**:
The specific days of the week on which an alarm automatically triggers.
_Avoid_: Schedule, repeat pattern, frequency.

**Ringing State**:
The state where an alarm has triggered and is actively playing audio in a loop over the lock screen until dismissed, snoozed, or timed out.
_Avoid_: Alerting, sounding, going off.

**Snooze**:
A temporary deferral of an active ringing alarm, rescheduling it to ring again after a preset interval.
_Avoid_: Delay, pause, postpone.

**Dismiss**:
The user action that stops the active ringing state and schedules the next recurrence (or deactivates the alarm if non-recurring).
_Avoid_: Stop, cancel, turn off, shut down.
