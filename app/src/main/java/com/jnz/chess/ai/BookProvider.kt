package com.jnz.chess.ai

import com.jnz.chess.domain.GameState
import com.jnz.chess.domain.Move

/** Alias so existing OpeningBookProvider implementations work with CompositeBookProvider. */
typealias BookProvider = OpeningBookProvider

/** Chains multiple BookProviders, deduplicating moves and preserving order. */
class CompositeBookProvider(
    private val providers: List<BookProvider>
) : BookProvider {
    override fun lookup(state: GameState): List<Pair<String, Int>> {
        val seen = mutableSetOf<String>()
        val results = mutableListOf<Pair<String, Int>>()
        for (provider in providers) {
            for ((uci, weight) in provider.lookup(state)) {
                if (uci !in seen) {
                    seen.add(uci)
                    results.add(uci to weight)
                }
            }
        }
        return results
    }
}

/** Select a move from a weighted list, filtered against legal moves. */
fun selectBookMove(bookMoves: List<Pair<String, Int>>, legalMoves: List<Move>): Move? {
    val totalWeight = bookMoves.sumOf { it.second }
    var random = (Math.random() * totalWeight).toInt()
    for ((uci, weight) in bookMoves) {
        random -= weight
        if (random <= 0) {
            val move = uciToMove(uci)
            if (move != null && legalMoves.any { matchesMove(it, move) }) return move
        }
    }
    // Fallback: try all candidates
    for ((uci, _) in bookMoves) {
        val move = uciToMove(uci)
        if (move != null && legalMoves.any { matchesMove(it, move) }) return move
    }
    return null
}

fun uciToMove(uci: String): Move? {
    if (uci.length < 4) return null
    val fromCol = uci[0] - 'a'
    val fromRank = uci[1] - '1'
    val toCol = uci[2] - 'a'
    val toRank = uci[3] - '1'
    val fromRow = 7 - fromRank
    val toRow = 7 - toRank
    if (fromCol !in 0..7 || toCol !in 0..7 || fromRow !in 0..7 || toRow !in 0..7) return null
    val promotionType = if (uci.length >= 5) {
        when (uci[4]) {
            'q' -> com.jnz.chess.domain.PieceType.QUEEN
            'r' -> com.jnz.chess.domain.PieceType.ROOK
            'b' -> com.jnz.chess.domain.PieceType.BISHOP
            'n' -> com.jnz.chess.domain.PieceType.KNIGHT
            else -> com.jnz.chess.domain.PieceType.QUEEN
        }
    } else null
    return Move(fromRow, fromCol, toRow, toCol, promotionType = promotionType)
}

private fun matchesMove(move: Move, target: Move): Boolean =
    move.fromRow == target.fromRow && move.fromCol == target.fromCol &&
    move.toRow == target.toRow && move.toCol == target.toCol &&
    move.promotionType == target.promotionType
