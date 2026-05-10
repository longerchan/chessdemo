package com.chessdemo.domain

object ChessEngine {

    /** Parse a FEN string into a GameState. Returns null on parse failure. */
    fun fenToState(fen: String): GameState? {
        try {
            val parts = fen.trim().split(" ")
            if (parts.size < 4) return null

            val boardPart = parts[0]
            val turnPart = parts[1]
            val castlingPart = parts[2]
            val epPart = parts[3]
            val halfMove = parts.getOrNull(4)?.toIntOrNull() ?: 0
            val fullMove = parts.getOrNull(5)?.toIntOrNull() ?: 1

            val newBoard = Array(8) { arrayOfNulls<Piece?>(8) }
            var row = 0
            var col = 0
            for (ch in boardPart) {
                if (ch == '/') {
                    row++
                    col = 0
                    if (row >= 8) break
                } else if (ch in '1'..'8') {
                    col += ch.digitToInt()
                } else {
                    val piece = Piece.fromFenChar(ch) ?: return null
                    newBoard[row][col] = piece
                    col++
                }
            }

            val turn = if (turnPart == "w") Color.WHITE else Color.BLACK
            val castling = CastlingRights(
                whiteKingSide = castlingPart.contains('K'),
                whiteQueenSide = castlingPart.contains('Q'),
                blackKingSide = castlingPart.contains('k'),
                blackQueenSide = castlingPart.contains('q'),
            )
            val enPassant = if (epPart == "-") null else {
                val file = epPart[0] - 'a'
                val rank = epPart[1].digitToInt()
                (7 - (rank - 1)) to file
            }

            val state = GameState(
                board = newBoard,
                currentTurn = turn,
                castlingRights = castling,
                enPassantTarget = enPassant,
                moveHistory = emptyList(),
                halfMoveClock = halfMove,
                fullMoveNumber = fullMove,
            )
            return state.copyWith(newPositionHistory = listOf(state.positionKey()))
        } catch (e: Exception) {
            return null
        }
    }

    private val knightOffsets = listOf(
        -2 to -1, -2 to 1, -1 to -2, -1 to 2,
        1 to -2, 1 to 2, 2 to -1, 2 to 1
    )

    private val bishopDirs = listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)
    private val rookDirs = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
    private val queenDirs = bishopDirs + rookDirs

    fun getLegalMoves(state: GameState, color: Color? = null): List<Move> {
        val c = color ?: state.currentTurn
        return generatePseudoLegalMoves(state, c).filter { isLegal(state, it, c) }
    }

    fun makeMove(state: GameState, move: Move): GameState {
        val piece = state.board[move.fromRow][move.fromCol] ?: return state
        val newBoard = state.board.map { it.copyOf() }.toTypedArray()
        var newCastling = state.castlingRights
        var clearEnPassant = false
        var newEnPassant: Pair<Int, Int>? = null
        var newHalfMove = state.halfMoveClock
        var isCapture = false

        newBoard[move.toRow][move.toCol] = piece
        newBoard[move.fromRow][move.fromCol] = null

        if (move.isEnPassant) {
            newBoard[move.fromRow][move.toCol] = null
            newHalfMove = 0
            clearEnPassant = true
            isCapture = true
        } else if (piece.type == PieceType.PAWN) {
            if (kotlin.math.abs(move.toRow - move.fromRow) == 2) {
                newEnPassant = ((move.fromRow + move.toRow) / 2) to move.fromCol
            } else {
                clearEnPassant = true
            }
            newHalfMove = 0
        } else {
            clearEnPassant = true
            if (state.board[move.toRow][move.toCol] != null) {
                newHalfMove = 0
                isCapture = true
            }
        }

        if (move.isCastling) {
            val row = move.fromRow
            if (move.toCol == 6) {
                newBoard[row][5] = newBoard[row][7]
                newBoard[row][7] = null
            } else {
                newBoard[row][3] = newBoard[row][0]
                newBoard[row][0] = null
            }
        }

        if (piece.type == PieceType.PAWN && (move.toRow == 0 || move.toRow == 7)) {
            newBoard[move.toRow][move.toCol] = Piece(move.promotionType, piece.color)
        }

        if (piece.type == PieceType.KING) {
            newCastling = if (piece.color == Color.WHITE)
                newCastling.copy(whiteKingSide = false, whiteQueenSide = false)
            else
                newCastling.copy(blackKingSide = false, blackQueenSide = false)
        }
        if (piece.type == PieceType.ROOK) {
            newCastling = when (move.fromRow to move.fromCol) {
                7 to 0 -> newCastling.copy(whiteQueenSide = false)
                7 to 7 -> newCastling.copy(whiteKingSide = false)
                0 to 0 -> newCastling.copy(blackQueenSide = false)
                0 to 7 -> newCastling.copy(blackKingSide = false)
                else -> newCastling
            }
        }
        if (move.toRow == 7 && move.toCol == 0) newCastling = newCastling.copy(whiteQueenSide = false)
        if (move.toRow == 7 && move.toCol == 7) newCastling = newCastling.copy(whiteKingSide = false)
        if (move.toRow == 0 && move.toCol == 0) newCastling = newCastling.copy(blackQueenSide = false)
        if (move.toRow == 0 && move.toCol == 7) newCastling = newCastling.copy(blackKingSide = false)

        if (piece.type != PieceType.PAWN && !isCapture) newHalfMove++

        val nextTurn = if (state.currentTurn == Color.WHITE) Color.BLACK else Color.WHITE
        var newFullMove = state.fullMoveNumber
        if (state.currentTurn == Color.BLACK) newFullMove++

        val newState = state.copyWith(
            newBoard = newBoard,
            newTurn = nextTurn,
            newCastling = newCastling,
            clearEnPassant = clearEnPassant,
            newEnPassant = newEnPassant,
            newHistory = state.moveHistory + move,
            newHalfMove = newHalfMove,
            newFullMove = newFullMove
        )

        val posKey = newState.positionKey()
        val updatedPositionHistory = newState.positionHistory + posKey
        val newStateWithHistory = newState.copyWith(newPositionHistory = updatedPositionHistory)

        val legalMoves = getLegalMoves(newStateWithHistory, nextTurn)
        val kingPos = newStateWithHistory.findKing(nextTurn)!!
        val inCheck = isSquareAttacked(newStateWithHistory, kingPos.first, kingPos.second,
            if (nextTurn == Color.WHITE) Color.BLACK else Color.WHITE)

        val repeated = countOccurrences(updatedPositionHistory, posKey)

        return if (legalMoves.isEmpty()) {
            if (inCheck) {
                val winner = if (nextTurn == Color.WHITE) "Black" else "White"
                newStateWithHistory.copyWith(newGameOver = true, newResult = "Checkmate! $winner wins.")
            } else {
                newStateWithHistory.copyWith(newGameOver = true, newResult = "Stalemate! Draw.")
            }
        } else if (newStateWithHistory.halfMoveClock >= 100) {
            newStateWithHistory.copyWith(newGameOver = true, newResult = "Draw by 50-move rule.")
        } else if (repeated >= 3) {
            newStateWithHistory.copyWith(newGameOver = true, newResult = "Draw by threefold repetition.")
        } else if (isInsufficientMaterial(newStateWithHistory)) {
            newStateWithHistory.copyWith(newGameOver = true, newResult = "Draw by insufficient material.")
        } else {
            if (inCheck) {
                val side = if (nextTurn == Color.WHITE) "White" else "Black"
                newStateWithHistory.copyWith(newResult = "$side is in check!")
            } else newStateWithHistory
        }
    }

    fun isLegal(state: GameState, move: Move, color: Color): Boolean {
        val piece = state.board[move.fromRow][move.fromCol]
        if (piece == null || piece.color != color) return false
        val newBoard = state.board.map { it.copyOf() }.toTypedArray()
        newBoard[move.toRow][move.toCol] = piece
        newBoard[move.fromRow][move.fromCol] = null
        if (move.isEnPassant) newBoard[move.fromRow][move.toCol] = null
        if (move.isCastling) {
            val row = move.fromRow
            if (move.toCol == 6) { newBoard[row][5] = newBoard[row][7]; newBoard[row][7] = null }
            else { newBoard[row][3] = newBoard[row][0]; newBoard[row][0] = null }
        }
        val oppColor = if (color == Color.WHITE) Color.BLACK else Color.WHITE
        val kingPos = findKingInBoard(newBoard, color) ?: return false
        return !isSquareAttackedInBoard(newBoard, kingPos.first, kingPos.second, oppColor)
    }

    private fun findKingInBoard(board: Array<Array<Piece?>>, color: Color): Pair<Int, Int>? {
        for (r in 0..7) for (c in 0..7) {
            val p = board[r][c] ?: continue
            if (p.type == PieceType.KING && p.color == color) return r to c
        }
        return null
    }

    fun isSquareAttacked(state: GameState, row: Int, col: Int, byColor: Color): Boolean =
        isSquareAttackedInBoard(state.board, row, col, byColor)

    private fun isSquareAttackedInBoard(board: Array<Array<Piece?>>, row: Int, col: Int, byColor: Color): Boolean {
        for ((dr, dc) in knightOffsets) {
            val r = row + dr; val c = col + dc
            if (r in 0..7 && c in 0..7) {
                val p = board[r][c]
                if (p != null && p.color == byColor && p.type == PieceType.KNIGHT) return true
            }
        }

        val pawnDir = if (byColor == Color.WHITE) 1 else -1
        for (dc in listOf(-1, 1)) {
            val r = row + pawnDir; val c = col + dc
            if (r in 0..7 && c in 0..7) {
                val p = board[r][c]
                if (p != null && p.color == byColor && p.type == PieceType.PAWN) return true
            }
        }

        for ((dr, dc) in bishopDirs) {
            var r = row + dr; var c = col + dc
            while (r in 0..7 && c in 0..7) {
                val p = board[r][c]
                if (p != null) {
                    if (p.color == byColor && (p.type == PieceType.BISHOP || p.type == PieceType.QUEEN)) return true
                    break
                }
                r += dr; c += dc
            }
        }

        for ((dr, dc) in rookDirs) {
            var r = row + dr; var c = col + dc
            while (r in 0..7 && c in 0..7) {
                val p = board[r][c]
                if (p != null) {
                    if (p.color == byColor && (p.type == PieceType.ROOK || p.type == PieceType.QUEEN)) return true
                    break
                }
                r += dr; c += dc
            }
        }

        for (dr in -1..1) for (dc in -1..1) {
            if (dr == 0 && dc == 0) continue
            val r = row + dr; val c = col + dc
            if (r in 0..7 && c in 0..7) {
                val p = board[r][c]
                if (p != null && p.color == byColor && p.type == PieceType.KING) return true
            }
        }

        return false
    }

    private fun generatePseudoLegalMoves(state: GameState, color: Color): List<Move> {
        val moves = mutableListOf<Move>()
        for (r in 0..7) for (c in 0..7) {
            val piece = state.board[r][c] ?: continue
            if (piece.color != color) continue
            when (piece.type) {
                PieceType.PAWN -> generatePawnMoves(state, r, c, piece, moves)
                PieceType.KNIGHT -> generateKnightMoves(state, r, c, piece, moves)
                PieceType.BISHOP -> generateSlidingMoves(state, r, c, piece, bishopDirs, moves)
                PieceType.ROOK -> generateSlidingMoves(state, r, c, piece, rookDirs, moves)
                PieceType.QUEEN -> generateSlidingMoves(state, r, c, piece, queenDirs, moves)
                PieceType.KING -> generateKingMoves(state, r, c, piece, moves)
            }
        }
        return moves
    }

    private fun generatePawnMoves(state: GameState, row: Int, col: Int, piece: Piece, moves: MutableList<Move>) {
        val dir = if (piece.color == Color.WHITE) -1 else 1
        val startRow = if (piece.color == Color.WHITE) 6 else 1
        val promoRow = if (piece.color == Color.WHITE) 0 else 7

        val oneForward = row + dir
        if (oneForward in 0..7 && state.board[oneForward][col] == null) {
            if (oneForward == promoRow) {
                for (promo in listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)) {
                    moves.add(Move(row, col, oneForward, col, promotionType = promo))
                }
            } else {
                moves.add(Move(row, col, oneForward, col))
                val twoForward = row + 2 * dir
                if (row == startRow && state.board[twoForward][col] == null) {
                    moves.add(Move(row, col, twoForward, col))
                }
            }
        }

        for (dc in listOf(-1, 1)) {
            val nc = col + dc
            if (oneForward !in 0..7 || nc !in 0..7) continue
            val target = state.board[oneForward][nc]
            if (target != null && target.color != piece.color) {
                if (oneForward == promoRow) {
                    for (promo in listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT)) {
                        moves.add(Move(row, col, oneForward, nc, promotionType = promo))
                    }
                } else {
                    moves.add(Move(row, col, oneForward, nc))
                }
            }
            if (state.enPassantTarget == (oneForward to nc)) {
                moves.add(Move(row, col, oneForward, nc, isEnPassant = true))
            }
        }
    }

    private fun generateKnightMoves(state: GameState, row: Int, col: Int, piece: Piece, moves: MutableList<Move>) {
        for ((dr, dc) in knightOffsets) {
            val r = row + dr; val c = col + dc
            if (r in 0..7 && c in 0..7) {
                val target = state.board[r][c]
                if (target == null || target.color != piece.color) {
                    moves.add(Move(row, col, r, c))
                }
            }
        }
    }

    private fun generateSlidingMoves(
        state: GameState, row: Int, col: Int, piece: Piece,
        directions: List<Pair<Int, Int>>, moves: MutableList<Move>
    ) {
        for ((dr, dc) in directions) {
            var r = row + dr; var c = col + dc
            while (r in 0..7 && c in 0..7) {
                val target = state.board[r][c]
                if (target != null) {
                    if (target.color != piece.color) moves.add(Move(row, col, r, c))
                    break
                }
                moves.add(Move(row, col, r, c))
                r += dr; c += dc
            }
        }
    }

    private fun generateKingMoves(state: GameState, row: Int, col: Int, piece: Piece, moves: MutableList<Move>) {
        val oppColor = if (piece.color == Color.WHITE) Color.BLACK else Color.WHITE

        for (dr in -1..1) for (dc in -1..1) {
            if (dr == 0 && dc == 0) continue
            val r = row + dr; val c = col + dc
            if (r in 0..7 && c in 0..7) {
                val target = state.board[r][c]
                if (target == null || target.color != piece.color) {
                    moves.add(Move(row, col, r, c))
                }
            }
        }

        val homeRow = if (piece.color == Color.WHITE) 7 else 0
        if (row == homeRow && col == 4 && !isSquareAttacked(state, row, col, oppColor)) {
            val rights = state.castlingRights
            val kingSide = if (piece.color == Color.WHITE) rights.whiteKingSide else rights.blackKingSide
            val queenSide = if (piece.color == Color.WHITE) rights.whiteQueenSide else rights.blackQueenSide

            if (kingSide && state.board[homeRow][5] == null && state.board[homeRow][6] == null
                && !isSquareAttacked(state, homeRow, 5, oppColor)
                && !isSquareAttacked(state, homeRow, 6, oppColor)) {
                moves.add(Move(row, col, homeRow, 6, isCastling = true))
            }
            if (queenSide && state.board[homeRow][3] == null && state.board[homeRow][2] == null && state.board[homeRow][1] == null
                && !isSquareAttacked(state, homeRow, 3, oppColor)
                && !isSquareAttacked(state, homeRow, 2, oppColor)) {
                moves.add(Move(row, col, homeRow, 2, isCastling = true))
            }
        }
    }

    private fun countOccurrences(positionHistory: List<String>, key: String): Int {
        var count = 0
        for (pos in positionHistory) {
            if (pos == key) count++
        }
        return count
    }

    private fun isInsufficientMaterial(state: GameState): Boolean {
        val pieces = mutableListOf<Piece>()
        for (r in 0..7) for (c in 0..7) {
            state.board[r][c]?.let { pieces.add(it) }
        }
        val whitePieces = pieces.filter { it.color == Color.WHITE }
        val blackPieces = pieces.filter { it.color == Color.BLACK }

        fun isMinor(p: Piece) = p.type in listOf(PieceType.BISHOP, PieceType.KNIGHT)

        return when {
            whitePieces.size == 1 && blackPieces.size == 1 -> true
            whitePieces.size == 1 && blackPieces.size == 2 && blackPieces.any { isMinor(it) } -> true
            blackPieces.size == 1 && whitePieces.size == 2 && whitePieces.any { isMinor(it) } -> true
            whitePieces.size == 2 && whitePieces.count { it.type == PieceType.BISHOP } == 2
                && blackPieces.size == 1 -> true
            blackPieces.size == 2 && blackPieces.count { it.type == PieceType.BISHOP } == 2
                && whitePieces.size == 1 -> true
            else -> false
        }
    }
}
