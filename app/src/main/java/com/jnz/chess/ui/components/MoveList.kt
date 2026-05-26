package com.jnz.chess.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jnz.chess.domain.GameState
import com.jnz.chess.domain.Move
import com.jnz.chess.domain.PieceType

@Composable
fun MoveListBar(
    moves: List<Move>,
    states: List<GameState>,
    currentIndex: Int,
    onMoveClick: (Int) -> Unit,
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier.fillMaxWidth().height(32.dp)
            .background(Color(0xFF2A2A2A), androidx.compose.foundation.shape.RoundedCornerShape(6.dp))
            .horizontalScroll(scrollState),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (moves.isEmpty()) {
            Text(text = "开局", modifier = Modifier.padding(horizontal = 8.dp), fontSize = 12.sp, color = Color.Gray)
        } else {
            var moveNum = 1
            for (i in moves.indices step 2) {
                val whiteMove = moves[i]
                val isCurrentWhite = (i + 1) == currentIndex
                Text(text = "$moveNum.", modifier = Modifier.padding(horizontal = 2.dp), fontSize = 12.sp, color = Color.Gray)
                Text(
                    text = formatMoveAlgebraic(whiteMove, states.getOrNull(i)),
                    modifier = Modifier.clickable { onMoveClick(i) }.padding(horizontal = 2.dp),
                    fontSize = 12.sp,
                    color = if (isCurrentWhite) Color.White else Color.LightGray,
                    fontWeight = if (isCurrentWhite) FontWeight.Bold else FontWeight.Normal,
                )
                if (i + 1 < moves.size) {
                    val blackMove = moves[i + 1]
                    val isCurrentBlack = (i + 2) == currentIndex
                    Text(
                        text = formatMoveAlgebraic(blackMove, states.getOrNull(i + 1)),
                        modifier = Modifier.clickable { onMoveClick(i + 1) }.padding(horizontal = 2.dp),
                        fontSize = 12.sp,
                        color = if (isCurrentBlack) Color.White else Color.LightGray,
                        fontWeight = if (isCurrentBlack) FontWeight.Bold else FontWeight.Normal,
                    )
                }
                moveNum++
            }
        }
    }
}

fun formatMoveAlgebraic(move: Move, state: GameState?): String {
    val files = "abcdefgh"
    if (move.isCastling) return if (move.toCol == 6) "O-O" else "O-O-O"

    val piece = state?.board?.getOrNull(move.fromRow)?.getOrNull(move.fromCol)
    val pieceSymbol = when (piece?.type) {
        PieceType.KING -> "K"; PieceType.QUEEN -> "Q"; PieceType.ROOK -> "R"
        PieceType.BISHOP -> "B"; PieceType.KNIGHT -> "N"; PieceType.PAWN -> ""; else -> ""
    }

    val isCapture = move.isEnPassant || (state?.board?.getOrNull(move.toRow)?.getOrNull(move.toCol) != null)
    val capture = if (isCapture) "x" else ""
    val pawnFile = if (piece?.type == PieceType.PAWN && isCapture) files[move.fromCol] else ""
    val promo = if ((move.toRow == 0 || move.toRow == 7) && piece?.type == PieceType.PAWN) {
        when (move.promotionType ?: PieceType.QUEEN) {
            PieceType.QUEEN -> "=Q"; PieceType.ROOK -> "=R"; PieceType.BISHOP -> "=B"
            PieceType.KNIGHT -> "=N"; else -> ""
        }
    } else ""

    val toFile = files[move.toCol]
    val toRank = 8 - move.toRow
    return "$pieceSymbol$pawnFile$capture$toFile$toRank$promo"
}
