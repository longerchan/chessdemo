package com.jnz.chess.domain

import org.junit.Assert.*
import org.junit.Test

class MoveTest {

    @Test
    fun `toString produces UCI notation for normal move`() {
        val move = Move(6, 4, 4, 4) // e2-e4
        assertEquals("e2e4", move.toString())
    }

    @Test
    fun `toString produces UCI notation for non-queen promotion`() {
        val move = Move(1, 4, 0, 4, promotionType = PieceType.KNIGHT) // e7-e8=N
        assertEquals("e7e8=N", move.toString())
    }

    @Test
    fun `toString for queen promotion includes suffix`() {
        val move = Move(1, 4, 0, 4, promotionType = PieceType.QUEEN) // e7-e8=Q
        assertEquals("e7e8=Q", move.toString())
    }

    @Test
    fun `default promotion type is null`() {
        val move = Move(1, 4, 0, 4)
        assertNull(move.promotionType)
    }

    @Test
    fun `isCastling flag is preserved`() {
        val move = Move(7, 4, 7, 6, isCastling = true)
        assertTrue(move.isCastling)
        assertEquals("e1g1", move.toString())
    }

    @Test
    fun `isEnPassant flag is preserved`() {
        val move = Move(4, 4, 3, 5, isEnPassant = true)
        assertTrue(move.isEnPassant)
        assertEquals("e4f5", move.toString())
    }
}
