package com.jnz.chess.domain

import org.junit.Assert.*
import org.junit.Test

class GameStateTest {

    @Test
    fun `positionKey is unique for different positions`() {
        val state1 = GameState.initial()
        val move = Move(6, 4, 4, 4) // e2-e4
        val state2 = ChessEngine.makeMove(state1, move)
        assertNotEquals(state1.positionKey(), state2.positionKey())
    }

    @Test
    fun `copyWith clearEnPassant clears target`() {
        val state = GameState.initial()
        val withEp = state.copyWith(newEnPassant = 3 to 4) // e3
        assertNotNull(withEp.enPassantTarget)
        val cleared = withEp.copyWith(clearEnPassant = true)
        assertNull(cleared.enPassantTarget)
    }

    @Test
    fun `copyWith newEnPassant sets target`() {
        val state = GameState.initial()
        val updated = state.copyWith(newEnPassant = 3 to 4)
        assertEquals(3 to 4, updated.enPassantTarget)
    }

    @Test
    fun `copyWith keeps enPassant when neither clearEnPassant nor newEnPassant provided`() {
        val state = GameState.initial().copyWith(newEnPassant = 3 to 4)
        val updated = state.copyWith(newTurn = Color.BLACK)
        assertEquals(3 to 4, updated.enPassantTarget)
    }

    @Test
    fun `copy includes positionHistory`() {
        val state = GameState.initial()
        val copied = state.copy()
        assertEquals(state.positionHistory, copied.positionHistory)
        assertNotSame(state.board, copied.board)
    }

    @Test
    fun `findKing returns correct position`() {
        val state = GameState.initial()
        val whiteKing = state.findKing(Color.WHITE)
        assertEquals(7 to 4, whiteKing) // e1
        val blackKing = state.findKing(Color.BLACK)
        assertEquals(0 to 4, blackKing) // e8
    }

    @Test
    fun `positionHistory is initialized with initial position key`() {
        val state = GameState.initial()
        assertEquals(1, state.positionHistory.size)
        assertEquals(state.positionKey(), state.positionHistory[0])
    }
}
