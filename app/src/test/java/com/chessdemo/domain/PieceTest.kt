package com.chessdemo.domain

import org.junit.Assert.*
import org.junit.Test

class PieceTest {

    @Test
    fun `fromFenChar parses white pieces`() {
        assertEquals(Piece(PieceType.KING, Color.WHITE), Piece.fromFenChar('K'))
        assertEquals(Piece(PieceType.QUEEN, Color.WHITE), Piece.fromFenChar('Q'))
        assertEquals(Piece(PieceType.ROOK, Color.WHITE), Piece.fromFenChar('R'))
        assertEquals(Piece(PieceType.BISHOP, Color.WHITE), Piece.fromFenChar('B'))
        assertEquals(Piece(PieceType.KNIGHT, Color.WHITE), Piece.fromFenChar('N'))
        assertEquals(Piece(PieceType.PAWN, Color.WHITE), Piece.fromFenChar('P'))
    }

    @Test
    fun `fromFenChar parses black pieces`() {
        assertEquals(Piece(PieceType.KING, Color.BLACK), Piece.fromFenChar('k'))
        assertEquals(Piece(PieceType.QUEEN, Color.BLACK), Piece.fromFenChar('q'))
        assertEquals(Piece(PieceType.ROOK, Color.BLACK), Piece.fromFenChar('r'))
        assertEquals(Piece(PieceType.BISHOP, Color.BLACK), Piece.fromFenChar('b'))
        assertEquals(Piece(PieceType.KNIGHT, Color.BLACK), Piece.fromFenChar('n'))
        assertEquals(Piece(PieceType.PAWN, Color.BLACK), Piece.fromFenChar('p'))
    }

    @Test
    fun `fromFenChar returns null for empty square`() {
        assertNull(Piece.fromFenChar('1'))
        assertNull(Piece.fromFenChar('/'))
        assertNull(Piece.fromFenChar(' '))
    }

    @Test
    fun `unicode returns correct symbols for white pieces`() {
        assertEquals("♔", Piece(PieceType.KING, Color.WHITE).unicode())
        assertEquals("♕", Piece(PieceType.QUEEN, Color.WHITE).unicode())
        assertEquals("♖", Piece(PieceType.ROOK, Color.WHITE).unicode())
        assertEquals("♗", Piece(PieceType.BISHOP, Color.WHITE).unicode())
        assertEquals("♘", Piece(PieceType.KNIGHT, Color.WHITE).unicode())
        assertEquals("♙", Piece(PieceType.PAWN, Color.WHITE).unicode())
    }

    @Test
    fun `unicode returns correct symbols for black pieces`() {
        assertEquals("♚", Piece(PieceType.KING, Color.BLACK).unicode())
        assertEquals("♛", Piece(PieceType.QUEEN, Color.BLACK).unicode())
        assertEquals("♜", Piece(PieceType.ROOK, Color.BLACK).unicode())
        assertEquals("♝", Piece(PieceType.BISHOP, Color.BLACK).unicode())
        assertEquals("♞", Piece(PieceType.KNIGHT, Color.BLACK).unicode())
        assertEquals("♟", Piece(PieceType.PAWN, Color.BLACK).unicode())
    }

    @Test
    fun `piece type symbols are correct`() {
        assertEquals('K', PieceType.KING.symbol)
        assertEquals('Q', PieceType.QUEEN.symbol)
        assertEquals('R', PieceType.ROOK.symbol)
        assertEquals('B', PieceType.BISHOP.symbol)
        assertEquals('N', PieceType.KNIGHT.symbol)
        assertEquals('P', PieceType.PAWN.symbol)
    }
}
