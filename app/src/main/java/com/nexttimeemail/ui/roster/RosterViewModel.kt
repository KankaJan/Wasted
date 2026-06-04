package com.nexttimeemail.ui.roster

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexttimeemail.data.Attendee
import com.nexttimeemail.data.MeetingRepository
import com.nexttimeemail.data.SettingsStore
import com.nexttimeemail.domain.CostCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RosterUiState(
    val attendees: List<Attendee> = emptyList(),
    val perHourTotal: Double = 0.0,
) {
    val canStart: Boolean get() = perHourTotal > 0.0
}

class RosterViewModel(
    private val repository: MeetingRepository,
    private val settings: SettingsStore,
) : ViewModel() {

    /** Cost step at which the meeting buzzes; 0 means the reminder is off. */
    private val _reminderThreshold = MutableStateFlow(settings.reminderThreshold)
    val reminderThreshold: StateFlow<Double> = _reminderThreshold.asStateFlow()

    /** Currency code shared by every attendee. */
    private val _currencyCode = MutableStateFlow(settings.currencyCode)
    val currencyCode: StateFlow<String> = _currencyCode.asStateFlow()

    val uiState: StateFlow<RosterUiState> = repository.attendees
        .map { attendees ->
            RosterUiState(
                attendees = attendees,
                perHourTotal = CostCalculator.perHourTotal(attendees),
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RosterUiState(),
        )

    fun save(attendee: Attendee) = viewModelScope.launch {
        repository.upsertAttendee(attendee)
    }

    fun delete(attendee: Attendee) = viewModelScope.launch {
        repository.deleteAttendee(attendee)
    }

    fun setReminderThreshold(value: Double) {
        val sanitized = value.coerceAtLeast(0.0)
        settings.reminderThreshold = sanitized
        _reminderThreshold.value = sanitized
    }

    fun setCurrencyCode(value: String) {
        settings.currencyCode = value
        _currencyCode.value = settings.currencyCode
    }
}
