# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

ChessDemo is an Android chess app built with Kotlin and Jetpack Compose. It supports human-vs-human, human-vs-AI, and AI-vs-AI modes with a Stockfish engine backend, opening book, chess clock, FEN/PGN import/export, and board editor.

## Tech Stack

- **Language**: Kotlin 2.0.21
- **UI**: Jetpack Compose (no XML views), Material3
- **Build**: Gradle 8.7 with Kotlin DSL, AGP 8.7.0
- **Min SDK**: 26, Target/Compile SDK: 35
- **Compose BOM**: 2024.12.01, Activity Compose: 1.9.3
- **Architecture**: MVVM with `AndroidViewModel` + StateFlow (no DI framework)
- **Native**: Stockfish chess engine via JNI (CMake, `arm64-v8a` + `x86_64` ABIs)
- **Tests**: JUnit 4.13.2

## Commands

### Build

```bash
./gradlew assembleDebug
```

### Install on device/emulator

```bash
./gradlew installDebug
```

### Run all tests

```bash
./gradlew test
```

### Lint

```bash
./gradlew lintDebug
```

## Architecture

```
com.chessdemo/
├── MainActivity.kt                    # Entry point, edge-to-edge, sets up ViewModel + Compose
├── domain/
│   ├── Piece.kt / Move.kt             # Data classes, Color/PieceType enums, unicode symbols
│   ├── GameState.kt                   # Immutable board state, castling, en passant, positionKey for repetition
│   ├── ChessEngine.kt                 # Pure-function chess rules: getLegalMoves, makeMove, FEN parsing
│   ├── GameResult.kt                  # Sealed class: WhiteWins, BlackWins, Draw, InProgress(checkSide)
│   └── GameTree.kt                    # Linear history: states list + currentIndex for back/forward navigation
├── ai/
│   ├── StockfishEngine.kt             # JNI bridge to native Stockfish (init, setOption, go, stop, goInfinite)
│   ├── StockfishAI.kt                 # High-level AI: difficulty/ELO, clock management, analysis, opening book lookup
│   ├── PolyglotBook.kt                # Binary opening book parser (PGBOOK00 format, SHA-256 hashed FEN lookup)
│   ├── OpeningBook.kt                 # Hardcoded opening book (UCI move sequence → weighted candidates)
│   └── (OpeningBookProvider.kt, BookProvider.kt, etc.)  # Book abstraction interfaces
└── ui/
    ├── GameScreen.kt                  # Main composable: layout, dialogs, delegates to ViewModel
    ├── GameViewModel.kt               # Central state: GameTree, GameUiState, clock, game mode, AI orchestration
    ├── AiEngineManager.kt             # Coroutine-based AI move execution + analysis loop
    ├── ClockEngineManager.kt          # Chess clock with configurable time + increment
    ├── GameModes.kt                   # GameMode enum (PLAY_WHITE/BLACK, TWO_PLAYERS, COMPUTER_VS_COMPUTER), NavMode
    ├── BoardEditor.kt                 # Board editor dialog for custom positions
    ├── components/                    # ChessBoard, ControlBar, ClockView, MoveList, Dialogs, etc.
    ├── util/                          # PgnUtils, StockfishInfoFormatter, BoardColors, MoveFeedback
    └── theme/Theme.kt                 # Material3 color scheme
```

### Domain Layer

- **`GameState`** is immutable — `ChessEngine.makeMove()` returns a new state. `copyWith()` provides named-parameter partial copies.
- **`ChessEngine`** is a Kotlin `object` with pure functions. Key APIs:
  - `getLegalMoves(state, color?)` — fully legal moves for a color
  - `makeMove(state, move)` — returns new GameState with game-over detection (checkmate, stalemate, 50-move, threefold repetition, insufficient material)
  - `isSquareAttacked(state, row, col, byColor)` — for check/castling detection
  - `fenToState(fen)` — returns `FenParseResult.Success`/`Error` with detailed validation
- **`GameTree`** stores a linear list of GameState snapshots with a `currentNodeIndex`. `addMove()` discards forward history; `goBack()`/`goForward()`/`goTo()` navigate without mutation.
- **`GameResult`** is a sealed class; `isGameOver` is `true` for all variants except `InProgress`.

### AI Layer

- **`StockfishEngine`** loads `libstockfish` via JNI and exposes native functions: `nativeInit`, `nativeSetOption`, `nativeSetPosition`, `nativeGo`, `nativeGoInfinite`, `nativeStop`, `nativeGetLatestInfo`, `nativeQuit`.
- **`StockfishAI`** (singleton object) wraps the engine with:
  - Difficulty levels (`BEGINNER` through `GRANDMASTER`) mapped to ELO + depth + move time
  - Clock-aware time management (`recordMove`, `setClocks`)
  - Unified opening book lookup (Polyglot binary + hardcoded `OpeningBook`)
  - `startAnalysis(state, multiPV, depth)` for infinite analysis mode
  - All AI search runs on `Dispatchers.IO` via coroutines in `AiEngineManager`
- **Opening book**: `PolyglotBook` loads a custom binary format (10-byte entries: 4B hash + 4B offset + 2B weight) from `assets/book.bin`. `OpeningBook` is a hardcoded fallback with ~100+ common opening lines. Both implement `OpeningBookProvider`.

### UI Layer

- **`GameViewModel`** (`AndroidViewModel`) owns all state via `MutableStateFlow`:
  - `GameTree` — current position and move history
  - `GameUiState` — selection, legal moves, AI thinking info, analysis PV lines, dialog visibility
  - `GameMode` — which sides are AI-controlled
  - `NavMode` — `PLAYING` (live game), `ANALYSIS` (infinite engine analysis), `EDITING` (board editor)
- State survives configuration changes via `SavedStateHandle` (FEN string saved/restored).
- **`AiEngineManager`** runs AI moves on `viewModelScope` with a 300ms delay; polls `getLatestInfo()` for real-time thinking output; handles AI-vs-AI loop.
- **`ClockEngineManager`** decrements clocks at 250ms intervals; supports configurable initial time + increment.
- **`GameScreen`** is the sole screen composable. It assembles `ControlBar`, `ClockBar`, `MoveListBar`, `CapturedPieces`, `ChessBoard`, `ThinkingPanel`, and all dialogs (mode, difficulty, time control, FEN/PGN import, board editor).

### Key Design Patterns

- **StateFlow over mutableStateOf**: All shared state lives in ViewModel StateFlows, not Composable-local `remember`.
- **No navigation library**: Single-activity, single-screen app with dialogs for secondary flows.
- **Proguard/R8**: Enabled for release builds with `proguard-android-optimize.txt`.
- **FEN is the interop format**: between Kotlin domain, Stockfish engine, clipboard, and saved state.
