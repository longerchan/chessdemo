package com.jnz.chess.ui

import android.media.SoundPool
import android.os.Vibrator
import com.jnz.chess.ai.StockfishAI
import com.jnz.chess.ai.StockfishEngine
import com.jnz.chess.domain.Color as PieceColor
import com.jnz.chess.domain.GameState
import com.jnz.chess.domain.GameTree
import com.jnz.chess.domain.Move
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
    private val applyClockIncrement: (PieceColor) -> Unit = {},
) {

    private var aiJob: Job? = null
    private var analysisJob: Job? = null

    fun triggerAiMove() {
        if (getGameTree().currentState().gameOver) return

        aiJob?.cancel()
        aiJob = scope.launch {
            updateUi { state -> state.copy(aiThinking = true) }
            val stateToSearch = getGameTree().currentState()
            if (stateToSearch.gameOver || !isActive) {
                updateUi { state -> state.copy(aiThinking = false) }
                return@launch
            }
            val expectedTurn = stateToSearch.currentTurn

            val success = executeAiSearch(stateToSearch, showThinking = true)
            if (!isActive || getGameTree().currentState().currentTurn != expectedTurn) {
                updateUi { state -> state.copy(aiThinking = false) }
                return@launch
            }
            updateUi { state -> state.copy(aiThinking = false) }

            if (success && getGameMode() == GameMode.COMPUTER_VS_COMPUTER) {
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
                if (!isActive) break

                val stateToSearch = getGameTree().currentState()
                if (stateToSearch.gameOver) break
                executeAiSearch(stateToSearch, showThinking = false)
                if (!isActive) break
                updateUi { state -> state.copy(aiThinking = false) }
            }
        }
    }

    /** Search Stockfish and apply the resulting move. Returns true if a move was played. */
    private suspend fun kotlinx.coroutines.CoroutineScope.executeAiSearch(stateToSearch: com.jnz.chess.domain.GameState, showThinking: Boolean): Boolean {
        val movingColor = stateToSearch.currentTurn
        val searchStartTime = System.currentTimeMillis()

        val bestMove = withContext(Dispatchers.IO) {
            try {
                val pollJob = if (showThinking) {
                    launch {
                        while (isActive) {
                            val info = StockfishAI.getLatestInfo()
                            if (info.isNotEmpty()) {
                                withContext(Dispatchers.Main) {
                                    updateUi { state -> state.copy(thinkingInfo = com.jnz.chess.ui.util.formatStockfishInfo(info)) }
                                }
                            }
                            delay(100)
                        }
                    }
                } else null
                val fen = StockfishAI.stateToFenPublic(stateToSearch)
                val move = StockfishAI.findBestMoveFromFen(fen)
                pollJob?.cancel()
                if (showThinking) {
                    val finalInfo = StockfishAI.getLatestInfo()
                    if (finalInfo.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            updateUi { state -> state.copy(thinkingInfo = com.jnz.chess.ui.util.formatStockfishInfo(finalInfo)) }
                        }
                    }
                }
                move
            } finally {
                if (!isActive) StockfishEngine.stop()
            }
        }

        if (!isActive) return false

        val elapsedMs = System.currentTimeMillis() - searchStartTime
        if (bestMove != null && !getGameTree().currentState().gameOver) {
            applyMove(bestMove)
            updateUi { state -> state.copy(lastMove = bestMove) }
            applyClockIncrement(movingColor)
            StockfishAI.recordMove(movingColor, elapsedMs)
            playMoveSound()
            vibrateMove()
            return true
        }
        return false
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
                    val multipvIndex = com.jnz.chess.ui.util.extractMultiPvIndex(info)
                    val lineSummary = com.jnz.chess.ui.util.formatAnalysisLine(info)
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

    private fun playMoveSound() {
        com.jnz.chess.ui.util.playMoveSound()
    }

    private fun vibrateMove() {
        com.jnz.chess.ui.util.vibrateMove(vibrator)
    }
}
