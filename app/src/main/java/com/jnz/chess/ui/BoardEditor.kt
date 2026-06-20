package com.jnz.chess.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jnz.chess.domain.*

private val lightSquare = ComposeColor(0xFFF0D9B5)
private val darkSquare = ComposeColor(0xFFB58863)

/** Board editor dialog: place pieces, set game state, validate, and load into game. */
@Composable
fun BoardEditorDialog(
    initialState: GameState,
    onDismiss: () -> Unit,
    onLoad: (GameState) -> Unit,
) {
    // Editable board: mutable copy of initial state
    var editorBoard by remember { mutableStateOf(initialState.board.map { row -> row.copyOf() }.toTypedArray()) }
    var selectedPiece by remember { mutableStateOf<Piece?>(null) } // null = eraser
    var currentTurn by remember { mutableStateOf(initialState.currentTurn) }
    var whiteKS by remember { mutableStateOf(initialState.castlingRights.whiteKingSide) }
    var whiteQS by remember { mutableStateOf(initialState.castlingRights.whiteQueenSide) }
    var blackKS by remember { mutableStateOf(initialState.castlingRights.blackKingSide) }
    var blackQS by remember { mutableStateOf(initialState.castlingRights.blackQueenSide) }
    var epTarget by remember { mutableStateOf(initialState.enPassantTarget) }
    var halfMove by remember { mutableStateOf(initialState.halfMoveClock) }
    var fullMove by remember { mutableStateOf(initialState.fullMoveNumber) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("棋盘编辑器", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Piece palette
                Text("选择棋子:", fontSize = 12.sp, color = ComposeColor.Gray)
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    // Eraser
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .border(
                                2.dp,
                                if (selectedPiece == null) ComposeColor.Blue else ComposeColor.Gray,
                                RoundedCornerShape(4.dp)
                            )
                            .background(ComposeColor(0xFFEEEEEE))
                            .clickable { selectedPiece = null },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✕", fontSize = 16.sp, color = ComposeColor.Red)
                    }
                    Spacer(Modifier.width(4.dp))
                    // Pieces
                    for (color in listOf(Color.WHITE, Color.BLACK)) {
                        for (type in listOf(
                            PieceType.PAWN, PieceType.KNIGHT, PieceType.BISHOP,
                            PieceType.ROOK, PieceType.QUEEN, PieceType.KING
                        )) {
                            val piece = Piece(type, color)
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .border(
                                        2.dp,
                                        if (selectedPiece == piece) ComposeColor.Blue else ComposeColor.Gray,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .background(ComposeColor(0xFFEEEEEE))
                                    .clickable { selectedPiece = piece },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(piece.unicode(), fontSize = 20.sp)
                            }
                            Spacer(Modifier.width(2.dp))
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Editor board
                val borderColor = MaterialTheme.colorScheme.onBackground
                Column(
                    modifier = Modifier
                        .width(280.dp)
                        .border(2.dp, borderColor, RoundedCornerShape(4.dp))
                ) {
                    for (row in 0..7) {
                        Row(modifier = Modifier.weight(1f)) {
                            for (col in 0..7) {
                                val isLight = (row + col) % 2 == 0
                                val piece = editorBoard[row][col]
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .background(if (isLight) lightSquare else darkSquare)
                                        .clickable {
                                            if (selectedPiece != null) {
                                                editorBoard = editorBoard.map { it.copyOf() }
                                                    .toTypedArray()
                                                editorBoard[row][col] = selectedPiece
                                            } else {
                                                // Eraser: remove piece
                                                editorBoard = editorBoard.map { it.copyOf() }
                                                    .toTypedArray()
                                                editorBoard[row][col] = null
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (piece != null) {
                                        Text(piece.unicode(), fontSize = 24.sp)
                                    }
                                    // Coordinate labels
                                    if (row == 7) {
                                        Text(
                                            text = "abcdefgh"[col].toString(),
                                            color = if (isLight) darkSquare else lightSquare,
                                            fontSize = 8.sp,
                                            modifier = Modifier.align(Alignment.BottomEnd).padding(1.dp)
                                        )
                                    }
                                    if (col == 0) {
                                        Text(
                                            text = "${8 - row}",
                                            color = if (isLight) darkSquare else lightSquare,
                                            fontSize = 8.sp,
                                            modifier = Modifier.align(Alignment.TopStart).padding(1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Game state settings
                Text("局面设置:", fontSize = 12.sp, color = ComposeColor.Gray, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))

                // Turn
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("先手: ", fontSize = 12.sp)
                    listOf(Color.WHITE, Color.BLACK).forEach { c ->
                        TextButton(
                            onClick = { currentTurn = c },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            colors = ButtonDefaults.textButtonColors(
                                containerColor = if (currentTurn == c) ComposeColor(0xFF444444) else ComposeColor.Transparent
                            )
                        ) {
                            Text(if (c == Color.WHITE) "白方" else "黑方", fontSize = 12.sp)
                        }
                    }
                }

                // Castling
                Text("王车易位:", fontSize = 12.sp)
                Row {
                    listOf(
                        "WK" to { whiteKS },
                        "WQ" to { whiteQS },
                        "BK" to { blackKS },
                        "BQ" to { blackQS }
                    ).forEach { (label, getter) ->
                        FilterChip(
                            selected = getter(),
                            onClick = {
                                when (label) {
                                    "WK" -> whiteKS = !whiteKS
                                    "WQ" -> whiteQS = !whiteQS
                                    "BK" -> blackKS = !blackKS
                                    "BQ" -> blackQS = !blackQS
                                }
                            },
                            label = { Text(label, fontSize = 10.sp) }
                        )
                    }
                }

                // En passant
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("吃过路兵: ", fontSize = 12.sp)
                    Text(
                        text = epTarget?.let { (r, c) -> "abcdefgh"[c].toString() + (8 - r) } ?: "无",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Move counters
                Row {
                    Text("半回合: ", fontSize = 12.sp)
                    OutlinedButton(
                        onClick = { halfMove = maxOf(0, halfMove - 1) },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) { Text("-") }
                    Text("$halfMove", fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
                    OutlinedButton(
                        onClick = { halfMove++ },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) { Text("+") }

                    Spacer(Modifier.width(16.dp))

                    Text("全回合: ", fontSize = 12.sp)
                    OutlinedButton(
                        onClick = { fullMove = maxOf(1, fullMove - 1) },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) { Text("-") }
                    Text("$fullMove", fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp))
                    OutlinedButton(
                        onClick = { fullMove++ },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) { Text("+") }
                }

                // Error message
                if (errorMsg != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(errorMsg!!, color = ComposeColor.Red, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = {
                    // Validate
                    val whiteKings = countPieces(editorBoard, PieceType.KING, Color.WHITE)
                    val blackKings = countPieces(editorBoard, PieceType.KING, Color.BLACK)
                    if (whiteKings != 1 || blackKings != 1) {
                        errorMsg = "双方必须各有一个王 (白:$whiteKings 黑:$blackKings)"
                        return@TextButton
                    }

                    // Check pawns not on rank 1 or 8
                    for (col in 0..7) {
                        if (editorBoard[0][col]?.type == PieceType.PAWN) {
                            errorMsg = "白兵不能在第8行"
                            return@TextButton
                        }
                        if (editorBoard[7][col]?.type == PieceType.PAWN) {
                            errorMsg = "黑兵不能在第1行"
                            return@TextButton
                        }
                    }

                    // Build GameState from editor board
                    val castling = CastlingRights(whiteKS, whiteQS, blackKS, blackQS)
                    val stateWithoutKey = GameState(
                        board = editorBoard,
                        currentTurn = currentTurn,
                        castlingRights = castling,
                        enPassantTarget = epTarget,
                        moveHistory = emptyList(),
                        halfMoveClock = halfMove,
                        fullMoveNumber = fullMove,
                        gameResult = null,
                        positionHistory = emptyList(),
                    )

                    // Check if king is in check
                    val kingPos = stateWithoutKey.findKing(currentTurn)
                    if (kingPos != null && ChessEngine.isSquareAttacked(
                            stateWithoutKey, kingPos.first, kingPos.second,
                            if (currentTurn == Color.WHITE) Color.BLACK else Color.WHITE
                        )
                    ) {
                        errorMsg = "先手方王不能被将"
                        return@TextButton
                    }

                    errorMsg = null
                    val key = stateWithoutKey.positionKey()
                    val tempState = stateWithoutKey.copyWith(
                        newPositionHistory = listOf(key),
                        newPositionCounts = mapOf(key to 1)
                    )
                    onLoad(tempState)
                }) {
                    Text("加载局面")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private fun countPieces(board: Array<Array<Piece?>>, type: PieceType, color: Color): Int {
    var count = 0
    for (r in 0..7) for (c in 0..7) {
        val p = board[r][c] ?: continue
        if (p.type == type && p.color == color) count++
    }
    return count
}
