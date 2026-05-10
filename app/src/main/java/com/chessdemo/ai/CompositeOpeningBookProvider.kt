package com.chessdemo.ai

import com.chessdemo.domain.ChessEngine
import com.chessdemo.domain.GameState
import com.chessdemo.domain.Move

/** Chains multiple OpeningBookProviders. Returns the first non-empty result. */
class CompositeOpeningBookProvider(
    private val providers: List<OpeningBookProvider>,
) : OpeningBookProvider {

    constructor(vararg providers: OpeningBookProvider) : this(providers.toList())

    override fun lookup(state: GameState): List<Pair<String, Int>> {
        for (provider in providers) {
            val candidates = provider.lookup(state)
            if (candidates.isNotEmpty()) return candidates
        }
        return emptyList()
    }

    /** Select a legal move from the book candidates using weighted random. */
    fun selectMove(state: GameState): Move? {
        val candidates = lookup(state)
        if (candidates.isEmpty()) return null

        val legalMoves = ChessEngine.getLegalMoves(state, state.currentTurn)

        // Try weighted random first
        val totalWeight = candidates.sumOf { it.second }
        var random = (Math.random() * totalWeight).toInt()
        for ((moveUci, weight) in candidates) {
            random -= weight
            if (random <= 0) {
                val move = uciToMove(moveUci)
                if (move != null && isLegalIn(move, legalMoves)) return move
                break
            }
        }

        // Fallback: first legal candidate
        for ((moveUci, _) in candidates) {
            val move = uciToMove(moveUci)
            if (move != null && isLegalIn(move, legalMoves)) return move
        }

        return null
    }

    private fun isLegalIn(move: Move, legalMoves: List<Move>): Boolean =
        legalMoves.any {
            it.fromRow == move.fromRow && it.fromCol == move.fromCol &&
            it.toRow == move.toRow && it.toCol == move.toCol
        }

    private fun uciToMove(uci: String): Move? {
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
                'q' -> com.chessdemo.domain.PieceType.QUEEN
                'r' -> com.chessdemo.domain.PieceType.ROOK
                'b' -> com.chessdemo.domain.PieceType.BISHOP
                'n' -> com.chessdemo.domain.PieceType.KNIGHT
                else -> com.chessdemo.domain.PieceType.QUEEN
            }
        } else com.chessdemo.domain.PieceType.QUEEN
        return Move(fromRow = fromRow, fromCol = fromCol, toRow = toRow, toCol = toCol, promotionType = promotionType)
    }
}
