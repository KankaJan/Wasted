package com.nexttimeemail.ui.roster

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexttimeemail.R
import com.nexttimeemail.data.Attendee
import com.nexttimeemail.data.RateType
import com.nexttimeemail.domain.CostCalculator
import com.nexttimeemail.ui.AppViewModelProvider
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RosterScreen(
    onStartMeeting: () -> Unit,
    onOpenHistory: () -> Unit,
    viewModel: RosterViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val reminderThreshold by viewModel.reminderThreshold.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Local text mirror of the persisted threshold; 0 (or blank) means "off".
    var reminderText by remember {
        mutableStateOf(if (reminderThreshold > 0) trimAmount(reminderThreshold) else "")
    }

    // null = closed; non-null Optional-ish: we use a sentinel for "add" vs "edit".
    var editing by remember { mutableStateOf<EditTarget?>(null) }
    val needAttendeeMsg = stringResource(R.string.need_one_attendee)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.roster_title)) },
                actions = {
                    androidx.compose.material3.IconButton(onClick = onOpenHistory) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.history))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = EditTarget.New }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_attendee))
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.attendees.isEmpty()) {
                EmptyRoster(modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.attendees, key = { it.id }) { attendee ->
                        AttendeeRow(attendee = attendee, onClick = { editing = EditTarget.Existing(attendee) })
                    }
                }
            }

            StartBar(
                perHourByCurrency = state.perHourByCurrency,
                canStart = state.canStart,
                reminderText = reminderText,
                onReminderChange = { input ->
                    reminderText = input.filterAmount()
                    viewModel.setReminderThreshold(
                        reminderText.replace(',', '.').toDoubleOrNull() ?: 0.0,
                    )
                },
                onStart = {
                    if (state.canStart) {
                        onStartMeeting()
                    } else {
                        scope.launch { snackbarHostState.showSnackbar(needAttendeeMsg) }
                    }
                },
            )
        }
    }

    when (val target = editing) {
        EditTarget.New -> AttendeeEditDialog(
            initial = null,
            onDismiss = { editing = null },
            onSave = { viewModel.save(it); editing = null },
        )
        is EditTarget.Existing -> AttendeeEditDialog(
            initial = target.attendee,
            onDismiss = { editing = null },
            onSave = { viewModel.save(it); editing = null },
            onDelete = { viewModel.delete(it); editing = null },
        )
        null -> Unit
    }
}

private sealed interface EditTarget {
    data object New : EditTarget
    data class Existing(val attendee: Attendee) : EditTarget
}

@Composable
private fun AttendeeRow(attendee: Attendee, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(attendee.name, style = MaterialTheme.typography.titleMedium)
                if (!attendee.email.isNullOrBlank()) {
                    Text(
                        attendee.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(rateLabel(attendee), style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun rateLabel(attendee: Attendee): String {
    val amount = CostCalculator.formatMoney(attendee.rateValue, attendee.currencyCode)
    val unit = stringResource(
        if (attendee.rateType == RateType.HOURLY) R.string.rate_hourly else R.string.rate_manday,
    )
    return "$amount / $unit"
}

@Composable
private fun EmptyRoster(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.roster_empty),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StartBar(
    perHourByCurrency: Map<String, Double>,
    canStart: Boolean,
    reminderText: String,
    onReminderChange: (String) -> Unit,
    onStart: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = stringResource(R.string.burn_rate),
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = stringResource(
                    R.string.burn_rate_per_hour,
                    CostCalculator.formatTotals(perHourByCurrency),
                ),
                style = MaterialTheme.typography.headlineSmall,
            )
            OutlinedTextField(
                value = reminderText,
                onValueChange = onReminderChange,
                label = { Text(stringResource(R.string.reminder_label)) },
                supportingText = { Text(stringResource(R.string.reminder_supporting)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            Button(
                onClick = onStart,
                enabled = canStart,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                Text(stringResource(R.string.start_meeting))
            }
        }
    }
}
