package com.jnz.chess.domain

import org.junit.Assert.*
import org.junit.Test

class GameResultTest {

    @Test
    fun `WhiteWins isGameOver is true`() {
        assertTrue(GameResult.WhiteWins.isGameOver)
    }

    @Test
    fun `BlackWins isGameOver is true`() {
        assertTrue(GameResult.BlackWins.isGameOver)
    }

    @Test
    fun `Draw isGameOver is true`() {
        assertTrue(GameResult.Draw.isGameOver)
    }

    @Test
    fun `InProgress isGameOver is false`() {
        assertFalse(GameResult.InProgress().isGameOver)
    }

    @Test
    fun `displayText for WhiteWins`() {
        assertEquals("Checkmate! White wins.", GameResult.WhiteWins.displayText())
    }

    @Test
    fun `displayText for BlackWins`() {
        assertEquals("Checkmate! Black wins.", GameResult.BlackWins.displayText())
    }

    @Test
    fun `displayText for Draw`() {
        assertEquals("Draw.", GameResult.Draw.displayText())
    }

    @Test
    fun `displayText for InProgress with white check`() {
        assertEquals("White is in check!", GameResult.InProgress(Color.WHITE).displayText())
    }

    @Test
    fun `displayText for InProgress with black check`() {
        assertEquals("Black is in check!", GameResult.InProgress(Color.BLACK).displayText())
    }

    @Test
    fun `displayText for InProgress without check is empty`() {
        assertEquals("", GameResult.InProgress(null).displayText())
    }

    @Test
    fun `pgnResult for WhiteWins`() {
        assertEquals("1-0", GameResult.WhiteWins.pgnResult())
    }

    @Test
    fun `pgnResult for BlackWins`() {
        assertEquals("0-1", GameResult.BlackWins.pgnResult())
    }

    @Test
    fun `pgnResult for Draw`() {
        assertEquals("1/2-1/2", GameResult.Draw.pgnResult())
    }

    @Test
    fun `pgnResult for InProgress is *`() {
        assertEquals("*", GameResult.InProgress().pgnResult())
    }
}
