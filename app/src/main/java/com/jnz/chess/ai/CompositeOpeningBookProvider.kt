package com.jnz.chess.ai

import com.jnz.chess.domain.ChessEngine
import com.jnz.chess.domain.GameState
import com.jnz.chess.domain.Move

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

}
