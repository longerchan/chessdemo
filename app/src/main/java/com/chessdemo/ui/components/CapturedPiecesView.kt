package com.chessdemo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.chessdemo.domain.*
import com.chessdemo.ui.util.*
import com.chessdemo.domain.Color as PieceColor

private fun computeCapturedPieces(gameState: GameState): List<Piece> {
    val initial = GameState.initial().board
    val current = gameState.board
    val initialCounts = mutableMapOf<Piece, Int>()
    val currentCounts = mutableMapOf<Piece, Int>()
    for (r in 0..7) for (c in 0..7) {
        initial[r][c]?.let { initialCounts[it] = initialCounts.getOrDefault(it, 0) + 1 }
        current[r][c]?.let { currentCounts[it] = currentCounts.getOrDefault(it, 0) + 1 }
    }
    val captured = mutableListOf<Piece>()
    for ((piece, count) in initialCounts) {
        val remaining = currentCounts.getOrDefault(piece, 0)
        repeat(count - remaining) { captured.add(piece) }
    }
    val pieceValue = mapOf(
        PieceType.QUEEN to 5, PieceType.ROOK to 4, PieceType.BISHOP to 3,
        PieceType.KNIGHT to 2, PieceType.PAWN to 1, PieceType.KING to 0
    )
    captured.sortByDescending { pieceValue[it.type] }
    return captured
}

@Composable
fun CapturedPieces(gameState: GameState) {
    val captured = computeCapturedPieces(gameState)
    val whiteCaptured = captured.filter { it.color == PieceColor.WHITE }
    if (whiteCaptured.isNotEmpty()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            whiteCaptured.forEach { piece ->
                Text(text = piece.unicode(), fontSize = 20.sp, modifier = Modifier.padding(horizontal = 1.dp))
            }
        }
    }
}

@Composable
fun CapturedPiecesBottom(gameState: GameState) {
    val captured = computeCapturedPieces(gameState)
    val blackCaptured = captured.filter { it.color == PieceColor.BLACK }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        blackCaptured.forEach { piece ->
            Text(text = piece.unicode(), fontSize = 20.sp, modifier = Modifier.padding(horizontal = 1.dp))
        }
    }
}
