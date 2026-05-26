package com.jnz.chess.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jnz.chess.ai.StockfishAI
import com.jnz.chess.domain.Color as PieceColor
import com.jnz.chess.ui.components.*

@Composable
fun GameScreen(viewModel: GameViewModel) {
    val context = LocalContext.current
    val gameTree = viewModel.gameTree.collectAsState().value
    val uiState = viewModel.uiState.collectAsState().value
    val clockState = viewModel.clockManager.clockState.collectAsState().value
    val gameMode = viewModel.gameMode.collectAsState().value
    val navMode = viewModel.navMode.collectAsState().value

    val currentState = gameTree.currentState()

    // Load opening book and NNUE network on first composition
    LaunchedEffect(Unit) {
        StockfishAI.loadBookFromAssets(context)
        StockfishAI.loadNNUEFromAssets(context)
    }

    // Clock activation: activate the clock of the side whose turn it is
    LaunchedEffect(navMode, currentState.currentTurn, currentState.gameOver, uiState.aiThinking) {
        if (navMode == NavMode.PLAYING && !currentState.gameOver && !uiState.aiThinking) {
            viewModel.updateClockActivation(true, currentState.currentTurn)
        } else {
            viewModel.updateClockActivation(false, currentState.currentTurn)
        }
    }

    // Analysis mode trigger
    LaunchedEffect(navMode, gameTree.currentNodeIndex) {
        if (navMode != NavMode.ANALYSIS) {
            viewModel.stopAnalysis()
            return@LaunchedEffect
        }
        val state = gameTree.currentState()
        if (state.gameOver) return@LaunchedEffect
        viewModel.startAnalysis()
    }

    Column(
        modifier = Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ControlBar(
            navMode = navMode, onNavModeChange = { viewModel.toggleNavMode() },
            gameMode = gameMode, onGameModeClick = { viewModel.showModeDialog(true) },
            difficulty = StockfishAI.difficulty, onDifficultyClick = { viewModel.showDifficultyDialog(true) },
            onReset = { viewModel.reset() },
            onFlipBoard = { viewModel.flipBoard() }, boardFlipped = uiState.boardFlipped,
            canGoBack = gameTree.canGoBack(), canGoForward = gameTree.canGoForward(),
            onGoBack = { viewModel.goBack() },
            onGoForward = { viewModel.goForward() },
        )

        Spacer(Modifier.height(6.dp))

        ClockBar(
            whiteTimeMs = clockState.whiteTimeMs, blackTimeMs = clockState.blackTimeMs,
            whiteActive = clockState.whiteActive, blackActive = clockState.blackActive,
            isWhiteTurn = currentState.currentTurn == PieceColor.WHITE,
            onWhiteClick = { viewModel.showTimeControlDialog(true) }, onBlackClick = { viewModel.showTimeControlDialog(true) },
            boardFlipped = uiState.boardFlipped,
        )

        Spacer(Modifier.height(8.dp))

        MoveListBar(
            moves = gameTree.moveList(), states = gameTree.states, currentIndex = gameTree.currentNodeIndex,
            onMoveClick = { index -> viewModel.goToMove(index + 1) },
        )

        Spacer(Modifier.height(6.dp))

        CapturedPieces(gameState = currentState)

        Spacer(Modifier.height(4.dp))

        val currentEval = com.jnz.chess.ui.util.extractEvalCp(uiState.thinkingInfo, uiState.analysisPvLines)
        ChessBoard(
            gameState = currentState, selectedSquare = uiState.selectedSquare, legalMoves = uiState.legalMoves,
            lastMove = uiState.lastMove, boardFlipped = uiState.boardFlipped,
            pvMoves = uiState.thinkingInfo.split("pv:").getOrElse(1) { "" }
                .trim().split(" ").filter { it.isNotEmpty() }.take(5),
            evalCp = currentEval,
            onSquareClick = { row, col -> viewModel.onSquareClick(row, col) },
        )

        Spacer(Modifier.height(4.dp))

        CapturedPiecesBottom(gameState = currentState)

        Spacer(Modifier.height(8.dp))

        ThinkingPanel(uiState.thinkingInfo, uiState.analysisPvLines, gameMode, navMode)

        Spacer(Modifier.height(8.dp))

        if (currentState.gameOver && currentState.result != null) {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Text(text = currentState.result ?: "", modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            OutlinedButton(onClick = { viewModel.copyFenToClipboard() },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp), shape = RoundedCornerShape(8.dp)) {
                Text("FEN", fontSize = 12.sp)
            }
            OutlinedButton(onClick = { viewModel.showFenImportDialog(true) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp), shape = RoundedCornerShape(8.dp)) {
                Text("导入FEN", fontSize = 12.sp)
            }
            OutlinedButton(onClick = { viewModel.copyPgnToClipboard() },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp), shape = RoundedCornerShape(8.dp)) {
                Text("PGN", fontSize = 12.sp)
            }
            OutlinedButton(onClick = { viewModel.showPgnImportDialog(true) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp), shape = RoundedCornerShape(8.dp)) {
                Text("导入PGN", fontSize = 12.sp)
            }
            OutlinedButton(onClick = { viewModel.showBoardEditor(true) },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp), shape = RoundedCornerShape(8.dp)) {
                Text("编辑", fontSize = 12.sp)
            }
        }
    }

    // === Dialogs ===
    when (uiState.dialogState) {
        is DialogState.Difficulty -> DifficultyDialog(
            currentDifficulty = StockfishAI.difficulty,
            onDismiss = { viewModel.showDifficultyDialog(false) },
            onSelect = { diff -> viewModel.setDifficulty(diff); viewModel.showDifficultyDialog(false) })
        is DialogState.TimeControl -> TimeControlDialog(
            initialMinutes = clockState.whiteTimeMs / 60000, initialIncrement = clockState.incrementMs / 1000,
            onDismiss = { viewModel.showTimeControlDialog(false) },
            onApply = { minutes, increment, _ ->
                viewModel.applyClockSettings(minutes, increment)
                viewModel.showTimeControlDialog(false)
            })
        is DialogState.Mode -> GameModeDialog(
            currentMode = gameMode, onDismiss = { viewModel.showModeDialog(false) },
            onSelect = { mode ->
                viewModel.setGameMode(mode)
                viewModel.reset()
                viewModel.showModeDialog(false)
                if (mode == GameMode.COMPUTER_VS_COMPUTER) viewModel.triggerComputerVsComputer()
            })
        is DialogState.FenImport -> FenImportDialog(
            onDismiss = { viewModel.showFenImportDialog(false) },
            onImport = { fen ->
                if (viewModel.importFen(fen)) viewModel.showFenImportDialog(false)
            })
        is DialogState.PgnImport -> PgnImportDialog(
            onDismiss = { viewModel.showPgnImportDialog(false) },
            onImport = { pgnText ->
                viewModel.importPgn(pgnText)
                viewModel.showPgnImportDialog(false)
            })
        is DialogState.BoardEditor -> BoardEditorDialog(
            initialState = currentState, onDismiss = { viewModel.showBoardEditor(false) },
            onLoad = { state ->
                viewModel.loadBoardEditorState(state)
                viewModel.showBoardEditor(false)
            })
        is DialogState.None -> { /* no dialog */ }
    }
}
