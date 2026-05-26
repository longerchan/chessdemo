package com.jnz.chess.domain

import org.junit.Assert.*
import org.junit.Test

class GameTreeTest {

    @Test
    fun `fromInitial creates tree at index 0`() {
        val tree = GameTree.fromInitial()
        assertEquals(0, tree.currentNodeIndex)
        assertEquals(1, tree.size())
        // Board should have starting position: white pawns on row 6
        assertNotNull(tree.currentState().board[6][4]) // e2 pawn
    }

    @Test
    fun `initial returns first state regardless of index`() {
        val tree = GameTree.fromInitial()
        assertEquals(tree.currentState(), tree.initial())
    }

    @Test
    fun `addMove appends state and advances index`() {
        val tree = GameTree.fromInitial()
        val move = Move(6, 4, 4, 4) // e2-e4
        val newTree = tree.addMove(move)
        assertEquals(2, newTree.size())
        assertEquals(1, newTree.currentNodeIndex)
        assertEquals(Color.BLACK, newTree.currentState().currentTurn)
    }

    @Test
    fun `addMove discards forward history`() {
        // e2-e4, e7-e5, then from start add e2-e3 (should remove e4 branch)
        val tree = GameTree.fromInitial()
        val t2 = tree.addMove(Move(6, 4, 4, 4)) // e4
        val t3 = t2.addMove(Move(1, 4, 3, 4)) // e5
        assertEquals(3, t3.size())
        assertEquals(2, t3.currentNodeIndex)

        // Go back and play different move
        val back = t3.goBack(1)
        val altTree = back.addMove(Move(1, 4, 2, 4)) // e6
        assertEquals(3, altTree.size())
        // The e5 state is discarded
        assertEquals(Piece(PieceType.PAWN, Color.BLACK), altTree.currentState().board[2][4])
    }

    @Test
    fun `goBack and goForward navigate correctly`() {
        val tree = GameTree.fromInitial()
        val t2 = tree.addMove(Move(6, 4, 4, 4)) // e4
        val t3 = t2.addMove(Move(1, 4, 3, 4)) // e5

        val back1 = t3.goBack()
        assertEquals(1, back1.currentNodeIndex)
        assertEquals(3, back1.size()) // forward history preserved

        val forward1 = back1.goForward()
        assertEquals(2, forward1.currentNodeIndex)
    }

    @Test
    fun `goBack at start stays at start`() {
        val tree = GameTree.fromInitial()
        val atStart = tree.goBack()
        assertEquals(0, atStart.currentNodeIndex)
    }

    @Test
    fun `goForward at end stays at end`() {
        val tree = GameTree.fromInitial()
        val atEnd = tree.goForward()
        assertEquals(0, atEnd.currentNodeIndex)
    }

    @Test
    fun `goTo navigates to exact index`() {
        val tree = GameTree.fromInitial()
        val t2 = tree.addMove(Move(6, 4, 4, 4))
        val t3 = t2.addMove(Move(1, 4, 3, 4))

        val at0 = t3.goTo(0)
        assertEquals(0, at0.currentNodeIndex)
        val at1 = t3.goTo(1)
        assertEquals(1, at1.currentNodeIndex)
    }

    @Test
    fun `goTo clamps out of range indices`() {
        val tree = GameTree.fromInitial()
        val t2 = tree.addMove(Move(6, 4, 4, 4))

        val negative = t2.goTo(-1)
        assertEquals(0, negative.currentNodeIndex)

        val beyond = t2.goTo(99)
        assertEquals(1, beyond.currentNodeIndex)
    }

    @Test
    fun `canGoBack and canGoForward reflect position`() {
        val tree = GameTree.fromInitial()
        val t2 = tree.addMove(Move(6, 4, 4, 4))

        assertFalse(tree.canGoBack())
        assertFalse(tree.canGoForward()) // index 0, size 1: 0 < 0 is false

        assertTrue(t2.canGoBack())
        assertFalse(t2.canGoForward())
    }

    @Test
    fun `moveList returns current state's move history`() {
        val tree = GameTree.fromInitial()
        val t1 = tree.addMove(Move(6, 4, 4, 4)) // e4
        val t2 = t1.addMove(Move(1, 4, 3, 4)) // e5
        assertEquals(2, t2.moveList().size)
        assertEquals("e2e4", t2.moveList()[0].toString())
        assertEquals("e7e5", t2.moveList()[1].toString())
    }

    @Test
    fun `primary constructor works with custom initial state`() {
        val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
        val state = ChessEngine.fenToStateOrNull(fen)!!
        val tree = GameTree(state)
        assertEquals(0, tree.currentNodeIndex)
        assertEquals(state, tree.currentState())
    }
}
