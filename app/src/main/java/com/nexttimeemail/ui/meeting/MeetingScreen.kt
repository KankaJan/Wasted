package com.nexttimeemail.ui.meeting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexttimeemail.R
import com.nexttimeemail.domain.CostCalculator
import com.nexttimeemail.ui.AppViewModelProvider
import com.nexttimeemail.ui.theme.MoneyCounterStyle
import com.nexttimeemail.ui.theme.TimerStyle
import com.nexttimeemail.util.buzz
import com.nexttimeemail.util.formatDate
import com.nexttimeemail.util.formatElapsed
import com.nexttimeemail.util.sendMeetingEmail
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetingScreen(
    onFinished: () -> Unit,
    viewModel: MeetingViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val locale = Locale.getDefault()

    LaunchedEffect(Unit) { viewModel.startIfNeeded() }

    // Buzz whenever the cost crosses a new reminder threshold step.
    LaunchedEffect(Unit) {
        viewModel.buzz.collect { buzz(context) }
    }

    val titleRes = if (state.phase == MeetingPhase.RUNNING) {
        R.string.meeting_in_progress
    } else {
        R.string.result_title
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(titleRes)) }) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.attendees_count, state.attendeeCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.reminderEnabled) {
                Text(
                    text = stringResource(R.string.reminder_active, formatThreshold(state.reminderThreshold)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Text(
                text = CostCalculator.formatTotals(state.costByCurrency, locale),
                style = MoneyCounterStyle,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 24.dp),
            )

            Text(
                text = stringResource(
                    if (state.phase == MeetingPhase.RUNNING) R.string.elapsed else R.string.duration,
                ),
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = formatElapsed(state.elapsedMillis),
                style = TimerStyle,
                modifier = Modifier.padding(top = 4.dp, bottom = 40.dp),
            )

            if (state.phase == MeetingPhase.RUNNING) {
                RunningControls(
                    running = state.running,
                    onTogglePause = viewModel::togglePause,
                    onEnd = { viewModel.endMeeting(locale) },
                )
            } else {
                EndedControls(
                    onEmail = {
                        sendMeetingEmail(
                            context = context,
                            recipients = state.recipients,
                            dateLabel = formatDate(state.startedAt, locale),
                            costSummary = state.costSummary(locale),
                        )
                    },
                    onDone = onFinished,
                )
            }
        }
    }
}

/** Drops a trailing ".0" so a whole-number threshold reads as "100", not "100.0". */
private fun formatThreshold(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

@Composable
private fun RunningControls(
    running: Boolean,
    onTogglePause: () -> Unit,
    onEnd: () -> Unit,
) {
    OutlinedButton(onClick = onTogglePause, modifier = Modifier.fillMaxWidth()) {
        Icon(
            imageVector = if (running) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = null,
        )
        Text(
            text = stringResource(if (running) R.string.pause else R.string.resume),
            modifier = Modifier.padding(start = 8.dp),
        )
    }
    Button(
        onClick = onEnd,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
    ) {
        Icon(Icons.Default.Stop, contentDescription = null)
        Text(stringResource(R.string.end_meeting), modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun EndedControls(
    onEmail: () -> Unit,
    onDone: () -> Unit,
) {
    Button(onClick = onEmail, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.Email, contentDescription = null)
        Text(stringResource(R.string.send_email), modifier = Modifier.padding(start = 8.dp))
    }
    OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(stringResource(R.string.done))
    }
}
