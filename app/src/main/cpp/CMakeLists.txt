cmake_minimum_required(VERSION 3.22.1)
project("stockfish")

set(STOCKFISH_SRC
    stockfish/src/benchmark.cpp
    stockfish/src/bitboard.cpp
    stockfish/src/engine.cpp
    stockfish/src/evaluate.cpp
    stockfish/src/memory.cpp
    stockfish/src/misc.cpp
    stockfish/src/movegen.cpp
    stockfish/src/movepick.cpp
    stockfish/src/nnue/features/full_threats.cpp
    stockfish/src/nnue/features/half_ka_v2_hm.cpp
    stockfish/src/nnue/network.cpp
    stockfish/src/nnue/nnue_accumulator.cpp
    stockfish/src/nnue/nnue_misc.cpp
    stockfish/src/position.cpp
    stockfish/src/score.cpp
    stockfish/src/search.cpp
    stockfish/src/syzygy/tbprobe.cpp
    stockfish/src/thread.cpp
    stockfish/src/timeman.cpp
    stockfish/src/tt.cpp
    stockfish/src/tune.cpp
    stockfish/src/uci.cpp
    stockfish/src/ucioption.cpp
    com_jnz_chess_ai_StockfishEngine.cpp
)

add_library(stockfish SHARED ${STOCKFISH_SRC})

target_compile_features(stockfish PRIVATE cxx_std_17)

target_compile_definitions(stockfish PRIVATE
    NDEBUG
    USE_PTHREADS
    NO_NUMA
    NNUE_EMBEDDING_OFF
)

# Optimization: O3 is critical for Stockfish's NNUE evaluation speed
target_compile_options(stockfish PRIVATE -O3 -fno-exceptions -fno-rtti)

if(${ANDROID_ABI} STREQUAL "arm64-v8a")
    target_compile_definitions(stockfish PRIVATE
        USE_NEON=8
        USE_NEON_DOTPROD
    )
    target_compile_options(stockfish PRIVATE -march=armv8.2-a+dotprod)
elseif(${ANDROID_ABI} STREQUAL "x86_64")
    target_compile_definitions(stockfish PRIVATE
        USE_SSE2
        USE_POPCNT
    )
    target_compile_options(stockfish PRIVATE -msse2)
endif()

target_include_directories(stockfish PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/stockfish/src
    ${CMAKE_CURRENT_SOURCE_DIR}/stockfish/src/incbin
    ${CMAKE_CURRENT_BINARY_DIR}
)

target_link_libraries(stockfish
    android
    log
)
