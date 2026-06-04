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
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexttimeemail.R
import com.nexttimeemail.data.Attendee
import com.nexttimeemail.data.RateType
import com.nexttimeemail.domain.CostCalculator
import com.nexttimeemail.service.MeetingService
import com.nexttimeemail.ui.AppViewModelProvider
import com.nexttimeemail.ui.meeting.MeetingEngine
import com.nexttimeemail.ui.meeting.MeetingParams
import java.util.Locale
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
    val currencyCode by viewModel.currencyCode.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Local text mirror of the persisted threshold; 0 (or blank) means "off".
    var reminderText by remember {
        mutableStateOf(if (reminderThreshold > 0) trimAmount(reminderThreshold) else "")
    }
    // Local text mirror of the persisted currency code.
    var currencyText by remember { mutableStateOf(currencyCode) }

    // null = closed; non-null Optional-ish: we use a sentinel for "add" vs "edit".
    var editing by remember { mutableStateOf<EditTarget?>(null) }
    val needAttendeeMsg = stringResource(R.string.need_one_attendee)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.roster_title)) },
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.AutoMirrored.Outlined.List, contentDescription = stringResource(R.string.history))
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
                        AttendeeRow(
                            attendee = attendee,
                            currencyCode = currencyCode,
                            onClick = { editing = EditTarget.Existing(attendee) },
                        )
                    }
                }
            }

            StartBar(
                perHourTotal = state.perHourTotal,
                currencyCode = currencyCode,
                canStart = state.canStart,
                currencyText = currencyText,
                onCurrencyChange = { input ->
                    currencyText = input.take(8).uppercase(Locale.ROOT)
                    viewModel.setCurrencyCode(currencyText)
                },
                reminderText = reminderText,
                onReminderChange = { input ->
                    reminderText = input.filterAmount()
                    viewModel.setReminderThreshold(
                        reminderText.replace(',', '.').toDoubleOrNull() ?: 0.0,
                    )
                },
                onStart = {
                    if (state.canStart) {
                        MeetingEngine.start(
                            MeetingParams(
                                attendeeCount = state.attendees.size,
                                perHourTotal = state.perHourTotal,
                                currencyCode = currencyCode,
                                recipients = state.attendees.mapNotNull {
                                    it.email?.trim()?.takeIf(String::isNotEmpty)
                                },
                                reminderThreshold = reminderThreshold,
                            ),
                        )
                        MeetingService.start(context)
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
private fun AttendeeRow(attendee: Attendee, currencyCode: String, onClick: () -> Unit) {
    OutlinedCard(
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
            Text(rateLabel(attendee, currencyCode), style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun rateLabel(attendee: Attendee, currencyCode: String): String {
    val amount = CostCalculator.formatMoney(attendee.rateValue, currencyCode)
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
    perHourTotal: Double,
    currencyCode: String,
    canStart: Boolean,
    currencyText: String,
    onCurrencyChange: (String) -> Unit,
    reminderText: String,
    onReminderChange: (String) -> Unit,
    onStart: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = stringResource(R.string.burn_rate),
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = stringResource(
                    R.string.burn_rate_per_hour,
                    CostCalculator.formatMoney(perHourTotal, currencyCode),
                ),
                style = MaterialTheme.typography.headlineSmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = currencyText,
                    onValueChange = onCurrencyChange,
                    label = { Text(stringResource(R.string.currency)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = reminderText,
                    onValueChange = onReminderChange,
                    label = { Text(stringResource(R.string.reminder_label)) },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Outlined.NotificationsNone, contentDescription = null)
                    },
                    suffix = { Text(currencyCode) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = stringResource(R.string.reminder_supporting),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            OutlinedButton(
                onClick = onStart,
                enabled = canStart,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                Text(
                    text = stringResource(R.string.start_meeting),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}
