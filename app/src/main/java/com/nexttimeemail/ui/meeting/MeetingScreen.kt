package com.nexttimeemail.ui.meeting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexttimeemail.R
import com.nexttimeemail.domain.CostCalculator
import com.nexttimeemail.ui.theme.MoneyCounterStyle
import com.nexttimeemail.ui.theme.TimerStyle
import com.nexttimeemail.util.formatDate
import com.nexttimeemail.util.formatElapsed
import com.nexttimeemail.util.sendMeetingEmail
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetingScreen(onFinished: () -> Unit) {
    val state by MeetingEngine.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val locale = Locale.getDefault()

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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.NotificationsNone,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(
                            R.string.reminder_active,
                            CostCalculator.formatMoney(state.reminderThreshold, state.currencyCode, locale),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }

            Text(
                text = CostCalculator.formatMoney(state.cost, state.currencyCode, locale),
                style = MoneyCounterStyle,
                color = MaterialTheme.colorScheme.onBackground,
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
                    onTogglePause = { MeetingEngine.togglePause() },
                    onEnd = { MeetingEngine.end(locale) },
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

@Composable
private fun RunningControls(
    running: Boolean,
    onTogglePause: () -> Unit,
    onEnd: () -> Unit,
) {
    OutlinedButton(onClick = onTogglePause, modifier = Modifier.fillMaxWidth()) {
        Icon(
            imageVector = if (running) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
            contentDescription = null,
        )
        Text(
            text = stringResource(if (running) R.string.pause else R.string.resume),
            modifier = Modifier.padding(start = 8.dp),
        )
    }
    OutlinedButton(
        onClick = onEnd,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        Icon(Icons.Outlined.Stop, contentDescription = null)
        Text(stringResource(R.string.end_meeting), modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun EndedControls(
    onEmail: () -> Unit,
    onDone: () -> Unit,
) {
    OutlinedButton(onClick = onEmail, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Outlined.Email, contentDescription = null)
        Text(stringResource(R.string.send_email), modifier = Modifier.padding(start = 8.dp))
    }
    OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(stringResource(R.string.done))
    }
}
