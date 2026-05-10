package com.chessdemo.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private fun canUserPlay(gameMode: GameMode, currentTurn: PieceColor): Boolean {
    return when (gameMode) {
        GameMode.PLAY_WHITE -> currentTurn == PieceColor.WHITE
        GameMode.PLAY_BLACK -> currentTurn == PieceColor.BLACK
        GameMode.TWO_PLAYERS -> true
        GameMode.COMPUTER_VS_COMPUTER -> false
    }
}

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

class GameViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {

    // Core game state
    private val _gameTree = MutableStateFlow(run {
        val savedFen = savedStateHandle.get<String>("current_fen")
        if (savedFen != null) {
            ChessEngine.fenToStateOrNull(savedFen)?.let { GameTree(it) } ?: GameTree.fromInitial()
        } else {
            GameTree.fromInitial()
        }
    })
    val gameTree: StateFlow<GameTree> = _gameTree.asStateFlow()

    // UI state
    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    // Clock state
    val clockManager = ClockEngineManager()
    val clockState: StateFlow<ClockState> = clockManager.clockState

    // Mode state
    private val _gameMode = MutableStateFlow(GameMode.PLAY_WHITE)
    val gameMode: StateFlow<GameMode> = _gameMode.asStateFlow()

    private val _navMode = MutableStateFlow(NavMode.PLAYING)
    val navMode: StateFlow<NavMode> = _navMode.asStateFlow()

    // AI manager
    private val app: Context get() = getApplication()
    private val soundPool by lazy { createSoundPool() }
    private val vibrator by lazy { app.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator }

    private val aiManager = AiEngineManager(
        scope = viewModelScope,
        getGameState = { _gameTree.value.currentState() },
        getGameTree = { _gameTree.value },
        applyMove = { move -> applyMoveInternal(move) },
        updateUi = { transform -> _uiState.value = transform(_uiState.value) },
        getGameMode = { _gameMode.value },
        getClockState = { clockManager.clockState.value },
        soundPool = soundPool,
        vibrator = vibrator,
    )

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
        aiManager.cleanup()
        clockManager.reset()
        _gameTree.value = GameTree.fromInitial()
        _uiState.value = GameUiState(boardFlipped = _uiState.value.boardFlipped)
        _gameMode.value = GameMode.PLAY_WHITE
        _navMode.value = NavMode.PLAYING
        StockfishAI.resetClocks()
        clockManager.startClockIfNeeded(viewModelScope)
    }

    fun goBack() {
        aiManager.cleanup()
        if (_uiState.value.aiThinking) StockfishEngine.stop()
        aiManager.stopAnalysis()
        val prevTree = _gameTree.value.goBack()
        _gameTree.value = prevTree
        _uiState.value = _uiState.value.copy(
            selectedSquare = null,
            legalMoves = emptyList(),
            lastMove = prevTree.moveList().lastOrNull(),
            thinkingInfo = "",
            analysisPvLines = emptyMap(),
        )
    }

    fun goForward() {
        val prevTree = _gameTree.value.goForward()
        _gameTree.value = prevTree
        _uiState.value = _uiState.value.copy(
            selectedSquare = null,
            legalMoves = emptyList(),
            lastMove = prevTree.moveList().lastOrNull(),
        )
    }

    fun goToMove(index: Int) {
        aiManager.cleanup()
        if (_uiState.value.aiThinking) StockfishEngine.stop()
        aiManager.stopAnalysis()
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
            aiManager.triggerAiVsAiLoop()
        }
    }

    fun updateClockActivation(active: Boolean, turn: PieceColor) {
        clockManager.updateClockActivation(active, turn)
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

        if (selected != null) {
            val targetMove = legalMoves.find { it.toRow == row && it.toCol == col }
            if (targetMove != null) {
                executeMove(targetMove)
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

    fun applyClockSettings(minutes: Long, increment: Long) {
        clockManager.applyClockSettings(minutes, increment)
        StockfishAI.setClocks(
            clockManager.clockState.value.whiteTimeMs,
            clockManager.clockState.value.blackTimeMs,
            clockManager.clockState.value.incrementMs,
        )
    }

    fun setDifficulty(diff: StockfishAI.Difficulty) {
        aiManager.setDifficulty(diff)
    }

    fun importFen(fen: String): Boolean {
        val result = ChessEngine.fenToState(fen)
        if (result is FenParseResult.Success) {
            val newState = result.state
            aiManager.cleanup()
            aiManager.stopAnalysis()
            _gameTree.value = GameTree(newState)
            _uiState.value = _uiState.value.copy(
                selectedSquare = null,
                legalMoves = emptyList(),
                lastMove = null,
                thinkingInfo = "",
                analysisPvLines = emptyMap(),
            )
            savedStateHandle["current_fen"] = fen
            return true
        }
        return false
    }

    fun importPgn(pgnText: String) {
        val result = parsePgn(pgnText)
        if (result != null) {
            val (fen, moves) = result
            val initialState = ChessEngine.fenToStateOrNull(fen)
            if (initialState != null) {
                var tree = GameTree(initialState)
                var failed = false
                for (move in moves) {
                    val legalMoves = ChessEngine.getLegalMoves(tree.currentState())
                    if (move in legalMoves) { tree = tree.addMove(move) } else { failed = true; break }
                }
                if (!failed || moves.isEmpty()) {
                    aiManager.cleanup()
                    aiManager.stopAnalysis()
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
        aiManager.cleanup()
        aiManager.stopAnalysis()
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

    // AI & Analysis delegates
    fun startAnalysis() = aiManager.startAnalysis()
    fun stopAnalysis() = aiManager.stopAnalysis()

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
        applyMoveInternal(move)
        StockfishAI.recordMove(_gameTree.value.currentState().currentTurn, 0L)
        playMoveSound(soundPool)
        vibrateMove(vibrator)

        aiManager.checkAndTriggerAi()
    }

    private fun applyMoveInternal(move: Move) {
        val tree = _gameTree.value
        _gameTree.value = tree.addMove(move)
        _uiState.value = _uiState.value.copy(
            lastMove = move,
            selectedSquare = null,
            legalMoves = emptyList(),
        )
        savedStateHandle["current_fen"] =
            StockfishAI.stateToFenPublic(_gameTree.value.currentState())
    }

    override fun onCleared() {
        super.onCleared()
        aiManager.cleanup()
        clockManager.cleanup()
        soundPool.release()
    }
}
