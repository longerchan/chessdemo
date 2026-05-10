package com.chessdemo.ai

import com.chessdemo.domain.GameState
import com.chessdemo.domain.Move

/** Provider of opening book moves. */
interface OpeningBookProvider {
    /** Look up candidate moves for the given position. Returns (UCI move, weight) pairs. */
    fun lookup(state: GameState): List<Pair<String, Int>>
}
