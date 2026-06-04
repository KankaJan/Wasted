package com.nexttimeemail.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexttimeemail.data.MeetingRecord
import com.nexttimeemail.data.MeetingRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(private val repository: MeetingRepository) : ViewModel() {

    val meetings: StateFlow<List<MeetingRecord>> = repository.meetings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun clear() = viewModelScope.launch { repository.clearHistory() }
}
