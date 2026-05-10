# Add project specific ProGuard rules here.

# Stockfish JNI - keep class and native methods to prevent UnsatisfiedLinkError
-keep class com.chessdemo.ai.StockfishEngine { *; }
-keepclassmembers class com.chessdemo.ai.StockfishEngine {
    native <methods>;
}

# Domain classes used by JNI or reflection
-keep class com.chessdemo.domain.** { *; }
-keep class com.chessdemo.domain.Color { *; }
-keep class com.chessdemo.domain.PieceType { *; }
