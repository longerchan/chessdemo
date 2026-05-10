package com.chessdemo.domain

/**
 * Linear game tree backed by a list of GameState snapshots.
 * currentNodeIndex points into the list; 0 = initial position.
 */
class GameTree(
    val states: List<GameState>,
    val currentNodeIndex: Int,
) {
    /** Create from any initial state (e.g., from FEN). */
    constructor(initialState: GameState) : this(listOf(initialState), 0)

    fun currentState(): GameState = states[currentNodeIndex]

    fun initial(): GameState = states[0]

    fun canGoBack(): Boolean = currentNodeIndex > 0

    fun canGoForward(): Boolean = currentNodeIndex < states.size - 1

    fun size(): Int = states.size

    fun moveList(): List<Move> = currentState().moveHistory

    /** Apply a move from current position, discarding any forward history. */
    fun addMove(move: Move): GameTree {
        val newState = ChessEngine.makeMove(currentState(), move)
        val newStates = states.subList(0, currentNodeIndex + 1).toMutableList()
        newStates.add(newState)
        return GameTree(newStates, newStates.size - 1)
    }

    fun goBack(steps: Int = 1): GameTree {
        return GameTree(states, maxOf(0, currentNodeIndex - steps))
    }

    fun goForward(steps: Int = 1): GameTree {
        return GameTree(states, minOf(states.size - 1, currentNodeIndex + steps))
    }

    fun goTo(index: Int): GameTree {
        return GameTree(states, index.coerceIn(0, states.size - 1))
    }

    companion object {
        fun fromInitial(): GameTree = GameTree(GameState.initial())
    }
}
