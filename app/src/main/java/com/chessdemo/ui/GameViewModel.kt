package com.chessdemo.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chessdemo.ai.StockfishAI
import com.chessdemo.ai.StockfishEngine
import com.chessdemo.domain.*
import com.chessdemo.domain.Color as PieceColor
import com.chessdemo.ui.util.createSoundPool
import com.chessdemo.ui.util.generatePgn
import com.chessdemo.ui.util.parsePgn
import com.chessdemo.ui.util.playMoveSound
import com.chessdemo.ui.util.vibrateMove
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun canUserPlay(gameMode: GameMode, currentTurn: PieceColor): Boolean {
    return when (gameMode) {
        GameMode.PLAY_WHITE -> currentTurn == PieceColor.WHITE
        GameMode.PLAY_BLACK -> currentTurn == PieceColor.BLACK
        GameMode.TWO_PLAYERS -> true
        GameMode.COMPUTER_VS_COMPUTER -> false
    }
}

data class ClockState(
    val whiteTimeMs: Long = 10 * 60 * 1000L,
    val blackTimeMs: Long = 10 * 60 * 1000L,
    val incrementMs: Long = 5000L,
    val whiteActive: Boolean = false,
    val blackActive: Boolean = false,
    val running: Boolean = false,
)

data class GameUiState(
    val selectedSquare: Pair<Int, Int>? = null,
    val legalMoves: List<Move> = emptyList(),
    val aiThinking: Boolean = false,
    val thinkingInfo: String = "",
    val analysisPvLines: Map<Int, String> = emptyMap(),
    val lastMove: Move? = null,
    val boardFlipped: Boolean = false,
    val showDifficultyDialog: Boolean = false,
    val showTimeControlDialog: Boolean = false,
    val showModeDialog: Boolean = false,
    val showFenImportDialog: Boolean = false,
    val showPgnImportDialog: Boolean = false,
    val showBoardEditor: Boolean = false,
)

class GameViewModel(application: Application) : AndroidViewModel(application) {

    // Core game state
    private val _gameTree = MutableStateFlow(GameTree.fromInitial())
    val gameTree: StateFlow<GameTree> = _gameTree.asStateFlow()

    // UI state
    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    // Clock state
    private val _clockState = MutableStateFlow(ClockState())
    val clockState: StateFlow<ClockState> = _clockState.asStateFlow()

    // Mode state
    private val _gameMode = MutableStateFlow(GameMode.PLAY_WHITE)
    val gameMode: StateFlow<GameMode> = _gameMode.asStateFlow()

    private val _navMode = MutableStateFlow(NavMode.PLAYING)
    val navMode: StateFlow<NavMode> = _navMode.asStateFlow()

    private var clockJob: Job? = null
    private var aiJob: Job? = null
    private var analysisJob: Job? = null

    private val app: Context get() = getApplication()
    private val soundPool by lazy { createSoundPool() }
    private val vibrator by lazy { app.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator }

    fun setGameMode(mode: GameMode) {
        _gameMode.value = mode
    }

    fun setNavMode(mode: NavMode) {
        _navMode.value = mode
    }

    fun toggleNavMode() {
        _navMode.value = when (_navMode.value) {
            NavMode.PLAYING -> NavMode.ANALYSIS
            NavMode.ANALYSIS -> NavMode.PLAYING
            else -> NavMode.PLAYING
        }
    }

    fun flipBoard() {
        _uiState.value = _uiState.value.copy(boardFlipped = !_uiState.value.boardFlipped)
    }

    fun reset() {
        aiJob?.cancel()
        analysisJob?.cancel()
        StockfishEngine.stop()
        _gameTree.value = GameTree.fromInitial()
        _uiState.value = GameUiState(boardFlipped = _uiState.value.boardFlipped)
        _clockState.value = ClockState()
        _gameMode.value = GameMode.PLAY_WHITE
        _navMode.value = NavMode.PLAYING
        clockJob?.cancel()
        StockfishAI.resetClocks()
        startClockIfNeeded()
    }

    fun goBack() {
        aiJob?.cancel()
        if (_uiState.value.aiThinking) StockfishEngine.stop()
        val tree = _gameTree.value
        _gameTree.value = tree.goBack()
        _uiState.value = _uiState.value.copy(
            selectedSquare = null,
            legalMoves = emptyList(),
            lastMove = run {
                val moves = tree.goBack().moveList()
                moves.lastOrNull()
            },
        )
        checkAndTriggerAi()
    }

    fun goForward() {
        val tree = _gameTree.value
        _gameTree.value = tree.goForward()
        _uiState.value = _uiState.value.copy(
            selectedSquare = null,
            legalMoves = emptyList(),
            lastMove = tree.goForward().moveList().lastOrNull(),
        )
        checkAndTriggerAi()
    }

    fun goToMove(index: Int) {
        aiJob?.cancel()
        analysisJob?.cancel()
        if (_uiState.value.aiThinking) StockfishEngine.stop()
        val tree = _gameTree.value
        _gameTree.value = tree.goTo(index)
        _uiState.value = _uiState.value.copy(
            selectedSquare = null,
            legalMoves = emptyList(),
            lastMove = tree.moveList().getOrNull(index - 1),
        )
    }

    fun triggerComputerVsComputer() {
        if (!_gameTree.value.currentState().gameOver) {
            triggerAiVsAiLoop()
        }
    }

    fun updateClockActivation(active: Boolean, turn: PieceColor) {
        _clockState.value = _clockState.value.copy(
            whiteActive = active && (turn == PieceColor.WHITE),
            blackActive = active && (turn == PieceColor.BLACK),
            running = active,
        )
    }

    fun onSquareClick(row: Int, col: Int) {
        val nav = _navMode.value
        if (nav != NavMode.PLAYING) {
            handleNavigationSquareClick(row, col)
            return
        }

        val state = _gameTree.value.currentState()
        if (_uiState.value.aiThinking || state.gameOver) return
        if (!canUserPlay(_gameMode.value, state.currentTurn)) return

        val current = _uiState.value
        val selected = current.selectedSquare
        val legalMoves = current.legalMoves

        // Check if this is a legal move target
        val existingSelected = selected
        if (existingSelected != null) {
            val targetMove = legalMoves.find { it.toRow == row && it.toCol == col }
            if (targetMove != null) {
                executeMove(targetMove)
                return
            }
        }

        // Select/deselect piece
        val piece = state.board[row][col]
        if (piece != null && piece.color == state.currentTurn) {
            val moves = ChessEngine.getLegalMoves(state, state.currentTurn)
                .filter { it.fromRow == row && it.fromCol == col }
            _uiState.value = current.copy(
                selectedSquare = row to col,
                legalMoves = moves,
            )
        } else {
            _uiState.value = current.copy(
                selectedSquare = null,
                legalMoves = emptyList(),
            )
        }
    }

    fun applyClockSettings(minutes: Long, increment: Long) {
        _clockState.value = _clockState.value.copy(
            whiteTimeMs = minutes * 60 * 1000L,
            blackTimeMs = minutes * 60 * 1000L,
            incrementMs = increment * 1000L,
        )
        StockfishAI.setClocks(
            _clockState.value.whiteTimeMs,
            _clockState.value.blackTimeMs,
            _clockState.value.incrementMs,
        )
    }

    fun setDifficulty(diff: StockfishAI.Difficulty) {
        StockfishAI.difficulty = diff
        StockfishAI.setEloForDifficulty(diff)
    }

    fun importFen(fen: String): Boolean {
        val newState = ChessEngine.fenToState(fen)
        if (newState != null) {
            aiJob?.cancel()
            analysisJob?.cancel()
            StockfishEngine.stop()
            _gameTree.value = GameTree(newState)
            _uiState.value = _uiState.value.copy(
                selectedSquare = null,
                legalMoves = emptyList(),
                lastMove = null,
                thinkingInfo = "",
                analysisPvLines = emptyMap(),
            )
            return true
        }
        return false
    }

    fun importPgn(pgnText: String) {
        val result = parsePgn(pgnText)
        if (result != null) {
            val (fen, moves) = result
            val initialState = ChessEngine.fenToState(fen)
            if (initialState != null) {
                var tree = GameTree(initialState)
                var failed = false
                for (move in moves) {
                    val legalMoves = ChessEngine.getLegalMoves(tree.currentState())
                    if (move in legalMoves) { tree = tree.addMove(move) } else { failed = true; break }
                }
                if (!failed || moves.isEmpty()) {
                    aiJob?.cancel()
                    analysisJob?.cancel()
                    StockfishEngine.stop()
                    _gameTree.value = tree
                    _uiState.value = _uiState.value.copy(
                        lastMove = moves.lastOrNull(),
                        selectedSquare = null,
                        legalMoves = emptyList(),
                        thinkingInfo = "",
                    )
                }
            }
        }
    }

    fun loadBoardEditorState(state: GameState) {
        aiJob?.cancel()
        analysisJob?.cancel()
        StockfishEngine.stop()
        _gameTree.value = GameTree(state)
        _uiState.value = _uiState.value.copy(
            selectedSquare = null,
            legalMoves = emptyList(),
            lastMove = null,
            thinkingInfo = "",
            analysisPvLines = emptyMap(),
        )
    }

    fun copyFenToClipboard(): String {
        val fen = StockfishAI.stateToFenPublic(_gameTree.value.currentState())
        val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Current FEN", fen))
        return fen
    }

    fun copyPgnToClipboard(): String {
        val pgn = generatePgn(_gameTree.value)
        val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("PGN", pgn))
        return pgn
    }

    // Dialog visibility
    fun showDifficultyDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showDifficultyDialog = show)
    }

    fun showTimeControlDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showTimeControlDialog = show)
    }

    fun showModeDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showModeDialog = show)
    }

    fun showFenImportDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showFenImportDialog = show)
    }

    fun showPgnImportDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showPgnImportDialog = show)
    }

    fun showBoardEditor(show: Boolean) {
        _uiState.value = _uiState.value.copy(showBoardEditor = show)
    }

    // === Private helpers ===

    private fun handleNavigationSquareClick(row: Int, col: Int) {
        val state = _gameTree.value.currentState()
        val current = _uiState.value
        val selected = current.selectedSquare
        if (selected != null) {
            val legalMoves = ChessEngine.getLegalMoves(state, state.currentTurn)
                .filter { it.fromRow == selected.first && it.fromCol == selected.second }
            val targetMove = legalMoves.find { it.toRow == row && it.toCol == col }
            if (targetMove != null) {
                _gameTree.value = _gameTree.value.addMove(targetMove)
                _uiState.value = current.copy(
                    selectedSquare = null,
                    legalMoves = emptyList(),
                    lastMove = targetMove,
                )
                playMoveSound(soundPool)
                vibrateMove(vibrator)
                return
            }
        }
        val piece = state.board[row][col]
        if (piece != null && piece.color == state.currentTurn) {
            val moves = ChessEngine.getLegalMoves(state, state.currentTurn)
                .filter { it.fromRow == row && it.fromCol == col }
            _uiState.value = current.copy(
                selectedSquare = row to col,
                legalMoves = moves,
            )
        } else {
            _uiState.value = current.copy(
                selectedSquare = null,
                legalMoves = emptyList(),
            )
        }
    }

    private fun executeMove(move: Move) {
        val tree = _gameTree.value
        _gameTree.value = tree.addMove(move)
        _uiState.value = _uiState.value.copy(
            lastMove = move,
            selectedSquare = null,
            legalMoves = emptyList(),
        )
        playMoveSound(soundPool)
        vibrateMove(vibrator)
        StockfishAI.recordMove(tree.currentState().currentTurn, 0L)

        if (_gameMode.value == GameMode.PLAY_WHITE &&
            _gameTree.value.currentState().currentTurn == PieceColor.BLACK &&
            !_gameTree.value.currentState().gameOver
        ) {
            triggerAiMove()
        }
        if (_gameMode.value == GameMode.PLAY_BLACK &&
            _gameTree.value.currentState().currentTurn == PieceColor.WHITE &&
            !_gameTree.value.currentState().gameOver
        ) {
            triggerAiMove()
        }
    }

    private fun triggerAiMove() {
        val current = _uiState.value
        if (current.aiThinking) return
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            _uiState.value = _uiState.value.copy(aiThinking = true)
            val clock = _clockState.value
            _clockState.value = clock.copy(
                whiteActive = (_gameTree.value.currentState().currentTurn == PieceColor.WHITE),
                blackActive = (_gameTree.value.currentState().currentTurn == PieceColor.BLACK),
                running = true,
            )
            delay(300)
            if (!isActive) {
                _uiState.value = _uiState.value.copy(aiThinking = false)
                return@launch
            }

            val capturedState = _gameTree.value.currentState()
            val searchResult = withContext(Dispatchers.IO) {
                try {
                    val pollJob = launch {
                        while (isActive) {
                            val info = StockfishAI.getLatestInfo()
                            if (info.isNotEmpty()) {
                                withContext(Dispatchers.Main) {
                                    _uiState.value = _uiState.value.copy(
                                        thinkingInfo = com.chessdemo.ui.util.formatStockfishInfo(info)
                                    )
                                }
                            }
                            delay(100)
                        }
                    }
                    val fen = StockfishAI.stateToFenPublic(capturedState)
                    val bestMove = StockfishAI.findBestMoveFromFen(fen)
                    pollJob.cancel()
                    bestMove to StockfishAI.getLatestInfo()
                } finally {
                    if (!isActive) StockfishEngine.stop()
                }
            }

            if (!isActive) {
                _uiState.value = _uiState.value.copy(aiThinking = false)
                return@launch
            }

            val (bestMove, finalInfo) = searchResult
            if (finalInfo.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(
                    thinkingInfo = com.chessdemo.ui.util.formatStockfishInfo(finalInfo)
                )
            }

            val tree = _gameTree.value
            if (bestMove != null && !tree.currentState().gameOver) {
                _gameTree.value = tree.addMove(bestMove)
                _uiState.value = _uiState.value.copy(lastMove = bestMove)
                StockfishAI.recordMove(tree.currentState().currentTurn, 0L)
                playMoveSound(soundPool)
                vibrateMove(vibrator)
            }
            _uiState.value = _uiState.value.copy(aiThinking = false)
            _clockState.value = _clockState.value.copy(running = false)
            checkAndTriggerAi()
        }
    }

    private fun checkAndTriggerAi() {
        val state = _gameTree.value.currentState()
        if (state.gameOver) return

        when (_gameMode.value) {
            GameMode.COMPUTER_VS_COMPUTER -> {
                if (!state.gameOver) triggerAiVsAiLoop()
            }
            GameMode.PLAY_WHITE -> {
                if (state.currentTurn == PieceColor.BLACK) triggerAiMove()
            }
            GameMode.PLAY_BLACK -> {
                if (state.currentTurn == PieceColor.WHITE) triggerAiMove()
            }
            GameMode.TWO_PLAYERS -> {} // nothing
        }
    }

    private fun triggerAiVsAiLoop() {
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            while (isActive) {
                val state = _gameTree.value.currentState()
                if (state.gameOver) break
                _uiState.value = _uiState.value.copy(aiThinking = true)
                _clockState.value = _clockState.value.copy(
                    whiteActive = (state.currentTurn == PieceColor.WHITE),
                    blackActive = (state.currentTurn == PieceColor.BLACK),
                    running = true,
                )
                delay(300)
                if (!isActive) break

                val capturedState = _gameTree.value.currentState()
                val bestMove = withContext(Dispatchers.IO) {
                    val fen = StockfishAI.stateToFenPublic(capturedState)
                    StockfishAI.findBestMoveFromFen(fen)
                }

                if (!isActive) break

                val tree = _gameTree.value
                if (bestMove != null && !tree.currentState().gameOver) {
                    _gameTree.value = tree.addMove(bestMove)
                    _uiState.value = _uiState.value.copy(lastMove = bestMove)
                    StockfishAI.recordMove(tree.currentState().currentTurn, 0L)
                    playMoveSound(soundPool)
                    vibrateMove(vibrator)
                }
                _uiState.value = _uiState.value.copy(aiThinking = false)
                _clockState.value = _clockState.value.copy(running = false)
                delay(500)
            }
        }
    }

    fun startAnalysis() {
        analysisJob?.cancel()
        val state = _gameTree.value.currentState()
        if (state.gameOver) return

        _uiState.value = _uiState.value.copy(aiThinking = true, analysisPvLines = emptyMap())
        StockfishAI.startAnalysis(state, multiPV = 3)

        analysisJob = viewModelScope.launch {
            while (isActive) {
                val info = StockfishAI.getLatestInfo()
                if (info.isNotEmpty()) {
                    val multipvIndex = com.chessdemo.ui.util.extractMultiPvIndex(info)
                    val lineSummary = com.chessdemo.ui.util.formatAnalysisLine(info)
                    if (multipvIndex != null && lineSummary.isNotEmpty()) {
                        _uiState.value = _uiState.value.copy(
                            analysisPvLines = _uiState.value.analysisPvLines + (multipvIndex to lineSummary)
                        )
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
        _uiState.value = _uiState.value.copy(aiThinking = false, analysisPvLines = emptyMap())
    }

    private fun startClockIfNeeded() {
        clockJob?.cancel()
        clockJob = viewModelScope.launch {
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

    override fun onCleared() {
        super.onCleared()
        StockfishEngine.stop()
        StockfishAI.stopAnalysis()
        soundPool.release()
        clockJob?.cancel()
        aiJob?.cancel()
        analysisJob?.cancel()
    }
}
