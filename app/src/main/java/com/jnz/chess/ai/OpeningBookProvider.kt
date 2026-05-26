package com.jnz.chess.ai

import com.jnz.chess.domain.GameState
import com.jnz.chess.domain.Move

/** Provider of opening book moves. */
interface OpeningBookProvider {
    /** Look up candidate moves for the given position. Returns (UCI move, weight) pairs. */
    fun lookup(state: GameState): List<Pair<String, Int>>
}
