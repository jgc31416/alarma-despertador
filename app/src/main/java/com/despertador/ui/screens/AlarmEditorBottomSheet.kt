package com.despertador.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.despertador.R
import com.despertador.data.audio.AudioPickerContract
import com.despertador.domain.model.Alarm
import com.despertador.domain.model.DayOfWeek

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditorBottomSheet(
    alarmToEdit: Alarm?,
    onDismiss: () -> Unit,
    onSave: (Alarm) -> Unit,
    onDelete: ((Alarm) -> Unit)? = null,
    onSelectAudioUri: (Uri, onResult: (cachedPath: String, fileName: String, hash: String) -> Unit) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val initialAlarm = remember(alarmToEdit) {
        alarmToEdit ?: Alarm(hour = 7, minute = 0)
    }

    val timePickerState = rememberTimePickerState(
        initialHour = initialAlarm.hour,
        initialMinute = initialAlarm.minute,
        is24Hour = true
    )

    var label by remember { mutableStateOf(initialAlarm.label) }
    var selectedDays by remember { mutableStateOf(initialAlarm.daysOfWeek) }
    var cachedAudioPath by remember { mutableStateOf(initialAlarm.cachedAudioPath) }
    var audioFileName by remember { mutableStateOf(initialAlarm.audioFileName) }
    var sha256Hash by remember { mutableStateOf(initialAlarm.sha256Hash) }

    val audioLauncher = rememberLauncherForActivityResult(
        contract = AudioPickerContract()
    ) { uri: Uri? ->
        if (uri != null) {
            onSelectAudioUri(uri) { path, fileName, hash ->
                cachedAudioPath = path
                audioFileName = fileName
                sha256Hash = hash
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(
                        if (alarmToEdit != null) R.string.edit_alarm_title else R.string.new_alarm_title
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                if (alarmToEdit != null && onDelete != null) {
                    IconButton(onClick = {
                        onDelete(alarmToEdit)
                        onDismiss()
                    }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete_alarm),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Time Picker
            TimePicker(state = timePickerState)

            Spacer(modifier = Modifier.height(16.dp))

            // Alarm Name Field
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text(stringResource(R.string.alarm_label_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Day Recurrence Chips
            Text(
                text = stringResource(R.string.repeat_days),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DayOfWeek.entries.forEach { day ->
                    val isSelected = day in selectedDays
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedDays = if (isSelected) {
                                selectedDays - day
                            } else {
                                selectedDays + day
                            }
                        },
                        label = { Text(day.shortName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Audio Selector
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { audioLauncher.launch(AudioPickerContract.AUDIO_MIME_TYPES) }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Audiotrack,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.size(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (audioFileName.isNotBlank()) audioFileName else stringResource(R.string.no_audio_selected),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.select_audio),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.cancel))
                }

                Button(
                    onClick = {
                        val updatedAlarm = initialAlarm.copy(
                            hour = timePickerState.hour,
                            minute = timePickerState.minute,
                            label = label.trim(),
                            daysOfWeek = selectedDays,
                            cachedAudioPath = cachedAudioPath,
                            audioFileName = audioFileName,
                            sha256Hash = sha256Hash
                        )
                        onSave(updatedAlarm)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.save_alarm))
                }
            }
        }
    }
}
