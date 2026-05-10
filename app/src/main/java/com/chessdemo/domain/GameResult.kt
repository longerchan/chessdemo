package com.chessdemo.domain

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
}
