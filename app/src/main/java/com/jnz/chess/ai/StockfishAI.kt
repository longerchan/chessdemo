package com.jnz.chess.ai

import com.jnz.chess.domain.Color
import com.jnz.chess.domain.GameState
import com.jnz.chess.domain.Move
import com.jnz.chess.domain.Piece
import com.jnz.chess.domain.PieceType

object StockfishAI {

    enum class Difficulty(val label: String, val depth: Int, val moveTime: Int) {
        BEGINNER("初级", 6, 1000),
        EASY("简单", 10, 2000),
        MEDIUM("中级", 22, 5000),
        HARD("困难", 28, 8000),
        GRANDMASTER("大师", 99, 15000),
    }

    @Volatile var difficulty: Difficulty = Difficulty.MEDIUM

    private const val TAG = "StockfishAI"

    private var engineInitialized = false
    private var nnueFilePath: String? = null

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
            StockfishEngine.initialize(nnueFilePath)
            setEloForDifficulty(difficulty)
            engineInitialized = true
        }
    }

    fun loadBookFromAssets(context: android.content.Context) {
        if (!PolyglotBook.isLoaded()) {
            PolyglotBook.loadFromAssets(context)
        }
    }

    /** Extract NNUE network file from assets to internal storage. Returns the file path. */
    fun loadNNUEFromAssets(context: android.content.Context): String? {
        if (nnueFilePath != null) return nnueFilePath
        val filename = "nn-fcf986aea78a.nnue"
        val outFile = java.io.File(context.filesDir, filename)
        if (outFile.exists()) {
            nnueFilePath = outFile.absolutePath
            android.util.Log.d(TAG, "NNUE already extracted: $nnueFilePath")
            return nnueFilePath
        }
        return try {
            context.assets.open(filename).use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            nnueFilePath = outFile.absolutePath
            android.util.Log.d(TAG, "NNUE extracted to: $nnueFilePath")
            nnueFilePath
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to extract NNUE from assets: ${e.message}")
            null
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
        when (diff) {
            Difficulty.BEGINNER, Difficulty.EASY -> {
                val elo = eloForDifficulty(diff)
                StockfishEngine.setOption("UCI_LimitStrength", "true")
                StockfishEngine.setOption("UCI_Elo", elo.toString())
            }
            else -> {
                StockfishEngine.setOption("UCI_LimitStrength", "false")
            }
        }
    }

    fun findBestMove(state: GameState): Move? {
        return findBestMoveFromFen(stateToFen(state), state)
    }

    fun findBestMoveFromFen(fen: String, state: GameState? = null): Move? {
        ensureEngineInitialized()

        // Unified opening book lookup (Polyglot + built-in)
        if (state != null) {
            val bookMoves = buildBookMoves(state)
            if (bookMoves.isNotEmpty()) {
                val legalMoves = com.jnz.chess.domain.ChessEngine.getLegalMoves(state, state.currentTurn)
                val move = selectBookMove(bookMoves, legalMoves)
                if (move != null) return move
            }
        }

        val depth = difficulty.depth
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

    /** Build unified book move list from Polyglot + OpeningBook. */
    private fun buildBookMoves(state: GameState): List<Pair<String, Int>> {
        val results = mutableListOf<Pair<String, Int>>()
        val seen = mutableSetOf<String>()

        // Polyglot external book
        if (PolyglotBook.isLoaded()) {
            val fen = stateToFenPublic(state)
            for (bm in PolyglotBook.lookupByFen(fen)) {
                if (bm.uci !in seen) {
                    seen.add(bm.uci)
                    results.add(bm.uci to bm.weight)
                    android.util.Log.d(TAG, "findBestMove: POLYGLOT BOOK=${bm.uci}")
                }
            }
        }

        // Built-in opening book
        for ((uci, weight) in OpeningBook.lookup(state)) {
            if (uci !in seen) {
                seen.add(uci)
                results.add(uci to weight)
            }
        }

        return results
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
            "${('a' + col).toChar()}${8 - row}"
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

}
