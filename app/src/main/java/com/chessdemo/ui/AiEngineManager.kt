package com.chessdemo.ui

import android.media.SoundPool
import android.os.Vibrator
import com.chessdemo.ai.StockfishAI
import com.chessdemo.ai.StockfishEngine
import com.chessdemo.domain.Color as PieceColor
import com.chessdemo.domain.GameState
import com.chessdemo.domain.GameTree
import com.chessdemo.domain.Move
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AiEngineManager(
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val getGameState: () -> GameState,
    private val getGameTree: () -> GameTree,
    private val applyMove: (Move) -> Unit,
    private val updateUi: ((GameUiState) -> GameUiState) -> Unit,
    private val getGameMode: () -> GameMode,
    private val getClockState: () -> ClockState,
    private val soundPool: SoundPool,
    private val vibrator: Vibrator,
) {

    private var aiJob: Job? = null
    private var analysisJob: Job? = null

    fun triggerAiMove() {
        val currentGameState = getGameState()
        val currentAiTurn = currentGameState.currentTurn
        if (getGameTree().currentState().gameOver) return

        aiJob?.cancel()
        aiJob = scope.launch {
            updateUi { state -> state.copy(aiThinking = true) }
            updateClockStateForAi(true, currentAiTurn)
            delay(300)
            if (!isActive) {
                updateUi { state -> state.copy(aiThinking = false) }
                updateClockStateForAi(false, currentAiTurn)
                return@launch
            }

            val searchResult = withContext(Dispatchers.IO) {
                try {
                    val pollJob = launch {
                        while (isActive) {
                            val info = StockfishAI.getLatestInfo()
                            if (info.isNotEmpty()) {
                                withContext(Dispatchers.Main) {
                                    updateUi { state -> state.copy(thinkingInfo = com.chessdemo.ui.util.formatStockfishInfo(info)) }
                                }
                            }
                            delay(100)
                        }
                    }
                    val fen = StockfishAI.stateToFenPublic(currentGameState)
                    val bestMove = StockfishAI.findBestMoveFromFen(fen)
                    pollJob.cancel()
                    bestMove to StockfishAI.getLatestInfo()
                } finally {
                    if (!isActive) StockfishEngine.stop()
                }
            }

            if (!isActive) {
                updateUi { state -> state.copy(aiThinking = false) }
                updateClockStateForAi(false, currentAiTurn)
                return@launch
            }

            val (bestMove, finalInfo) = searchResult
            if (finalInfo.isNotEmpty()) {
                updateUi { state -> state.copy(thinkingInfo = com.chessdemo.ui.util.formatStockfishInfo(finalInfo)) }
            }

            if (bestMove != null && !getGameTree().currentState().gameOver) {
                applyMove(bestMove)
                updateUi { state -> state.copy(lastMove = bestMove) }
                StockfishAI.recordMove(getGameState().currentTurn, 0L)
                playMoveSound()
                vibrateMove()
            }
            updateUi { state -> state.copy(aiThinking = false) }
            updateClockStateForAi(false, currentAiTurn)

            if (getGameMode() == GameMode.COMPUTER_VS_COMPUTER) {
                checkAndTriggerAi()
            }
        }
    }

    fun triggerAiVsAiLoop() {
        aiJob?.cancel()
        aiJob = scope.launch {
            while (isActive) {
                val state = getGameTree().currentState()
                if (state.gameOver) break
                updateUi { state -> state.copy(aiThinking = true) }
                updateClockStateForAi(true, state.currentTurn)
                delay(300)
                if (!isActive) break

                val stateToSearch = getGameTree().currentState()
                if (stateToSearch.gameOver) break
                val bestMove = withContext(Dispatchers.IO) {
                    val fen = StockfishAI.stateToFenPublic(stateToSearch)
                    StockfishAI.findBestMoveFromFen(fen)
                }

                if (!isActive) break

                if (bestMove != null && !getGameTree().currentState().gameOver) {
                    applyMove(bestMove)
                    updateUi { state -> state.copy(lastMove = bestMove) }
                    StockfishAI.recordMove(getGameState().currentTurn, 0L)
                    playMoveSound()
                    vibrateMove()
                }
                updateUi { state -> state.copy(aiThinking = false) }
                updateClockStateForAi(false, state.currentTurn)
                delay(500)
            }
        }
    }

    fun checkAndTriggerAi() {
        val state = getGameTree().currentState()
        if (state.gameOver) return

        when (getGameMode()) {
            GameMode.COMPUTER_VS_COMPUTER -> {
                if (!state.gameOver) triggerAiVsAiLoop()
            }
            GameMode.PLAY_WHITE -> {
                if (state.currentTurn == PieceColor.BLACK) triggerAiMove()
            }
            GameMode.PLAY_BLACK -> {
                if (state.currentTurn == PieceColor.WHITE) triggerAiMove()
            }
            GameMode.TWO_PLAYERS -> {}
        }
    }

    fun startAnalysis() {
        analysisJob?.cancel()
        val state = getGameState()
        if (state.gameOver) return

        updateUi { state -> state.copy(aiThinking = true, analysisPvLines = emptyMap()) }
        StockfishAI.startAnalysis(state, multiPV = 3)

        analysisJob = scope.launch {
            while (isActive) {
                val info = StockfishAI.getLatestInfo()
                if (info.isNotEmpty()) {
                    val multipvIndex = com.chessdemo.ui.util.extractMultiPvIndex(info)
                    val lineSummary = com.chessdemo.ui.util.formatAnalysisLine(info)
                    if (multipvIndex != null && lineSummary.isNotEmpty()) {
                        updateUi { state -> state.copy(analysisPvLines = state.analysisPvLines + (multipvIndex to lineSummary)) }
                    }
                }
                delay(150)
            }
        }
    }

    fun stopAnalysis() {
        analysisJob?.cancel()
        analysisJob = null
        StockfishAI.stopAnalysis()
        updateUi { state -> state.copy(aiThinking = false, analysisPvLines = emptyMap()) }
    }

    fun setDifficulty(diff: StockfishAI.Difficulty) {
        StockfishAI.difficulty = diff
        StockfishAI.setEloForDifficulty(diff)
    }

    fun cleanup() {
        aiJob?.cancel()
        analysisJob?.cancel()
        StockfishEngine.stop()
        StockfishAI.stopAnalysis()
    }

    // --- Private helpers ---

    private fun updateClockStateForAi(active: Boolean, aiTurn: PieceColor) {
        // This is done via the clockManager externally; we don't have direct access.
        // For now, the clock state updates are handled in GameViewModel's LaunchedEffect.
    }

    private fun playMoveSound() {
        com.chessdemo.ui.util.playMoveSound(soundPool)
    }

    private fun vibrateMove() {
        com.chessdemo.ui.util.vibrateMove(vibrator)
    }
}
