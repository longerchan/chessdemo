package com.chessdemo.ai

object StockfishEngine {

    private const val TT_SIZE_MB = 64
    private const val THREADS = 4
    private var initialized = false
    var nativeLoaded = false
        private set

    init {
        try {
            System.loadLibrary("stockfish")
            nativeLoaded = true
        } catch (e: UnsatisfiedLinkError) {
            android.util.Log.e("StockfishEngine", "Failed to load native library: ${e.message}")
        }
    }

    @JvmStatic
    private external fun nativeInit(ttSizeMB: Int, threadCount: Int): Long

    @JvmStatic
    private external fun nativeSetOption(name: String, value: String)

    @JvmStatic
    private external fun nativeSetPosition(fen: String, moves: Array<String>): String

    @JvmStatic
    private external fun nativeGo(
        depth: Int,
        wTime: Int, bTime: Int,
        wInc: Int, bInc: Int,
        movestogo: Int, movetime: Int,
        nodes: Int, mate: Int,
        infinite: Boolean,
    ): String

    @JvmStatic
    private external fun nativeStop()

    @JvmStatic
    private external fun nativeGoInfinite(depth: Int, multiPV: Int): String

    @JvmStatic
    private external fun nativeGetLatestInfo(): String

    @JvmStatic
    private external fun nativeQuit()

    fun setOption(name: String, value: String) {
        nativeSetOption(name, value)
    }

    fun getLatestInfo(): String = nativeGetLatestInfo()

    fun initialize() {
        if (!initialized) {
            if (!nativeLoaded) throw IllegalStateException("Native library not loaded")
            nativeInit(TT_SIZE_MB, THREADS)
            initialized = true
        }
    }

    fun findBestMove(fen: String, moveHistoryUci: List<String>, depth: Int): String? {
        val result = nativeSetPosition(fen, moveHistoryUci.toTypedArray())
        if (result != "ok") return null

        val bestMoveUci = nativeGo(
            depth = depth,
            wTime = 0, bTime = 0,
            wInc = 0, bInc = 0,
            movestogo = 0, movetime = 0,
            nodes = 0, mate = 0,
            infinite = false,
        )
        return if (bestMoveUci == "none" || bestMoveUci.isEmpty()) null else bestMoveUci
    }

    fun findBestMoveTimed(
        fen: String,
        moveHistoryUci: List<String>,
        depth: Int,
        wTime: Int = 0, bTime: Int = 0,
        wInc: Int = 0, bInc: Int = 0,
        movestogo: Int = 0,
        movetime: Int = 0,
    ): String? {
        val result = nativeSetPosition(fen, moveHistoryUci.toTypedArray())
        if (result != "ok") return null

        val bestMoveUci = nativeGo(
            depth = depth,
            wTime = wTime, bTime = bTime,
            wInc = wInc, bInc = bInc,
            movestogo = movestogo, movetime = movetime,
            nodes = 0, mate = 0,
            infinite = false,
        )
        return if (bestMoveUci == "none" || bestMoveUci.isEmpty()) null else bestMoveUci
    }

    fun stop() {
        nativeStop()
    }

    /** Start infinite analysis. Sets MultiPV option before starting. */
    fun startAnalysis(fen: String, multiPV: Int = 3, depth: Int = 30): Boolean {
        setOption("MultiPV", multiPV.toString())
        val posResult = nativeSetPosition(fen, emptyArray())
        if (posResult != "ok") return false
        val result = nativeGoInfinite(depth = depth, multiPV = multiPV)
        return result == "ok"
    }

    fun stopAnalysis() {
        nativeStop()
    }

    fun shutdown() {
        nativeQuit()
        initialized = false
    }
}
