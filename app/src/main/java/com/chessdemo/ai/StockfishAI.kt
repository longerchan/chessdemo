package com.chessdemo.ai

import com.chessdemo.domain.Color
import com.chessdemo.domain.GameState
import com.chessdemo.domain.Move
import com.chessdemo.domain.Piece
import com.chessdemo.domain.PieceType

object StockfishAI {

    enum class Difficulty(val label: String, val depth: Int, val moveTime: Int) {
        BEGINNER("初级", 6, 1000),
        EASY("简单", 10, 3000),
        MEDIUM("中级", 15, 5000),
        HARD("困难", 20, 8000),
        GRANDMASTER("大师", 28, 15000),
    }

    @Volatile var difficulty: Difficulty = Difficulty.MEDIUM

    private const val TAG = "StockfishAI"

    private var engineInitialized = false

    // Simple clock: tracks remaining time per side (ms)
    // Default: 10 min + 5s increment. Reset by calling resetClocks().
    @Volatile private var whiteTimeMs: Long = 10 * 60 * 1000L
    @Volatile private var blackTimeMs: Long = 10 * 60 * 1000L
    @Volatile private var incrementMs: Long = 5000L

    fun resetClocks() {
        whiteTimeMs = 10 * 60 * 1000L
        blackTimeMs = 10 * 60 * 1000L
        incrementMs = 5000L
    }

    fun setClocks(whiteMs: Long, blackMs: Long, incMs: Long = 5000L) {
        whiteTimeMs = whiteMs
        blackTimeMs = blackMs
        incrementMs = incMs
    }

    /** Deduct time from the side that just moved, add increment. */
    fun recordMove(side: Color, elapsedMs: Long) {
        val inc = incrementMs
        if (side == Color.WHITE) {
            whiteTimeMs = maxOf(0L, whiteTimeMs - elapsedMs + inc)
        } else {
            blackTimeMs = maxOf(0L, blackTimeMs - elapsedMs + inc)
        }
    }

    private fun ensureEngineInitialized() {
        if (!engineInitialized) {
            StockfishEngine.initialize()
            setEloForDifficulty(difficulty)
            engineInitialized = true
        }
    }

    fun loadBookFromAssets(context: android.content.Context) {
        if (!PolyglotBook.isLoaded()) {
            PolyglotBook.loadFromAssets(context)
        }
    }

    fun isBookLoaded(): Boolean = PolyglotBook.isLoaded()

    private fun eloForDifficulty(diff: Difficulty): Int = when (diff) {
        Difficulty.BEGINNER -> 800
        Difficulty.EASY -> 1200
        Difficulty.MEDIUM -> 1600
        Difficulty.HARD -> 2000
        Difficulty.GRANDMASTER -> 2800
    }

    fun setEloForDifficulty(diff: Difficulty) {
        val elo = eloForDifficulty(diff)
        StockfishEngine.setOption("UCI_LimitStrength", "true")
        StockfishEngine.setOption("UCI_Elo", elo.toString())
    }

    fun findBestMove(state: GameState): Move? {
        return findBestMoveFromFen(stateToFen(state), state)
    }

    fun findBestMoveFromFen(fen: String, state: GameState? = null): Move? {
        ensureEngineInitialized()
        // Check Polyglot external book first
        if (PolyglotBook.isLoaded()) {
            val bookMoves = PolyglotBook.lookupByFen(fen)
            if (bookMoves.isNotEmpty()) {
                val legalMoves = if (state != null)
                    com.chessdemo.domain.ChessEngine.getLegalMoves(state, state.currentTurn)
                else emptyList()
                val selected = weightedRandom(bookMoves)
                val move = uciToMove(selected.uci)
                if (move != null && (legalMoves.isEmpty() || legalMoves.any {
                    it.fromRow == move.fromRow && it.fromCol == move.fromCol &&
                    it.toRow == move.toRow && it.toCol == move.toCol
                })) {
                    android.util.Log.d(TAG, "findBestMove: POLYGLOT BOOK=$selected.uci")
                    return move
                }
            }
        }
        // Fallback to built-in opening book
        if (state != null) {
            val bookMove = OpeningBook.selectMove(state)
            if (bookMove != null) {
                val uci = "${("abcdefgh"[bookMove.fromCol])}${8 - bookMove.fromRow}${("abcdefgh"[bookMove.toCol])}${8 - bookMove.toRow}"
                android.util.Log.d(TAG, "findBestMove: BOOK MOVE=$uci moveNum=${state.fullMoveNumber}")
                return bookMove
            }
        }

        val depth = 0 // Don't limit by depth when using movetime
        val moveTime = difficulty.moveTime
        val wTime = whiteTimeMs.toInt()
        val bTime = blackTimeMs.toInt()
        val inc = incrementMs.toInt()
        android.util.Log.d(TAG, "findBestMoveFromFen: fen=$fen moveTime=$moveTime wTime=$wTime bTime=$bTime inc=$inc")
        val bestMoveUci = StockfishEngine.findBestMoveTimed(
            fen = fen,
            moveHistoryUci = emptyList(),
            depth = depth,
            wTime = wTime, bTime = bTime,
            wInc = inc, bInc = inc,
            movestogo = 0,
            movetime = moveTime,
        )
        android.util.Log.d(TAG, "findBestMoveFromFen: result=$bestMoveUci")

        return bestMoveUci?.let { uciToMove(it) }
    }

    fun getLatestInfo(): String {
        ensureEngineInitialized()
        return StockfishEngine.getLatestInfo()
    }

    fun startAnalysis(state: GameState, multiPV: Int = 3, depth: Int = 30): Boolean {
        ensureEngineInitialized()
        val fen = stateToFenInternal(state)
        return StockfishEngine.startAnalysis(fen, multiPV, depth)
    }

    fun stopAnalysis() {
        StockfishEngine.stopAnalysis()
    }

    fun shutdown() {
        if (engineInitialized) {
            StockfishEngine.shutdown()
        }
    }

    private fun stateToFen(state: GameState): String = stateToFenInternal(state)

    fun stateToFenPublic(state: GameState): String = stateToFenInternal(state)

    private fun stateToFenInternal(state: GameState): String {
        val sb = StringBuilder()

        for (row in 0..7) {
            var emptyCount = 0
            for (col in 0..7) {
                val piece = state.board[row][col]
                if (piece == null) {
                    emptyCount++
                } else {
                    if (emptyCount > 0) {
                        sb.append(emptyCount)
                        emptyCount = 0
                    }
                    sb.append(pieceToFenChar(piece))
                }
            }
            if (emptyCount > 0) sb.append(emptyCount)
            if (row < 7) sb.append('/')
        }

        sb.append(' ')
        sb.append(if (state.currentTurn == Color.WHITE) 'w' else 'b')

        sb.append(' ')
        val castling = buildCastling(state)
        sb.append(if (castling.isEmpty()) "-" else castling)

        sb.append(' ')
        sb.append(state.enPassantTarget?.let { (row, col) ->
            "${('a' + col)}${8 - row}"
        } ?: "-")

        sb.append(' ')
        sb.append(state.halfMoveClock)

        sb.append(' ')
        sb.append(state.fullMoveNumber)

        return sb.toString()
    }

    private fun pieceToFenChar(piece: Piece): Char {
        val c = when (piece.type) {
            PieceType.KING -> 'k'
            PieceType.QUEEN -> 'q'
            PieceType.ROOK -> 'r'
            PieceType.BISHOP -> 'b'
            PieceType.KNIGHT -> 'n'
            PieceType.PAWN -> 'p'
        }
        return if (piece.color == Color.WHITE) c.uppercaseChar() else c
    }

    private fun buildCastling(state: GameState): String {
        val sb = StringBuilder()
        val rights = state.castlingRights
        if (rights.whiteKingSide) sb.append('K')
        if (rights.whiteQueenSide) sb.append('Q')
        if (rights.blackKingSide) sb.append('k')
        if (rights.blackQueenSide) sb.append('q')
        return sb.toString()
    }

    private fun moveToUci(move: Move): String {
        val files = "abcdefgh"
        val fromRank = 8 - move.fromRow
        val toRank = 8 - move.toRow
        val from = "${files[move.fromCol]}$fromRank"
        val to = "${files[move.toCol]}$toRank"
        // Only include promotion letter for actual pawn promotions (reaching rank 1 or 8)
        val promo = if (toRank == 1 || toRank == 8) {
            when (move.promotionType) {
                PieceType.QUEEN -> "q"
                PieceType.ROOK -> "r"
                PieceType.BISHOP -> "b"
                PieceType.KNIGHT -> "n"
                else -> "q"
            }
        } else {
            ""
        }
        return "$from$to$promo"
    }

    private fun uciToMove(uci: String): Move? {
        if (uci.length < 4) return null

        val fromCol = uci[0] - 'a'
        val fromRank = uci[1] - '1'
        val toCol = uci[2] - 'a'
        val toRank = uci[3] - '1'

        val fromRow = 7 - fromRank
        val toRow = 7 - toRank

        if (fromCol !in 0..7 || toCol !in 0..7 || fromRow !in 0..7 || toRow !in 0..7) return null

        val promotionType = if (uci.length >= 5) {
            when (uci[4]) {
                'q' -> PieceType.QUEEN
                'r' -> PieceType.ROOK
                'b' -> PieceType.BISHOP
                'n' -> PieceType.KNIGHT
                else -> PieceType.QUEEN
            }
        } else {
            PieceType.QUEEN
        }

        return Move(
            fromRow = fromRow,
            fromCol = fromCol,
            toRow = toRow,
            toCol = toCol,
            promotionType = promotionType,
        )
    }

    /** Weighted random selection from a list of book moves. */
    private fun weightedRandom(moves: List<PolyglotBook.BookMove>): PolyglotBook.BookMove {
        val totalWeight = moves.sumOf { it.weight }
        var random = (Math.random() * totalWeight).toInt()
        for (bookMove in moves) {
            random -= bookMove.weight
            if (random <= 0) return bookMove
        }
        return moves.last()
    }
}
