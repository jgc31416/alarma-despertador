# Prototype Jetpack Compose UI Screens for Alarm List and Ringing Activity

Type: prototype
Status: resolved
Blocked by: 04

## Question
How should the Jetpack Compose screens (Alarm List with dynamic 5-cap FAB, Alarm Editor with TimePicker and Day chips, and Full-Screen Ringing Screen with Dismiss and Snooze) be structured with Material 3 components and UI state models?

## Answer
The interactive UI and state machine prototype was constructed and captured on git branch `prototype/compose-ui` (viewable in browser at [`prototype/index.html`](file:///home/jesus/code/home/despertador/prototype/index.html)).

### Key UI Decisions Validated:
1. **Alarm List Screen**:
   - Displays dynamic cards with time, name/label, recurrence chips (S, M, T, W, T, F, S), and audio badge (`🎵 <name>`).
   - Material 3 `Switch` toggles active/inactive state inline.
   - Extended FAB displays active count (`X / 5 alarms`). When 5 alarms exist, the FAB disables with error styling and prevents further additions.
2. **Alarm Editor (Modal BottomSheet)**:
   - Houses Material 3 `TimePicker` (hour/minute), label `OutlinedTextField`, day-of-week recurrence filter chips, and SAF file picker action.
3. **Full-Screen Ringing Screen (`RingingActivity`)**:
   - Immersive dark screen waking the device over the lock screen.
   - Visual Gentle Wake meter reflecting real-time 30s linear volume ramp (20% -> 100%).
   - Prominent high-contrast **Dismiss** button and secondary **Snooze** button with dynamic remaining time.
4. **State Seams**:
   - Clean Architecture separation between `AlarmListUiState`, `AlarmEditorUiState`, `RingingUiState`, and underlying Room entities.

