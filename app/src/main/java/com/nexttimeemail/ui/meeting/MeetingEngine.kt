package com.nexttimeemail.ui.meeting

import android.os.SystemClock
import com.nexttimeemail.data.MeetingRecord
import com.nexttimeemail.data.MeetingRepository
import com.nexttimeemail.domain.CostCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    val perHourTotal: Double = 0.0,
    val currencyCode: String = "USD",
    val recipients: List<String> = emptyList(),
    val startedAt: Long = System.currentTimeMillis(),
    val reminderThreshold: Double = 0.0,
) {
    val reminderEnabled: Boolean get() = reminderThreshold > 0.0

    /** Live cost at the current elapsed time. */
    val cost: Double
        get() = CostCalculator.costAtElapsed(perHourTotal, elapsedMillis)

    fun costSummary(locale: Locale = Locale.getDefault()): String =
        CostCalculator.formatMoney(cost, currencyCode, locale)
}

/** Immutable description of a meeting to start, assembled from the roster. */
data class MeetingParams(
    val attendeeCount: Int,
    val perHourTotal: Double,
    val currencyCode: String,
    val recipients: List<String>,
    val reminderThreshold: Double,
)

/**
 * Process-wide owner of the running meeting: the 1 s cost ticker, the public
 * state, the reminder-buzz events, and history persistence on end.
 *
 * It lives outside any ViewModel or Activity so the meeting keeps running (and
 * the foreground-service notification keeps updating) while the app is in the
 * background. Elapsed time is derived from a monotonic clock, so it is immune to
 * tick drift and survives pauses.
 */
object MeetingEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(MeetingUiState())
    val state: StateFlow<MeetingUiState> = _state.asStateFlow()

    /** Emits once each time the cost crosses a new reminder threshold step. */
    private val _buzz = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val buzz: SharedFlow<Unit> = _buzz.asSharedFlow()

    private var repository: MeetingRepository? = null

    private var accumulatedMillis = 0L
    private var lastResumeUptime = 0L
    private var ticker: Job? = null
    private var lastBuzzStep = 0

    /** True while a meeting is running (i.e. not ended). */
    val isActive: Boolean get() = _state.value.phase == MeetingPhase.RUNNING && ticker != null

    /** Wire up persistence once, from the Application. */
    fun init(repository: MeetingRepository) {
        this.repository = repository
    }

    /** Begin a brand-new meeting, replacing any previous session. */
    fun start(params: MeetingParams) {
        ticker?.cancel()
        accumulatedMillis = 0L
        lastBuzzStep = 0
        lastResumeUptime = SystemClock.elapsedRealtime()
        _state.value = MeetingUiState(
            phase = MeetingPhase.RUNNING,
            running = true,
            elapsedMillis = 0,
            attendeeCount = params.attendeeCount,
            perHourTotal = params.perHourTotal,
            currencyCode = params.currencyCode,
            recipients = params.recipients,
            startedAt = System.currentTimeMillis(),
            reminderThreshold = params.reminderThreshold,
        )
        startTicker()
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (true) {
                val updated = _state.updateAndGet { it.copy(elapsedMillis = currentElapsed()) }
                maybeBuzz(updated)
                delay(1_000)
            }
        }
    }

    private fun maybeBuzz(state: MeetingUiState) {
        if (!state.reminderEnabled) return
        val step = CostCalculator.reminderStep(state.cost, state.reminderThreshold)
        if (step > lastBuzzStep) {
            lastBuzzStep = step
            _buzz.tryEmit(Unit)
        }
    }

    private fun currentElapsed(): Long =
        accumulatedMillis + (SystemClock.elapsedRealtime() - lastResumeUptime)

    fun togglePause() {
        val state = _state.value
        if (state.phase != MeetingPhase.RUNNING) return
        if (state.running) {
            accumulatedMillis = currentElapsed()
            ticker?.cancel()
            ticker = null
            _state.update { it.copy(running = false, elapsedMillis = accumulatedMillis) }
        } else {
            lastResumeUptime = SystemClock.elapsedRealtime()
            _state.update { it.copy(running = true) }
            startTicker()
        }
    }

    /** Stop the clock, freeze the final figures and persist the meeting. */
    fun end(locale: Locale = Locale.getDefault()) {
        if (_state.value.phase == MeetingPhase.ENDED) return
        val finalElapsed = currentElapsed()
        ticker?.cancel()
        ticker = null
        accumulatedMillis = finalElapsed

        val frozen = _state.updateAndGet {
            it.copy(phase = MeetingPhase.ENDED, running = false, elapsedMillis = finalElapsed)
        }

        val repo = repository ?: return
        scope.launch {
            repo.recordMeeting(
                MeetingRecord(
                    startedAt = frozen.startedAt,
                    durationMillis = finalElapsed,
                    attendeeCount = frozen.attendeeCount,
                    costSummary = frozen.costSummary(locale),
                ),
            )
        }
    }
}
