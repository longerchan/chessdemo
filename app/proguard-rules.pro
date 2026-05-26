# Add project specific ProGuard rules here.

# Stockfish JNI - keep class and native methods to prevent UnsatisfiedLinkError
-keep class com.jnz.chess.ai.StockfishEngine { *; }
-keepclassmembers class com.jnz.chess.ai.StockfishEngine {
    native <methods>;
}

# Domain classes used by JNI or reflection
-keep class com.jnz.chess.domain.** { *; }
-keep class com.jnz.chess.domain.Color { *; }
-keep class com.jnz.chess.domain.PieceType { *; }
