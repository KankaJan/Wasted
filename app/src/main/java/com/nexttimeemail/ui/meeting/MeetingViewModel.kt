package com.nexttimeemail.ui.meeting

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexttimeemail.data.MeetingRecord
import com.nexttimeemail.data.MeetingRepository
import com.nexttimeemail.domain.CostCalculator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import java.util.Locale

enum class MeetingPhase { RUNNING, ENDED }

data class MeetingUiState(
    val phase: MeetingPhase = MeetingPhase.RUNNING,
    val running: Boolean = true,
    val elapsedMillis: Long = 0,
    val attendeeCount: Int = 0,
    val perHourByCurrency: Map<String, Double> = emptyMap(),
    val recipients: List<String> = emptyList(),
    val startedAt: Long = System.currentTimeMillis(),
) {
    /** Live cost grouped by currency at the current elapsed time. */
    val costByCurrency: Map<String, Double>
        get() = CostCalculator.costAtElapsed(perHourByCurrency, elapsedMillis)

    fun costSummary(locale: Locale = Locale.getDefault()): String =
        CostCalculator.formatTotals(costByCurrency, locale)
}

/**
 * Drives the live meeting: ticks the cost once per second and, on end, writes a
 * history record. Elapsed time is derived from a monotonic clock so it survives
 * pauses and isn't thrown off by drift in the tick loop.
 */
class MeetingViewModel(private val repository: MeetingRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(MeetingUiState())
    val uiState: StateFlow<MeetingUiState> = _uiState.asStateFlow()

    private var accumulatedMillis = 0L
    private var lastResumeUptime = SystemClock.elapsedRealtime()
    private var ticker: Job? = null
    private var loaded = false

    /** Loads the current roster and starts the clock. Safe to call repeatedly. */
    fun startIfNeeded() {
        if (loaded) return
        loaded = true
        viewModelScope.launch {
            val attendees = repository.attendeesSnapshot()
            _uiState.update {
                it.copy(
                    attendeeCount = attendees.size,
                    perHourByCurrency = CostCalculator.perHourByCurrency(attendees),
                    recipients = attendees.mapNotNull { a -> a.email?.trim()?.takeIf(String::isNotEmpty) },
                    startedAt = System.currentTimeMillis(),
                )
            }
            lastResumeUptime = SystemClock.elapsedRealtime()
            startTicker()
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (true) {
                _uiState.update { it.copy(elapsedMillis = currentElapsed()) }
                delay(1_000)
            }
        }
    }

    private fun currentElapsed(): Long =
        accumulatedMillis + (SystemClock.elapsedRealtime() - lastResumeUptime)

    fun togglePause() {
        val state = _uiState.value
        if (state.phase != MeetingPhase.RUNNING) return
        if (state.running) {
            accumulatedMillis = currentElapsed()
            ticker?.cancel()
            _uiState.update { it.copy(running = false, elapsedMillis = accumulatedMillis) }
        } else {
            lastResumeUptime = SystemClock.elapsedRealtime()
            _uiState.update { it.copy(running = true) }
            startTicker()
        }
    }

    /** Stops the clock, freezes the final figures and persists the meeting. */
    fun endMeeting(locale: Locale) {
        if (_uiState.value.phase == MeetingPhase.ENDED) return
        val finalElapsed = currentElapsed()
        ticker?.cancel()
        accumulatedMillis = finalElapsed

        val frozen = _uiState.updateAndGet {
            it.copy(phase = MeetingPhase.ENDED, running = false, elapsedMillis = finalElapsed)
        }

        viewModelScope.launch {
            repository.recordMeeting(
                MeetingRecord(
                    startedAt = frozen.startedAt,
                    durationMillis = finalElapsed,
                    attendeeCount = frozen.attendeeCount,
                    costSummary = frozen.costSummary(locale),
                ),
            )
        }
    }

    override fun onCleared() {
        ticker?.cancel()
        super.onCleared()
    }
}
