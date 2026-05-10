# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

ChessDemo is an Android chess game built with Kotlin and Jetpack Compose. It supports two-player local play and a basic AI opponent.

## Tech Stack

- **Language**: Kotlin 2.0.21
- **UI**: Jetpack Compose (no XML views)
- **Build**: Gradle with Kotlin DSL (`build.gradle.kts`)
- **Min SDK**: 26 (Android 8.0), Target SDK: 35
- **Compose BOM**: 2024.12.01, Activity Compose: 1.9.3

## Architecture

The app is organized into three layers with no DI framework or complex architecture — everything is simple and direct:

```
com.chessdemo/
├── MainActivity.kt              # Entry point, sets up Compose content
├── domain/
│   ├── Piece.kt                 # Piece, PieceType, Color enums + unicode symbols
│   ├── Move.kt                  # Move data class with algebraic notation
│   ├── GameState.kt             # Immutable board state, castling rights, en passant, game over detection
│   └── ChessEngine.kt           # Move generation, validation, full chess rules
├── ai/
│   └── ChessAI.kt               # Minimax with alpha-beta pruning (depth 3) + piece-square tables
└── ui/
    ├── GameScreen.kt            # Main screen: board rendering, interaction, game loop
    └── theme/Theme.kt           # Material3 color scheme
```

### Domain Layer

- `GameState` is immutable — `ChessEngine.makeMove()` returns a new state, never mutates.
- `ChessEngine` is a Kotlin `object` with pure functions. Key APIs:
  - `getLegalMoves(state, color?)` — returns all legal moves for a color
  - `makeMove(state, move)` — returns new GameState after applying the move
  - `isSquareAttacked(state, row, col, byColor)` — attack detection for check/castling
- All chess rules implemented: castling (king/queen side), en passant, pawn promotion (auto-queen for AI, all options available), check/checkmate/stalemate, 50-move rule, insufficient material detection.

### AI Layer

- `ChessAI.findBestMove(state)` returns the best move for the current player using minimax with alpha-beta pruning at depth 3.
- Evaluation combines material values (pawn=100, knight=320, bishop=330, rook=500, queen=900) with piece-square tables for positional play.
- AI plays as Black. It runs on the main thread via a `LaunchedEffect` with a 300ms delay for UX.

### UI Layer

- `GameScreen` is the only screen. It manages all state via `remember { mutableStateOf(...) }`.
- Interaction: tap a piece to select it, legal moves shown as dots, tap destination to move.
- Supports switching between "vs AI" and "2 Player" modes.

## Commands

### Build

```bash
./gradlew assembleDebug
```

### Run on connected device/emulator

```bash
./gradlew installDebug
```

### Run tests

No test framework is configured yet. To add one, add `testImplementation("junit:junit:4.13.2")` to `app/build.gradle.kts` and run:

```bash
./gradlew test
```

### Lint

```bash
./gradlew lintDebug
```

## Notes

- No XML layout files — all UI is Jetpack Compose.
- No navigation — single screen app.
- No dependency injection — direct object/function calls.
- The project does not have a Gradle wrapper (`gradlew`) yet. Run `gradle wrapper` or download the wrapper JAR to `gradle/wrapper/` before building.
- To build from scratch, you need Android SDK 35 installed.
