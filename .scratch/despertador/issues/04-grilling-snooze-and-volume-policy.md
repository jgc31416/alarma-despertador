# Decide Snooze Duration and Volume Behavior

Type: grilling
Status: resolved
Blocked by: 01

## Question
What should the snooze interval policy be (fixed duration such as 5 or 10 minutes vs user-customizable per alarm), what is the maximum allowed snooze count (or infinite), and how should in-app alarm volume interact with Android system alarm stream volume?

## Answer
1. **Snooze Duration**: Configurable globally in an app settings screen (defaulting to 10 minutes, options: 5, 10, 15, 20 minutes) persisted via Jetpack DataStore / Room.
2. **Snooze Repetition**: Unlimited snoozing — the user can snooze the alarm as many times as necessary without a hard cap.
3. **Gentle Wake**: Mandatory gentle volume ramp enabled for all alarms: audio begins at 20% volume and escalates linearly to 100% over a 30-second window.
4. **Volume Control**: Strictly respects Android system hardware alarm stream volume (`AudioManager.STREAM_ALARM`), avoiding confusion with physical volume rockers.

