package com.chessdemo.ai

import android.content.Context
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.security.MessageDigest

/**
 * Polyglot-style opening book using a binary file format.
 *
 * File format per entry (10 bytes):
 *   4 bytes: position hash (big-endian int, unsigned) — SHA-256 of FEN board part prefix
 *   4 bytes: move UCI string offset (index into string pool)
 *   2 bytes: weight (1–65535)
 *
 * Followed by a null-terminated string pool of UCI move strings.
 * Header: 8 bytes magic ("PGBOOK00"), 4 bytes entry count, 4 bytes string pool size.
 *
 * In-memory: entries are packed into a LongArray as (hash << 32) | (offset << 16) | weight.
 */
object PolyglotBook {

    private var entries: LongArray? = null // packed: (hash << 32) | (offset << 16) | weight
    private var moveStrings: List<String>? = null
    private var loaded = false

    data class BookMove(val uci: String, val weight: Int)

    /** Load book from assets. Returns true on success. */
    fun loadFromAssets(context: Context, fileName: String = "book.bin"): Boolean {
        return try {
            context.assets.open(fileName).use { input ->
                val buf = input.readBytes()
                parseBinary(buf)
            }
            loaded = true
            android.util.Log.d("PolyglotBook", "Loaded ${entries?.size ?: 0} entries")
            true
        } catch (e: Exception) {
            android.util.Log.w("PolyglotBook", "Failed to load $fileName: ${e.message}")
            false
        }
    }

    /** Load book from an external File. */
    fun loadFromFile(file: File): Boolean {
        return try {
            FileChannel.open(file.toPath()).use { channel ->
                val buf = ByteBuffer.allocate(channel.size().toInt())
                channel.read(buf)
                buf.flip()
                parseBinary(buf.array())
            }
            loaded = true
            true
        } catch (e: Exception) {
            android.util.Log.w("PolyglotBook", "Failed to load ${file.name}: ${e.message}")
            false
        }
    }

    /** Look up book moves for a given FEN position. */
    fun lookupByFen(fen: String): List<BookMove> {
        if (!loaded) return emptyList()
        val hash = (computeHash(fen) ushr 32) and 0xFFFFFFFFL
        val ents = entries ?: return emptyList()
        val strings = moveStrings ?: return emptyList()

        // Binary search for hash
        val count = ents.size
        var lo = 0
        var hi = count - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val entryHash = (ents[mid] ushr 32) and 0xFFFFFFFFL
            when {
                entryHash < hash -> lo = mid + 1
                entryHash > hash -> hi = mid - 1
                else -> {
                    // Found — collect all entries with this hash
                    val results = mutableListOf<BookMove>()
                    var i = mid
                    while (i >= 0 && ((ents[i] ushr 32) and 0xFFFFFFFFL) == hash) {
                        results.add(decodeEntry(ents[i], strings))
                        i--
                    }
                    i = mid + 1
                    while (i < count && ((ents[i] ushr 32) and 0xFFFFFFFFL) == hash) {
                        results.add(decodeEntry(ents[i], strings))
                        i++
                    }
                    return results
                }
            }
        }
        return emptyList()
    }

    /** Check if book is loaded. */
    fun isLoaded(): Boolean = loaded

    /** Adapter for BookProvider / OpeningBookProvider interface. Polyglot uses FEN-based lookup. */
    fun lookup(state: com.chessdemo.domain.GameState): List<Pair<String, Int>> {
        return lookupByState(state)
    }

    /** Look up moves for a GameState using FEN from StockfishAI. */
    fun lookupByState(state: com.chessdemo.domain.GameState): List<Pair<String, Int>> {
        if (!loaded) return emptyList()
        val fen = com.chessdemo.ai.StockfishAI.stateToFenPublic(state)
        return lookupByFen(fen).map { it.uci to it.weight }
    }

    /** Clear loaded book. */
    fun close() {
        entries = null
        moveStrings = null
        loaded = false
    }

    // --- Internal ---

    /** Compute a hash from the full FEN string. */
    private fun computeHash(fen: String): Long {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(fen.toByteArray(Charsets.UTF_8))
        // Take first 8 bytes as a long (big-endian). Upper 32 bits match the stored buf.int hash.
        var hash = 0L
        for (i in 0 until 8) {
            hash = (hash shl 8) or (bytes[i].toLong() and 0xFFL)
        }
        return hash
    }

    private fun parseBinary(data: ByteArray) {
        val buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)

        // Header
        val magic = ByteArray(8)
        buf.get(magic)
        if (!magic.contentEquals("PGBOOK00".toByteArray())) {
            throw IllegalArgumentException("Invalid magic: ${magic.decodeToString()}")
        }

        val entryCount = buf.int
        val stringPoolSize = buf.int

        // Read string pool
        val stringPoolData = ByteArray(stringPoolSize)
        buf.get(stringPoolData)
        moveStrings = parseStringPool(stringPoolData)

        // Read entries
        entries = LongArray(entryCount)
        for (i in 0 until entryCount) {
            val hash = buf.int.toLong() and 0xFFFFFFFFL
            val offset = buf.short.toInt() and 0xFFFF
            val weight = buf.short.toInt() and 0xFFFF
            // Skip 2 bytes padding
            buf.short
            entries!![i] = (hash shl 32) or ((offset.toLong() and 0xFFFFL) shl 16) or (weight.toLong() and 0xFFFFL)
        }
    }

    private fun parseStringPool(data: ByteArray): List<String> {
        val strings = mutableListOf<String>()
        val sb = StringBuilder()
        for (byte in data) {
            if (byte == 0.toByte()) {
                strings.add(sb.toString())
                sb.clear()
            } else {
                sb.append(byte.toChar())
            }
        }
        return strings
    }

    private fun decodeEntry(packed: Long, strings: List<String>): BookMove {
        val offset = ((packed ushr 16) and 0xFFFFL).toInt()
        val weight = (packed and 0xFFFFL).toInt()
        val uci = strings.getOrElse(offset) { "unknown" }
        return BookMove(uci, weight)
    }
}
