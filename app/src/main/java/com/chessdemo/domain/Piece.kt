package com.chessdemo.domain

enum class Color { WHITE, BLACK }

enum class PieceType(val symbol: Char) {
    KING('K'), QUEEN('Q'), ROOK('R'), BISHOP('B'), KNIGHT('N'), PAWN('P');
}

data class Piece(val type: PieceType, val color: Color) {
    fun unicode(): String = when (this) {
        Piece(PieceType.KING, Color.WHITE) -> "♔"
        Piece(PieceType.QUEEN, Color.WHITE) -> "♕"
        Piece(PieceType.ROOK, Color.WHITE) -> "♖"
        Piece(PieceType.BISHOP, Color.WHITE) -> "♗"
        Piece(PieceType.KNIGHT, Color.WHITE) -> "♘"
        Piece(PieceType.PAWN, Color.WHITE) -> "♙"
        Piece(PieceType.KING, Color.BLACK) -> "♚"
        Piece(PieceType.QUEEN, Color.BLACK) -> "♛"
        Piece(PieceType.ROOK, Color.BLACK) -> "♜"
        Piece(PieceType.BISHOP, Color.BLACK) -> "♝"
        Piece(PieceType.KNIGHT, Color.BLACK) -> "♞"
        Piece(PieceType.PAWN, Color.BLACK) -> "♟"
        else -> "?"
    }

    companion object {
        /** Parse a single FEN character like 'P', 'p', 'N', 'n' etc. */
        fun fromFenChar(ch: Char): Piece? {
            val (color, type) = when (ch) {
                'K' -> Color.WHITE to PieceType.KING
                'Q' -> Color.WHITE to PieceType.QUEEN
                'R' -> Color.WHITE to PieceType.ROOK
                'B' -> Color.WHITE to PieceType.BISHOP
                'N' -> Color.WHITE to PieceType.KNIGHT
                'P' -> Color.WHITE to PieceType.PAWN
                'k' -> Color.BLACK to PieceType.KING
                'q' -> Color.BLACK to PieceType.QUEEN
                'r' -> Color.BLACK to PieceType.ROOK
                'b' -> Color.BLACK to PieceType.BISHOP
                'n' -> Color.BLACK to PieceType.KNIGHT
                'p' -> Color.BLACK to PieceType.PAWN
                else -> return null
            }
            return Piece(type, color)
        }

        fun fromChar(c: Char): Piece? {
            val (color, type) = when (c) {
                'K' -> Color.WHITE to PieceType.KING
                'Q' -> Color.WHITE to PieceType.QUEEN
                'R' -> Color.WHITE to PieceType.ROOK
                'B' -> Color.WHITE to PieceType.BISHOP
                'N' -> Color.WHITE to PieceType.KNIGHT
                'P' -> Color.WHITE to PieceType.PAWN
                'k' -> Color.BLACK to PieceType.KING
                'q' -> Color.BLACK to PieceType.QUEEN
                'r' -> Color.BLACK to PieceType.ROOK
                'b' -> Color.BLACK to PieceType.BISHOP
                'n' -> Color.BLACK to PieceType.KNIGHT
                'p' -> Color.BLACK to PieceType.PAWN
                else -> return null
            }
            return Piece(type, color)
        }
    }
}
