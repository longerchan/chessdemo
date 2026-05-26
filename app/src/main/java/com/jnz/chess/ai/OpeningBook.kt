package com.jnz.chess.ai

import com.jnz.chess.domain.ChessEngine
import com.jnz.chess.domain.Color
import com.jnz.chess.domain.GameState
import com.jnz.chess.domain.Move
import com.jnz.chess.domain.PieceType

/**
 * Hardcoded polyglot-style opening book using UCI move sequences as keys.
 * Each key maps to a list of book moves (with weights for variety).
 */
object OpeningBook : OpeningBookProvider {

    // Opening book: key = space-separated UCI move list from starting position
    // value = list of candidate moves with weights (move, weight)
    private val book: Map<String, List<Pair<String, Int>>> = mapOf(
        // --- Starting position (empty key) --- White's first move
        "" to listOf(
            "e2e4" to 40,
            "d2d4" to 30,
            "g1f3" to 15,
            "c2c4" to 10,
            "b1c3" to 5,
        ),

        // 1. e4 --- Black responses
        "e2e4" to listOf(
            "e7e5" to 35,
            "c7c5" to 30,
            "e7e6" to 15,
            "c7c6" to 10,
            "d7d6" to 5,
            "g8f6" to 5,
        ),

        // 1. e4 e5 --- White
        "e2e4 e7e5" to listOf(
            "g1f3" to 50,
            "f1c4" to 20,
            "f1b5" to 15,
            "d2d4" to 10,
            "b1c3" to 5,
        ),

        // 1. e4 e5 2. Nf3 --- Black
        "e2e4 e7e5 g1f3" to listOf(
            "b8c6" to 40,
            "d7d6" to 20,
            "g8f6" to 20,
        ),

        // 1. e4 e5 2. Nf3 Nc6 --- White (Italian, Ruy Lopez, Scotch)
        "e2e4 e7e5 g1f3 b8c6" to listOf(
            "f1b5" to 35,
            "f1c4" to 30,
            "d2d4" to 20,
            "b1c3" to 15,
        ),

        // 1. e4 e5 2. Nf3 Nc6 3. Bb5 (Ruy Lopez) --- Black
        "e2e4 e7e5 g1f3 b8c6 f1b5" to listOf(
            "a7a6" to 45,
            "g8f6" to 30,
            "d7d6" to 15,
            "f8c5" to 10,
        ),

        // 1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 (Ruy Lopez) --- White
        "e2e4 e7e5 g1f3 b8c6 f1b5 a7a6" to listOf(
            "b5a4" to 50,
            "b5c4" to 20,
            "b5c6" to 15,
            "b5e2" to 10,
            "b5d3" to 5,
        ),

        // 1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 4. Ba4 Nf6 --- White
        "e2e4 e7e5 g1f3 b8c6 f1b5 a7a6 b5a4 g8f6" to listOf(
            "e1g1" to 50,
            "b1c3" to 25,
            "d2d3" to 15,
            "d2d4" to 10,
        ),

        // 1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 4. Ba4 Nf6 5. O-O --- Black (Main line)
        "e2e4 e7e5 g1f3 b8c6 f1b5 a7a6 b5a4 g8f6 e1g1" to listOf(
            "f8e7" to 35,
            "b7b5" to 25,
            "f8c5" to 20,
            "b8d7" to 10,
            "d7d6" to 10,
        ),

        // 1. e4 e5 2. Nf3 Nc6 3. Bc4 (Italian) --- Black
        "e2e4 e7e5 g1f3 b8c6 f1c4" to listOf(
            "f8c5" to 35,
            "g8f6" to 35,
            "f8e7" to 15,
            "b8a5" to 10,
            "d7d6" to 5,
        ),

        // 1. e4 e5 2. Nf3 Nc6 3. Bc4 Bc5 (Giuoco Piano) --- White
        "e2e4 e7e5 g1f3 b8c6 f1c4 f8c5" to listOf(
            "c2c3" to 35,
            "d2d3" to 25,
            "b2b4" to 15,
            "e1g1" to 15,
            "d2d4" to 10,
        ),

        // 1. e4 e5 2. Nf3 Nc6 3. Bc4 Nf6 (Two Knights) --- White
        "e2e4 e7e5 g1f3 b8c6 f1c4 g8f6" to listOf(
            "d2d4" to 30,
            "d2d3" to 25,
            "e1g1" to 20,
            "b1c3" to 15,
            "c2c3" to 10,
        ),

        // 1. e4 e5 2. Nf3 Nc6 3. d4 (Scotch) --- Black
        "e2e4 e7e5 g1f3 b8c6 d2d4" to listOf(
            "e5d4" to 50,
            "d7d6" to 30,
            "d7d5" to 15,
            "g8f6" to 5,
        ),

        // 1. e4 e5 2. Nf3 Nc6 3. d4 exd4 --- White
        "e2e4 e7e5 g1f3 b8c6 d2d4 e5d4" to listOf(
            "f3d4" to 50,
            "f1c4" to 30,
            "d1d4" to 15,
            "c2c3" to 5,
        ),

        // 1. e4 e5 2. Nf3 Nc6 3. d4 exd4 4. Nxd4 --- Black
        "e2e4 e7e5 g1f3 b8c6 d2d4 e5d4 f3d4" to listOf(
            "g8f6" to 35,
            "f8c5" to 30,
            "d7d6" to 15,
            "d8h4" to 10,
            "f8b4" to 10,
        ),

        // 1. e4 c5 (Sicilian) --- White
        "e2e4 c7c5" to listOf(
            "g1f3" to 40,
            "d2d4" to 25,
            "b1c3" to 15,
            "c2c3" to 10,
            "f2f4" to 5,
            "b2b4" to 5,
        ),

        // 1. e4 c5 2. Nf3 --- Black
        "e2e4 c7c5 g1f3" to listOf(
            "d7d6" to 35,
            "b8c6" to 25,
            "e7e6" to 20,
            "g8f6" to 10,
            "d7d5" to 5,
            "g7g6" to 5,
        ),

        // 1. e4 c5 2. Nf3 d6 (Open Sicilian) --- White
        "e2e4 c7c5 g1f3 d7d6" to listOf(
            "d2d4" to 60,
            "b1c3" to 20,
            "c2c3" to 10,
            "f1b5" to 5,
            "f1e2" to 5,
        ),

        // 1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 --- Black
        "e2e4 c7c5 g1f3 d7d6 d2d4 c5d4 f3d4" to listOf(
            "g8f6" to 40,
            "b8c6" to 30,
            "a7a6" to 15,
            "g7g6" to 10,
            "e7e5" to 5,
        ),

        // 1. e4 c5 2. Nf3 d6 3. d4 cxd4 4. Nxd4 Nf6 5. Nc3 --- Black
        "e2e4 c7c5 g1f3 d7d6 d2d4 c5d4 f3d4 g8f6 b1c3" to listOf(
            "a7a6" to 25,
            "g7g6" to 45,
            "e7e5" to 15,
            "b8c6" to 15,
            "e7e6" to 15,
            "d7d5" to 5,
        ),

        // 1. e4 e6 (French) --- White
        "e2e4 e7e6" to listOf(
            "d2d4" to 60,
            "d2d3" to 15,
            "g1f3" to 10,
            "b1c3" to 10,
            "f2f4" to 5,
        ),

        // 1. e4 e6 2. d4 d5 (French Defense) --- White
        "e2e4 e7e6 d2d4 d7d5" to listOf(
            "b1c3" to 25,
            "e4e5" to 25,
            "e4d5" to 25,
            "b1d2" to 15,
            "g1f3" to 10,
        ),

        // 1. e4 c6 (Caro-Kann) --- White
        "e2e4 c7c6" to listOf(
            "d2d4" to 55,
            "d2d3" to 15,
            "g1f3" to 15,
            "b1c3" to 10,
            "f2f4" to 5,
        ),

        // 1. e4 c6 2. d4 d5 (Caro-Kann) --- White
        "e2e4 c7c6 d2d4 d7d5" to listOf(
            "e4e5" to 30,
            "b1c3" to 25,
            "b1d2" to 20,
            "e4d5" to 15,
            "g1f3" to 10,
        ),

        // 1. e4 d6 (Pirc/Modern) --- White
        "e2e4 d7d6" to listOf(
            "d2d4" to 50,
            "g1f3" to 25,
            "d2d3" to 15,
            "b1c3" to 10,
        ),

        // 1. e4 d5 (Scandinavian) --- White
        "e2e4 d7d5" to listOf(
            "e4d5" to 55,
            "e4e5" to 20,
            "b1c3" to 15,
            "d2d3" to 5,
            "d2d4" to 5,
        ),

        // 1. e4 d5 2. exd5 Qxd5 --- White
        "e2e4 d7d5 e4d5 d8d5" to listOf(
            "b1c3" to 60,
            "g1f3" to 25,
            "d2d4" to 10,
            "d2d3" to 5,
        ),

        // 1. e4 g6 (Modern/Pirc) --- White
        "e2e4 g7g6" to listOf(
            "d2d4" to 50,
            "g1f3" to 25,
            "f2f4" to 10,
            "b1c3" to 10,
            "d2d3" to 5,
        ),

        // 1. e4 e5 2. Nf3 Nf6 (Petrov) --- White
        "e2e4 e7e5 g1f3 g8f6" to listOf(
            "f3e5" to 40,
            "d2d4" to 30,
            "f3c3" to 15,
            "b1c3" to 10,
            "d2d3" to 5,
        ),

        // 1. d4 --- Black responses
        "d2d4" to listOf(
            "g8f6" to 40,
            "d7d5" to 30,
            "d7d6" to 10,
            "e7e6" to 10,
            "f7f5" to 5,
            "c7c5" to 5,
        ),

        // 1. d4 d6 (White 2nd move)
        "d2d4 d7d6" to listOf(
            "c2c4" to 30,
            "g1f3" to 25,
            "e2e4" to 20,
            "b1c3" to 10,
            "d1d3" to 5,
            "c1g5" to 5,
            "g2g3" to 5,
        ),

        // 1. d4 d6 2. c4 (White) --- Black
        "d2d4 d7d6 c2c4" to listOf(
            "g8f6" to 35,
            "e7e5" to 20,
            "e7e6" to 15,
            "g7g6" to 15,
            "c7c5" to 10,
            "d7d5" to 5,
        ),

        // 1. d4 e6 (White 2nd move)
        "d2d4 e7e6" to listOf(
            "c2c4" to 45,
            "g1f3" to 25,
            "b1c3" to 10,
            "e2e4" to 10,
            "c1g5" to 5,
            "e2e3" to 5,
        ),

        // 1. d4 e6 2. c4 (White) --- Black
        "d2d4 e7e6 c2c4" to listOf(
            "d7d5" to 35,
            "c7c5" to 25,
            "g8f6" to 20,
            "b7b6" to 10,
            "f8b4" to 5,
            "g7g6" to 5,
        ),

        // 1. d4 e6 2. c4 d5 (French/QGD) --- White
        "d2d4 e7e6 c2c4 d7d5" to listOf(
            "b1c3" to 35,
            "g1f3" to 35,
            "c4d5" to 15,
            "c1g5" to 10,
            "e2e3" to 5,
        ),

        // 1. d4 e6 2. c4 d5 3. Nc3 (White) --- Black
        "d2d4 e7e6 c2c4 d7d5 b1c3" to listOf(
            "g8f6" to 35,
            "c7c5" to 25,
            "f8b4" to 15,
            "d7d5" to 5,
            "b8d7" to 10,
            "f8e7" to 10,
        ),

        // 1. d4 d5 2. c4 e6 (QGD) --- White
        "d2d4 d7d5 c2c4 e7e6" to listOf(
            "b1c3" to 30,
            "g1f3" to 30,
            "c1g5" to 15,
            "c4d5" to 10,
            "a2a3" to 5,
            "e2e3" to 10,
        ),

        // 1. d4 d5 2. c4 c6 (Slav) --- White
        "d2d4 d7d5 c2c4 c7c6" to listOf(
            "g1f3" to 35,
            "b1c3" to 25,
            "c4d5" to 15,
            "g2g3" to 10,
            "e2e3" to 10,
            "c1g5" to 5,
        ),

        // 1. d4 d5 2. c4 e6 3. Nc3 Nf6 --- White
        "d2d4 d7d5 c2c4 e7e6 b1c3 g8f6" to listOf(
            "c1g5" to 30,
            "g1f3" to 30,
            "e2e3" to 20,
            "c4d5" to 10,
            "c1f4" to 5,
        ),

        // 1. d4 d5 2. c4 c6 3. Nf3 Nf6 --- White
        "d2d4 d7d5 c2c4 c7c6 g1f3 g8f6" to listOf(
            "b1c3" to 35,
            "c4d5" to 20,
            "e2e3" to 15,
            "c1g5" to 10,
            "g2g3" to 10,
            "d1b3" to 10,
        ),

        // 1. d4 e6 2. c4 c5 (French) --- White
        "d2d4 e7e6 c2c4 c7c5" to listOf(
            "g1f3" to 30,
            "d4c5" to 25,
            "d4d5" to 15,
            "b1c3" to 15,
            "e2e3" to 10,
            "g2g3" to 5,
        ),

        // 1. d4 Nf6 2. c4 e6 3. Nc3 (White) --- Black
        "d2d4 g8f6 c2c4 e7e6 b1c3" to listOf(
            "f8b4" to 30,
            "d7d5" to 30,
            "c7c5" to 20,
            "b7b6" to 10,
            "f8e7" to 5,
            "b8c6" to 5,
        ),

        // 1. d4 Nf6 2. c4 g6 3. Nc3 (White) --- Black
        "d2d4 g8f6 c2c4 g7g6 b1c3" to listOf(
            "f8g7" to 55,
            "d7d6" to 20,
            "d7d5" to 15,
            "b7b6" to 5,
            "c7c5" to 5,
        ),

        // 1. d4 Nf6 2. c4 g6 3. Nc3 Bg7 4. e4 (White) --- Black
        "d2d4 g8f6 c2c4 g7g6 b1c3 f8g7 e2e4" to listOf(
            "d7d6" to 50,
            "e7e5" to 25,
            "d7d5" to 15,
            "c7c5" to 5,
            "c7c6" to 5,
        ),

        // 1. d4 Nf6 2. c4 g6 3. Nc3 Bg7 4. e4 d6 (White) --- Black
        "d2d4 g8f6 c2c4 g7g6 b1c3 f8g7 e2e4 d7d6" to listOf(
            "g1f3" to 45,
            "c1e3" to 20,
            "f1e2" to 25,
            "e4e5" to 5,
            "e1g1" to 5,
        ),

        // 1. d4 Nf6 --- White
        "d2d4 g8f6" to listOf(
            "c2c4" to 45,
            "g1f3" to 25,
            "c1g5" to 10,
            "b1c3" to 10,
            "c1f4" to 5,
            "f2f3" to 5,
        ),

        // 1. d4 Nf6 2. c4 --- Black
        "d2d4 g8f6 c2c4" to listOf(
            "e7e6" to 30,
            "g7g6" to 25,
            "d7d5" to 20,
            "c7c5" to 10,
            "d7d6" to 5,
            "c7c6" to 5,
            "e7e5" to 5,
        ),

        // 1. d4 Nf6 2. c4 g6 (King's Indian / Grunfeld) --- White
        "d2d4 g8f6 c2c4 g7g6" to listOf(
            "b1c3" to 35,
            "g1f3" to 30,
            "g2g3" to 20,
            "c1g5" to 10,
            "d4d5" to 5,
        ),

        // 1. d4 Nf6 2. c4 e6 (Queen's Gambit Declined / Nimzo) --- White
        "d2d4 g8f6 c2c4 e7e6" to listOf(
            "b1c3" to 35,
            "g1f3" to 35,
            "c1g5" to 15,
            "g2g3" to 10,
            "a2a3" to 5,
        ),

        // 1. d4 d5 (Queen's Gambit territory) --- White
        "d2d4 d7d5" to listOf(
            "c2c4" to 55,
            "g1f3" to 20,
            "b1c3" to 15,
            "c1f4" to 5,
            "e2e3" to 5,
        ),

        // 1. d4 d5 2. c4 --- Black
        "d2d4 d7d5 c2c4" to listOf(
            "e7e6" to 30,
            "d5c4" to 25,
            "c7c6" to 20,
            "c7c5" to 15,
            "g8f6" to 5,
            "d7d5" to 5,
        ),

        // 1. d4 d5 2. c4 e6 (QGD) --- White
        "d2d4 d7d5 c2c4 e7e6" to listOf(
            "b1c3" to 35,
            "g1f3" to 35,
            "c1g5" to 15,
            "c4d5" to 10,
            "a2a3" to 5,
        ),

        // 1. d4 d5 2. c4 c6 (Slav) --- White
        "d2d4 d7d5 c2c4 c7c6" to listOf(
            "g1f3" to 35,
            "b1c3" to 25,
            "c4d5" to 15,
            "g2g3" to 10,
            "e2e3" to 10,
            "c1g5" to 5,
        ),

        // 1. d4 d5 2. c4 dxc4 (Queen's Gambit Accepted) --- White
        "d2d4 d7d5 c2c4 d5c4" to listOf(
            "e2e3" to 30,
            "g1f3" to 30,
            "e2e4" to 20,
            "a2a4" to 10,
            "b1c3" to 10,
        ),

        // 1. Nf3 --- Black
        "g1f3" to listOf(
            "g8f6" to 30,
            "d7d5" to 25,
            "c7c5" to 20,
            "d7d6" to 10,
            "e7e6" to 10,
            "g7g6" to 5,
        ),

        // 1. Nf3 d5 (White 2nd move)
        "g1f3 d7d5" to listOf(
            "d2d4" to 40,
            "c2c4" to 30,
            "g1f3" to 5,
            "b1c3" to 10,
            "d2d3" to 5,
            "e2e3" to 5,
            "g2g3" to 5,
        ),

        // 1. Nf3 d5 2. d4 (White) --- Black
        "g1f3 d7d5 d2d4" to listOf(
            "g8f6" to 40,
            "c7c5" to 20,
            "c7c6" to 15,
            "d5c4" to 10,
            "e7e6" to 10,
            "g7g6" to 5,
        ),

        // 1. Nf3 d5 2. d4 Nf6 (White 3rd) --- Black
        "g1f3 d7d5 d2d4 g8f6" to listOf(
            "c2c4" to 40,
            "b1c3" to 20,
            "c1f4" to 15,
            "e2e3" to 10,
            "g2g3" to 10,
            "d1d3" to 5,
        ),

        // 1. Nf3 d5 2. c4 (English) --- Black
        "g1f3 d7d5 c2c4" to listOf(
            "d5c4" to 30,
            "c7c6" to 25,
            "e7e6" to 20,
            "d7d6" to 10,
            "g8f6" to 10,
            "c7c5" to 5,
        ),

        // 1. Nf3 d5 2. c4 dxc4 (White 3rd) --- Black
        "g1f3 d7d5 c2c4 d5c4" to listOf(
            "e2e3" to 30,
            "a2a4" to 25,
            "e2e4" to 20,
            "b1c3" to 15,
            "d1a4" to 10,
        ),

        // 1. Nf3 Nf6 2. d4 (White) --- Black
        "g1f3 g8f6 d2d4" to listOf(
            "d7d5" to 35,
            "g7g6" to 20,
            "e7e6" to 20,
            "d7d6" to 10,
            "c7c5" to 10,
            "b7b6" to 5,
        ),

        // 1. Nf3 Nf6 2. c4 (English/Reti) --- Black
        "g1f3 g8f6 c2c4" to listOf(
            "g7g6" to 25,
            "e7e6" to 25,
            "c7c5" to 20,
            "d7d5" to 15,
            "c7c6" to 10,
            "b7b6" to 5,
        ),

        // 1. c4 (English) --- Black
        "c2c4" to listOf(
            "e7e5" to 25,
            "c7c5" to 20,
            "g8f6" to 20,
            "e7e6" to 15,
            "g7g6" to 10,
            "d7d6" to 5,
            "c7c6" to 5,
        ),

        // 1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 4. Ba4 Nf6 5. O-O Be7 6. Re1 (Ruy Lopez main) --- Black
        "e2e4 e7e5 g1f3 b8c6 f1b5 a7a6 b5a4 g8f6 e1g1 f8e7 f1e1" to listOf(
            "b7b5" to 35,
            "d7d6" to 30,
            "e8g8" to 15,
            "b8d7" to 10,
            "d8c7" to 5,
            "c8d7" to 5,
        ),

        // 1. e4 e5 2. Nf3 Nc6 3. Bc4 Bc5 4. c3 Nf6 5. d4 (Italian main) --- Black
        "e2e4 e7e5 g1f3 b8c6 f1c4 f8c5 c2c3 g8f6 d2d4" to listOf(
            "e5d4" to 40,
            "d7d6" to 30,
            "f8b6" to 15,
            "c5b6" to 10,
            "d8e7" to 5,
        ),

        // 1. e4 e5 2. Nf3 Nc6 3. Bc4 Bc5 4. c3 Nf6 5. d4 exd4 --- White
        "e2e4 e7e5 g1f3 b8c6 f1c4 f8c5 c2c3 g8f6 d2d4 e5d4" to listOf(
            "c3d4" to 50,
            "e4e5" to 30,
            "f3d4" to 15,
            "e1g1" to 5,
        ),

        // 1. e4 e5 2. Nf3 Nc6 3. Bc4 Bc5 4. c3 Nf6 5. d4 exd4 6. cxd4 --- Black
        "e2e4 e7e5 g1f3 b8c6 f1c4 f8c5 c2c3 g8f6 d2d4 e5d4 c3d4" to listOf(
            "f8b4" to 30,
            "c5b6" to 30,
            "d7d6" to 20,
            "d8b6" to 10,
        ),

        // 1. d4 Nf6 2. c4 g6 3. Nc3 Bg7 4. e4 d6 5. Nf3 O-O (KID main) --- White
        "d2d4 g8f6 c2c4 g7g6 b1c3 f8g7 e2e4 d7d6 g1f3 e8g8" to listOf(
            "f1e2" to 35,
            "c1g5" to 25,
            "f1d3" to 15,
            "e4e5" to 10,
            "e1g1" to 10,
            "c1e3" to 5,
        ),

        // 1. d4 d5 2. c4 e6 3. Nc3 Nf6 4. Bg5 Be7 (QGD main) --- White
        "d2d4 d7d5 c2c4 e7e6 b1c3 g8f6 c1g5 f8e7" to listOf(
            "e2e3" to 50,
            "g1f3" to 30,
            "c4d5" to 10,
            "d1c2" to 5,
            "a2a3" to 5,
        ),

        // 1. d4 d5 2. c4 e6 3. Nc3 Nf6 4. Bg5 Be7 5. e3 (QGD) --- Black
        "d2d4 d7d5 c2c4 e7e6 b1c3 g8f6 c1g5 f8e7 e2e3" to listOf(
            "e8g8" to 45,
            "b7b6" to 20,
            "c7c5" to 15,
            "h7h6" to 10,
            "b8d7" to 10,
        ),

        // 1. d4 d5 2. c4 c6 3. Nf3 Nf6 (Slav) --- White
        "d2d4 d7d5 c2c4 c7c6 g1f3 g8f6" to listOf(
            "b1c3" to 45,
            "c4d5" to 20,
            "e2e3" to 15,
            "c1g5" to 10,
            "g2g3" to 5,
            "d1b3" to 5,
        ),

        // 1. c4 e5 (English: Symmetrical) --- White
        "c2c4 e7e5" to listOf(
            "b1c3" to 35,
            "g1f3" to 30,
            "g2g3" to 15,
            "b2b4" to 10,
            "e2e3" to 5,
            "d2d3" to 5,
        ),

        // 1. c4 c5 (Symmetrical English) --- White
        "c2c4 c7c5" to listOf(
            "g1f3" to 35,
            "b1c3" to 25,
            "g2g3" to 20,
            "e2e3" to 10,
            "d2d4" to 5,
            "b2b3" to 5,
        ),

        // 1. c4 Nf6 (English) --- White
        "c2c4 g8f6" to listOf(
            "b1c3" to 30,
            "g1f3" to 30,
            "g2g3" to 20,
            "d2d4" to 10,
            "e2e3" to 5,
            "b2b3" to 5,
        ),

        // 1. Nc3 (Dunst/Van Geet) --- Black
        "b1c3" to listOf(
            "d7d5" to 25,
            "e7e5" to 25,
            "g8f6" to 20,
            "d7d6" to 15,
            "e7e6" to 10,
            "c7c5" to 5,
        ),

        // 1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 4. Ba4 Nf6 5. O-O Be7 --- White
        "e2e4 e7e5 g1f3 b8c6 f1b5 a7a6 b5a4 g8f6 e1g1 f8e7" to listOf(
            "f1e1" to 35,
            "d2d4" to 25,
            "b1c3" to 20,
            "c2c3" to 15,
            "d2d3" to 5,
        ),

        // 1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 4. Ba4 Nf6 5. O-O b5 (Moller) --- White
        "e2e4 e7e5 g1f3 b8c6 f1b5 a7a6 b5a4 g8f6 e1g1 b7b5" to listOf(
            "a4b3" to 55,
            "a4b5" to 20,
            "b1c3" to 10,
            "d2d3" to 10,
            "c2c3" to 5,
        ),

        // 1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 4. Ba4 Nf6 5. O-O b5 6. Bb3 Bc5 --- White
        "e2e4 e7e5 g1f3 b8c6 f1b5 a7a6 b5a4 g8f6 e1g1 b7b5 a4b3 f8c5" to listOf(
            "c2c3" to 40,
            "d2d4" to 25,
            "d2d3" to 20,
            "b1c3" to 15,
        ),

        // 1. e4 e5 2. Nf3 Nc6 3. Bc4 Bc5 4. c3 Nf6 --- White
        "e2e4 e7e5 g1f3 b8c6 f1c4 f8c5 c2c3 g8f6" to listOf(
            "d2d4" to 40,
            "d2d3" to 30,
            "d1e2" to 15,
            "e1g1" to 10,
            "b2b4" to 5,
        ),

        // 1. e4 e5 2. Nf3 Nc6 3. Bc4 Bc5 4. c3 Nf6 5. d4 exd4 6. cxd4 Bb4+ --- White
        "e2e4 e7e5 g1f3 b8c6 f1c4 f8c5 c2c3 g8f6 d2d4 e5d4 c3d4 f8b4" to listOf(
            "b1c3" to 45,
            "c1d2" to 40,
            "b1d2" to 15,
        ),

        // 1. d4 Nf6 2. c4 g6 3. Nc3 (King's Indian) --- Black
        "d2d4 g8f6 c2c4 g7g6 b1c3" to listOf(
            "f8g7" to 55,
            "d7d6" to 20,
            "d7d5" to 15,
            "b7b6" to 5,
            "c7c5" to 5,
        ),

        // 1. d4 Nf6 2. c4 g6 3. Nc3 Bg7 4. e4 (King's Indian) --- Black
        "d2d4 g8f6 c2c4 g7g6 b1c3 f8g7 e2e4" to listOf(
            "d7d6" to 50,
            "e7e5" to 25,
            "d7d5" to 15,
            "c7c5" to 5,
            "c7c6" to 5,
        ),

        // 1. d4 Nf6 2. c4 g6 3. Nc3 Bg7 4. e4 d6 5. Nf3 (KID main line) --- Black
        "d2d4 g8f6 c2c4 g7g6 b1c3 f8g7 e2e4 d7d6 g1f3" to listOf(
            "e8g8" to 45,
            "b8d7" to 20,
            "e7e5" to 15,
            "b8c6" to 10,
            "c7c5" to 5,
            "b8a6" to 5,
        ),

        // 1. d4 Nf6 2. c4 e6 3. Nc3 (QGD/Nimzo) --- Black
        "d2d4 g8f6 c2c4 e7e6 b1c3" to listOf(
            "f8b4" to 30,
            "d7d5" to 30,
            "c7c5" to 20,
            "b7b6" to 10,
            "f8e7" to 5,
            "b8c6" to 5,
        ),

        // 1. d4 Nf6 2. c4 e6 3. Nc3 Bb4 (Nimzo-Indian) --- White
        "d2d4 g8f6 c2c4 e7e6 b1c3 f8b4" to listOf(
            "e2e3" to 40,
            "c1g5" to 20,
            "d1c2" to 15,
            "g1e2" to 10,
            "g1f3" to 5,
            "c1d2" to 5,
            "a2a3" to 5,
        ),

        // 1. d4 Nf6 2. c4 e6 3. Nc3 Bb4 4. e3 (Nimzo main) --- Black
        "d2d4 g8f6 c2c4 e7e6 b1c3 f8b4 e2e3" to listOf(
            "c7c5" to 35,
            "e8g8" to 25,
            "b7b6" to 20,
            "d7d5" to 15,
            "d7d6" to 5,
        ),

        // 1. d4 Nf6 2. c4 e6 3. Nc3 d5 (QGD Orthodox) --- White
        "d2d4 g8f6 c2c4 e7e6 b1c3 d7d5" to listOf(
            "c1g5" to 30,
            "g1f3" to 30,
            "c4d5" to 20,
            "e2e3" to 15,
            "c1f4" to 5,
        ),

        // 1. d4 d5 2. c4 e6 3. Nc3 Nf6 --- White
        "d2d4 d7d5 c2c4 e7e6 b1c3 g8f6" to listOf(
            "c1g5" to 30,
            "g1f3" to 30,
            "e2e3" to 20,
            "c4d5" to 10,
            "c1f4" to 5,
            "a2a3" to 5,
        ),

        // 1. d4 d5 2. c4 e6 3. Nc3 Nf6 4. Bg5 (QGD main) --- Black
        "d2d4 d7d5 c2c4 e7e6 b1c3 g8f6 c1g5" to listOf(
            "d5c4" to 35,
            "f8e7" to 40,
            "f8b4" to 10,
            "c7c5" to 10,
            "h7h6" to 5,
        ),

        // 1. d4 d5 2. c4 c6 3. Nf3 (Slav main) --- Black
        "d2d4 d7d5 c2c4 c7c6 g1f3" to listOf(
            "g8f6" to 60,
            "d5c4" to 30,
            "e7e6" to 10,
        ),

        // 1. e4 e5 2. Nf3 d6 (Philidor) --- White
        "e2e4 e7e5 g1f3 d7d6" to listOf(
            "d2d4" to 40,
            "f1c4" to 25,
            "b1c3" to 15,
            "d2d3" to 10,
            "f1b5" to 5,
            "e2e3" to 5,
        ),

        // 1. e4 Nf6 (Alekhine) --- White
        "e2e4 g8f6" to listOf(
            "e4e5" to 60,
            "b1c3" to 20,
            "d2d3" to 10,
            "d2d4" to 5,
            "f2f3" to 5,
        ),

        // 1. e4 Nf6 2. e5 Nd5 (Alekhine) --- White
        "e2e4 g8f6 e4e5 f6d5" to listOf(
            "d2d4" to 45,
            "g1f3" to 25,
            "c2c4" to 20,
            "f2f4" to 10,
        )
    )

    override fun lookup(state: GameState): List<Pair<String, Int>> {
        val moveSequence = state.moveHistory.joinToString(" ") { moveToUci(it) }
        return book[moveSequence] ?: emptyList()
    }

    private fun moveToUci(move: Move): String {
        val files = "abcdefgh"
        val fromRank = 8 - move.fromRow
        val toRank = 8 - move.toRow
        val from = "${files[move.fromCol]}$fromRank"
        val to = "${files[move.toCol]}$toRank"
        val promo = if ((toRank == 1 || toRank == 8) && move.promotionType != null) {
            when (move.promotionType) {
                PieceType.QUEEN -> "q"
                PieceType.ROOK -> "r"
                PieceType.BISHOP -> "b"
                PieceType.KNIGHT -> "n"
                else -> "q"
            }
        } else ""
        return "$from$to$promo"
    }
}
