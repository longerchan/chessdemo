package com.chessdemo.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chessdemo.domain.*
import com.chessdemo.ui.util.*

@Composable
fun ChessBoard(
    gameState: GameState,
    selectedSquare: Pair<Int, Int>?,
    legalMoves: List<Move>,
    lastMove: Move?,
    boardFlipped: Boolean,
    pvMoves: List<String>,
    evalCp: Int?,
    onSquareClick: (Int, Int) -> Unit,
) {
    val borderColor = MaterialTheme.colorScheme.onBackground
    val files = "abcdefgh"
    val textMeasurer = rememberTextMeasurer()

    val lastMoveSquares = mutableSetOf<Pair<Int, Int>>()
    if (lastMove != null) {
        lastMoveSquares.add(lastMove.fromRow to lastMove.fromCol)
        lastMoveSquares.add(lastMove.toRow to lastMove.toCol)
    }

    val checkSquare = run {
        val kingPos = gameState.findKing(gameState.currentTurn)
        if (kingPos != null && ChessEngine.isSquareAttacked(
                gameState, kingPos.first, kingPos.second,
                if (gameState.currentTurn == Color.WHITE) Color.BLACK else Color.WHITE
            )
        ) kingPos else null
    }

    val evalWhiteRatio = evalCp?.let { cp ->
        0.5f + (cp.coerceIn(-1000, 1000) / 2000f)
    } ?: 0.5f

    val barWidth = 24.dp
    Row(
        modifier = Modifier.fillMaxWidth().aspectRatio(1.08f)
            .border(3.dp, borderColor, RoundedCornerShape(4.dp))
    ) {
        // Evaluation bar
        Canvas(modifier = Modifier.width(barWidth).fillMaxHeight()) {
            val bgH = size.height
            val whiteH = bgH * evalWhiteRatio
            drawRect(ComposeColor(0xFF333333), size = size)
            drawRect(ComposeColor(0xFFEEEEEE), size = Size(size.width, whiteH))
        }

        // Board Canvas
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            BoardCanvas(
                gameState = gameState,
                selectedSquare = selectedSquare,
                legalMoves = legalMoves,
                lastMoveSquares = lastMoveSquares,
                checkSquare = checkSquare,
                pvMoves = pvMoves,
                boardFlipped = boardFlipped,
                files = files,
                textMeasurer = textMeasurer,
                onSquareClick = onSquareClick,
            )
        }
    }
}

@Composable
private fun BoardCanvas(
    gameState: GameState,
    selectedSquare: Pair<Int, Int>?,
    legalMoves: List<Move>,
    lastMoveSquares: Set<Pair<Int, Int>>,
    checkSquare: Pair<Int, Int>?,
    pvMoves: List<String>,
    boardFlipped: Boolean,
    files: String,
    textMeasurer: TextMeasurer,
    onSquareClick: (Int, Int) -> Unit,
) {
    val density = LocalDensity.current
    val pieceFontSize = 32.sp
    val labelFontSize = 10.sp
    val legalDotRadius = 4.dp
    val pvDotRadius = 3.dp

    val boardRows = if (boardFlipped) (7 downTo 0).toList() else (0..7).toList()
    val boardCols = if (boardFlipped) (7 downTo 0).toList() else (0..7).toList()

    val pieceTextCache = remember { mutableMapOf<Piece, String>() }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(gameState, selectedSquare, legalMoves, boardFlipped) {
                detectTapGestures { offset ->
                    val sqWidth = size.width / 8f
                    val sqHeight = size.height / 8f
                    val col = (offset.x / sqWidth).toInt().coerceIn(0, 7)
                    val row = (offset.y / sqHeight).toInt().coerceIn(0, 7)
                    val actualRow = if (boardFlipped) 7 - row else row
                    val actualCol = if (boardFlipped) 7 - col else col
                    onSquareClick(actualRow, actualCol)
                }
            }
    ) {
        val sqWidth = size.width / 8f
        val sqHeight = size.height / 8f

        for (displayRow in boardRows.indices) {
            for (displayCol in boardCols.indices) {
                val row = boardRows[displayRow]
                val col = boardCols[displayCol]
                val x = displayCol * sqWidth
                val y = displayRow * sqHeight
                val isLight = (row + col) % 2 == 0

                // Square background
                drawRect(
                    color = if (isLight) LightSquare else DarkSquare,
                    topLeft = Offset(x, y),
                    size = Size(sqWidth, sqHeight)
                )

                // Last move highlight
                if ((row to col) in lastMoveSquares) {
                    drawRect(
                        color = LastMoveHighlight,
                        topLeft = Offset(x, y),
                        size = Size(sqWidth, sqHeight)
                    )
                }

                // Check highlight
                if (checkSquare?.first == row && checkSquare?.second == col) {
                    drawRect(
                        color = CheckHighlight,
                        topLeft = Offset(x, y),
                        size = Size(sqWidth, sqHeight)
                    )
                }

                // Selected highlight
                if (selectedSquare?.first == row && selectedSquare?.second == col) {
                    drawRect(
                        color = SelectedColor,
                        topLeft = Offset(x, y),
                        size = Size(sqWidth, sqHeight)
                    )
                }

                // Legal move dots
                if (legalMoves.any { it.toRow == row && it.toCol == col }) {
                    val piece = gameState.board[row][col]
                    if (piece != null) {
                        // Border dot on piece
                        drawRect(
                            color = LegalMoveDot,
                            topLeft = Offset(x, y),
                            size = Size(sqWidth, sqHeight),
                            style = Stroke(width = 3.dp.toPx())
                        )
                    } else {
                        // Small circle on empty square
                        val centerOffset = Offset(x + sqWidth / 2f, y + sqHeight / 2f)
                        drawCircle(
                            color = LegalMoveDot,
                            radius = with(density) { legalDotRadius.toPx() },
                            center = centerOffset
                        )
                    }
                }

                // PV indicator
                val isPvTarget = pvMoves.any {
                    it.length >= 5 && it[3] - 'a' == col && 7 - (it[4] - '1') == row
                }
                if (isPvTarget && gameState.board[row][col] == null) {
                    drawCircle(
                        color = ComposeColor(0x4000AAFF),
                        radius = with(density) { pvDotRadius.toPx() },
                        center = Offset(x + sqWidth / 2f, y + sqHeight / 2f)
                    )
                }

                // Piece
                val piece = gameState.board[row][col]
                if (piece != null) {
                    val symbol = pieceTextCache.getOrPut(piece) { piece.unicode() }
                    val style = TextStyle(
                        fontSize = pieceFontSize,
                        fontFamily = FontFamily.Default,
                        color = ComposeColor.Unspecified
                    )
                    val textResult = textMeasurer.measure(symbol, style)
                    val textOffset = Offset(
                        x + (sqWidth - textResult.size.width) / 2f,
                        y + (sqHeight - textResult.size.height) / 2f
                    )
                    drawText(textResult, topLeft = textOffset)
                }

                // File labels (a-h) on bottom edge
                if (displayRow == 7) {
                    val style = TextStyle(fontSize = labelFontSize, color = if (isLight) DarkSquare else LightSquare)
                    val textResult = textMeasurer.measure(files[col].toString(), style)
                    drawText(textResult, topLeft = Offset(x + sqWidth - textResult.size.width - 2.dp.toPx(), y + sqHeight - textResult.size.height - 2.dp.toPx()))
                }

                // Rank labels (1-8) on left edge
                if (displayCol == 0) {
                    val rankText = "${8 - row}"
                    val style = TextStyle(fontSize = labelFontSize, color = if (isLight) DarkSquare else LightSquare)
                    val textResult = textMeasurer.measure(rankText, style)
                    drawText(textResult, topLeft = Offset(x + 2.dp.toPx(), y + 2.dp.toPx()))
                }
            }
        }
    }
}
