package com.nexttimeemail.ui.roster

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexttimeemail.data.Attendee
import com.nexttimeemail.data.MeetingRepository
import com.nexttimeemail.domain.CostCalculator
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RosterUiState(
    val attendees: List<Attendee> = emptyList(),
    val perHourByCurrency: Map<String, Double> = emptyMap(),
) {
    val canStart: Boolean get() = perHourByCurrency.isNotEmpty()
}

class RosterViewModel(private val repository: MeetingRepository) : ViewModel() {

    val uiState: StateFlow<RosterUiState> = repository.attendees
        .map { attendees ->
            RosterUiState(
                attendees = attendees,
                perHourByCurrency = CostCalculator.perHourByCurrency(attendees),
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
}
