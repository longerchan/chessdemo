package com.jnz.chess.ui.util

import com.jnz.chess.domain.*

fun generatePgn(tree: com.jnz.chess.domain.GameTree): String {
    val moves = tree.moveList()
    if (moves.isEmpty()) return "[Result \"*\"]\n\n*"
    val sb = StringBuilder()
    val pgnResult = tree.currentState().gameResult?.pgnResult() ?: "*"
    sb.append("[Result \"$pgnResult\"]\n\n")
    var moveNum = 1
    for (i in moves.indices step 2) {
        sb.append("$moveNum. ")
        sb.append(moveToAlgebraic(moves[i], tree.states[i]))
        if (i + 1 < moves.size) {
            sb.append(" ")
            sb.append(moveToAlgebraic(moves[i + 1], tree.states[i + 1]))
        }
        sb.append(" ")
        moveNum++
    }
    if (tree.currentState().gameOver) {
        sb.append("$pgnResult")
    }
    return sb.toString().trim()
}

fun moveToAlgebraic(move: Move, state: GameState): String {
    val files = "abcdefgh"
    if (move.isCastling) return if (move.toCol == 6) "O-O" else "O-O-O"

    val piece = state.board[move.fromRow][move.fromCol]
    val pieceSymbol = when (piece?.type) {
        PieceType.KING -> "K"; PieceType.QUEEN -> "Q"; PieceType.ROOK -> "R"
        PieceType.BISHOP -> "B"; PieceType.KNIGHT -> "N"; PieceType.PAWN -> ""
        else -> ""
    }

    val isCapture = move.isEnPassant || (state.board[move.toRow][move.toCol] != null)
    val pawnFile = if (piece?.type == PieceType.PAWN && isCapture) files[move.fromCol] else ""
    val capture = if (isCapture) "x" else ""
    val promo = if (piece?.type == PieceType.PAWN && (move.toRow == 0 || move.toRow == 7)) {
        "=${move.promotionType?.symbol ?: "Q"}"
    } else ""
    val toSq = "${files[move.toCol]}${8 - move.toRow}"

    var notation = "$pieceSymbol$pawnFile$capture$toSq$promo"

    // Check/mate suffix
    val nextState = ChessEngine.makeMove(state, move)
    if (nextState.gameOver && nextState.result != null) {
        notation += "#"
    } else {
        val kingPos = nextState.findKing(nextState.currentTurn)
        if (kingPos != null) {
            val opponentColor = if (nextState.currentTurn == Color.WHITE) Color.BLACK else Color.WHITE
            if (ChessEngine.isSquareAttacked(nextState, kingPos.first, kingPos.second, opponentColor)) {
                notation += "+"
            }
        }
    }

    return notation
}

/** Strip PGN comments {...} and variations (...) with correct nesting handling. */
private fun stripPgnExtras(text: String): String {
    val sb = StringBuilder()
    var depth = 0

    for (ch in text) {
        when (ch) {
            '{', '(' -> depth++
            '}', ')' -> if (depth > 0) depth--
            else -> if (depth == 0) sb.append(ch)
        }
    }
    return sb.toString()
}

fun parsePgn(pgnText: String): Pair<String, List<Move>>? {
    try {
        val tags = mutableMapOf<String, String>()
        val tagRegex = Regex("\\[([A-Za-z]+)\\s+\"(.+?)\"\\]")
        for (match in tagRegex.findAll(pgnText)) {
            tags[match.groupValues[1]] = match.groupValues[2]
        }
        val fen = tags["FEN"] ?: "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

        // Strip tags, comments, variations (with nesting support), NAGs, results
        val moveText = stripPgnExtras(pgnText)
            .replace(tagRegex, "")
            .replace(Regex("0-1|1-0|1/2-1/2|\\*"), "")
            .replace(Regex("[!?]{1,2}"), "")       // annotation symbols
            .replace(Regex("[+#]"), "")             // check/mate suffixes
            .trim()

        // Parse moves: extract pairs like "1." "1..." "2." "2..." preserving order
        val movePairRegex = Regex("(\\d+)\\.\\s*([A-Za-z0-9x=+\\#-]+)\\.?\\s*\\.?\\s*([A-Za-z0-9x=+\\#-]+)?")
        val moves = mutableListOf<Move>()
        val initialState = ChessEngine.fenToStateOrNull(fen) ?: return null
        var currentState = initialState

        for (match in movePairRegex.findAll(moveText)) {
            val whiteMove = match.groupValues[2].takeIf { it.isNotBlank() }
            val blackMove = match.groupValues[3].takeIf { it.isNotBlank() }
            if (whiteMove != null) {
                val legalMoves = ChessEngine.getLegalMoves(currentState)
                val move = findMoveByAlgebraic(whiteMove, currentState, legalMoves) ?: return null
                moves.add(move)
                currentState = ChessEngine.makeMove(currentState, move)
            }
            if (blackMove != null) {
                val legalMoves = ChessEngine.getLegalMoves(currentState)
                val move = findMoveByAlgebraic(blackMove, currentState, legalMoves) ?: return null
                moves.add(move)
                currentState = ChessEngine.makeMove(currentState, move)
            }
        }

        return fen to moves
    } catch (_: Exception) {
        return null
    }
}

fun findMoveByAlgebraic(algebraic: String, state: GameState, legalMoves: List<Move>): Move? {
    val clean = algebraic.trim()
    val files = "abcdefgh"

    if (clean == "O-O" || clean == "0-0") return legalMoves.firstOrNull { it.isCastling && it.toCol == 6 }
    if (clean == "O-O-O" || clean == "0-0-0") return legalMoves.firstOrNull { it.isCastling && it.toCol == 2 }

    // Extended regex with optional source-rank disambiguation and promotion
    val regex = Regex("^([KQRBN])?([a-h])?([1-8])?x?([a-h][1-8])(?:=([QRBN]))?[+#]?$")
    val match = regex.matchEntire(clean) ?: return null

    val pieceType = when (match.groupValues[1]) {
        "" -> PieceType.PAWN; "K" -> PieceType.KING; "Q" -> PieceType.QUEEN
        "R" -> PieceType.ROOK; "B" -> PieceType.BISHOP; "N" -> PieceType.KNIGHT
        else -> PieceType.PAWN
    }
    val sourceFile = match.groupValues[2].takeIf { it.isNotEmpty() }?.let { it[0] - 'a' }
    val sourceRank = match.groupValues[3].takeIf { it.isNotEmpty() }?.let { it[0] - '1' }
    val targetFile = match.groupValues[4][0] - 'a'
    val targetRank = match.groupValues[4][1] - '1'
    val targetRow = 7 - targetRank
    val promoType = when (match.groupValues[5]) {
        "" -> PieceType.QUEEN; "Q" -> PieceType.QUEEN; "R" -> PieceType.ROOK
        "B" -> PieceType.BISHOP; "N" -> PieceType.KNIGHT; else -> PieceType.QUEEN
    }

    return legalMoves.firstOrNull { move ->
        move.toRow == targetRow && move.toCol == targetFile &&
            state.board[move.fromRow][move.fromCol]?.type == pieceType &&
            (sourceFile == null || move.fromCol == sourceFile) &&
            (sourceRank == null || move.fromRow == 7 - sourceRank) &&
            (pieceType != PieceType.PAWN || move.toRow != 0 && move.toRow != 7 || move.promotionType == promoType)
    }
}
