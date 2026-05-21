package com.chessdemo.ui

import com.chessdemo.domain.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ClockState(
    val whiteTimeMs: Long = 10 * 60 * 1000L,
    val blackTimeMs: Long = 10 * 60 * 1000L,
    val incrementMs: Long = 5000L,
    val whiteActive: Boolean = false,
    val blackActive: Boolean = false,
    val running: Boolean = false,
)

class ClockEngineManager {

    private val _clockState = MutableStateFlow(ClockState())
    val clockState: StateFlow<ClockState> = _clockState.asStateFlow()

    private var clockJob: Job? = null

    fun startClockIfNeeded(scope: kotlinx.coroutines.CoroutineScope) {
        clockJob?.cancel()
        clockJob = scope.launch {
            while (isActive) {
                delay(250L)
                val clock = _clockState.value
                if (!clock.running) continue
                var newWhite = clock.whiteTimeMs
                var newBlack = clock.blackTimeMs
                if (clock.whiteActive) newWhite = maxOf(0L, newWhite - 250L)
                if (clock.blackActive) newBlack = maxOf(0L, newBlack - 250L)
                if (newWhite != clock.whiteTimeMs || newBlack != clock.blackTimeMs) {
                    _clockState.value = clock.copy(whiteTimeMs = newWhite, blackTimeMs = newBlack)
                }
            }
        }
    }

    fun updateClockActivation(active: Boolean, turn: Color) {
        _clockState.value = _clockState.value.copy(
            whiteActive = active && (turn == Color.WHITE),
            blackActive = active && (turn == Color.BLACK),
            running = active,
        )
    }

    fun applyClockSettings(minutes: Long, increment: Long) {
        _clockState.value = _clockState.value.copy(
            whiteTimeMs = minutes * 60 * 1000L,
            blackTimeMs = minutes * 60 * 1000L,
            incrementMs = increment * 1000L,
        )
    }

    fun applyIncrement(turn: Color) {
        val clock = _clockState.value
        _clockState.value = if (turn == Color.WHITE) {
            clock.copy(whiteTimeMs = clock.whiteTimeMs + clock.incrementMs)
        } else {
            clock.copy(blackTimeMs = clock.blackTimeMs + clock.incrementMs)
        }
    }

    fun reset() {
        clockJob?.cancel()
        _clockState.value = ClockState()
    }

    fun cleanup() {
        clockJob?.cancel()
    }
}
