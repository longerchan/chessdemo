package com.jnz.chess.domain

sealed class GameResult {
    data object WhiteWins : GameResult()
    data object BlackWins : GameResult()
    data object Draw : GameResult()
    data class InProgress(val checkSide: Color? = null) : GameResult()

    val isGameOver: Boolean get() = this !is InProgress

    fun displayText(): String = when (this) {
        WhiteWins -> "Checkmate! White wins."
        BlackWins -> "Checkmate! Black wins."
        Draw -> "Draw."
        is InProgress -> when (checkSide) {
            Color.WHITE -> "White is in check!"
            Color.BLACK -> "Black is in check!"
            null -> ""
        }
    }

    /** Standard PGN result string. */
    fun pgnResult(): String = when (this) {
        WhiteWins -> "1-0"
        BlackWins -> "0-1"
        Draw -> "1/2-1/2"
        else -> "*"
    }
}
