package com.chessdemo.domain

data class CastlingRights(
    val whiteKingSide: Boolean = true,
    val whiteQueenSide: Boolean = true,
    val blackKingSide: Boolean = true,
    val blackQueenSide: Boolean = true,
)

class GameState(
    val board: Array<Array<Piece?>>,
    val currentTurn: Color,
    val castlingRights: CastlingRights,
    val enPassantTarget: Pair<Int, Int>?,
    val moveHistory: List<Move>,
    val halfMoveClock: Int = 0,
    val fullMoveNumber: Int = 1,
    val gameOver: Boolean = false,
    val result: String? = null,
    val positionHistory: List<String> = emptyList(),
) {
    fun copy(): GameState {
        val newBoard = board.map { it.copyOf() }.toTypedArray()
        return GameState(
            newBoard, currentTurn, castlingRights, enPassantTarget,
            moveHistory, halfMoveClock, fullMoveNumber, gameOver, result, positionHistory
        )
    }

    fun copyWith(
        newBoard: Array<Array<Piece?>>? = null,
        newTurn: Color? = null,
        newCastling: CastlingRights? = null,
        clearEnPassant: Boolean = false,
        newEnPassant: Pair<Int, Int>? = null,
        newHistory: List<Move>? = null,
        newHalfMove: Int? = null,
        newFullMove: Int? = null,
        newGameOver: Boolean? = null,
        newResult: String? = null,
        newPositionHistory: List<String>? = null,
    ): GameState {
        val resolvedEnPassant = when {
            clearEnPassant -> null
            newEnPassant != null -> newEnPassant
            else -> enPassantTarget
        }
        return GameState(
            newBoard ?: board.map { it.copyOf() }.toTypedArray(),
            newTurn ?: currentTurn,
            newCastling ?: castlingRights,
            resolvedEnPassant,
            newHistory ?: moveHistory,
            newHalfMove ?: halfMoveClock,
            newFullMove ?: fullMoveNumber,
            newGameOver ?: gameOver,
            newResult ?: result,
            newPositionHistory ?: positionHistory,
        )
    }

    /** Generate a compact position key for repetition detection. */
    fun positionKey(): String {
        val sb = StringBuilder(64)
        for (r in 0..7) for (c in 0..7) {
            val p = board[r][c]
            if (p != null) {
                sb.append(
                    if (p.color == Color.WHITE) p.type.symbol
                    else p.type.symbol.lowercaseChar()
                )
            } else {
                sb.append('.')
            }
        }
        sb.append(if (currentTurn == Color.WHITE) 'w' else 'b')
        sb.append(
            if (castlingRights.whiteKingSide) 'K' else '-'
        ).append(
            if (castlingRights.whiteQueenSide) 'Q' else '-'
        ).append(
            if (castlingRights.blackKingSide) 'k' else '-'
        ).append(
            if (castlingRights.blackQueenSide) 'q' else '-'
        )
        enPassantTarget?.let { (r, c) ->
            sb.append("${('a' + c)}${8 - r}")
        } ?: sb.append('-')
        return sb.toString()
    }

    fun getPiece(row: Int, col: Int): Piece? =
        if (row in 0..7 && col in 0..7) board[row][col] else null

    fun findKing(color: Color): Pair<Int, Int>? {
        for (r in 0..7) for (c in 0..7) {
            val p = board[r][c] ?: continue
            if (p.type == PieceType.KING && p.color == color) return r to c
        }
        return null
    }

    companion object {
        fun initial(): GameState {
            val b = Array(8) { arrayOfNulls<Piece>(8) }
            val backRank = listOf(
                PieceType.ROOK, PieceType.KNIGHT, PieceType.BISHOP, PieceType.QUEEN,
                PieceType.KING, PieceType.BISHOP, PieceType.KNIGHT, PieceType.ROOK
            )
            for (c in 0..7) {
                b[0][c] = Piece(backRank[c], Color.BLACK)
                b[1][c] = Piece(PieceType.PAWN, Color.BLACK)
                b[6][c] = Piece(PieceType.PAWN, Color.WHITE)
                b[7][c] = Piece(backRank[c], Color.WHITE)
            }
            val init = GameState(
                board = b,
                currentTurn = Color.WHITE,
                castlingRights = CastlingRights(),
                enPassantTarget = null,
                moveHistory = emptyList()
            )
            return init.copyWith(newPositionHistory = listOf(init.positionKey()))
        }
    }
}
