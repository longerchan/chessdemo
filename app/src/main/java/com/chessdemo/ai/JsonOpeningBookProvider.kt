package com.chessdemo.ai

import android.content.Context
import com.chessdemo.domain.GameState
import org.json.JSONObject

/** Loads an opening book from a JSON file in assets. */
class JsonOpeningBookProvider(private val context: Context, private val fileName: String = "opening_book.json") :
    OpeningBookProvider {

    private val book: Map<String, List<Pair<String, Int>>> by lazy { loadBook() }

    private fun loadBook(): Map<String, List<Pair<String, Int>>> {
        return try {
            val json = context.assets.open(fileName).bufferedReader().use { it.readText() }
            val obj = JSONObject(json)
            val keys = mutableListOf<String>()
            val iterator = obj.keys()
            while (iterator.hasNext()) keys.add(iterator.next())
            keys.associateWith { key ->
                val movesArray = obj.getJSONArray(key)
                (0 until movesArray.length()).map { i ->
                    val moveObj = movesArray.getJSONObject(i)
                    moveObj.getString("move") to moveObj.getInt("weight")
                }
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    override fun lookup(state: GameState): List<Pair<String, Int>> {
        val moveSequence = state.moveHistory.joinToString(" ") { moveToUci(it) }
        return book[moveSequence] ?: emptyList()
    }

    private fun moveToUci(move: com.chessdemo.domain.Move): String {
        val files = "abcdefgh"
        val fromRank = 8 - move.fromRow
        val toRank = 8 - move.toRow
        val from = "${files[move.fromCol]}$fromRank"
        val to = "${files[move.toCol]}$toRank"
        val promo = if (toRank == 1 || toRank == 8) {
            when (move.promotionType) {
                com.chessdemo.domain.PieceType.QUEEN -> "q"
                com.chessdemo.domain.PieceType.ROOK -> "r"
                com.chessdemo.domain.PieceType.BISHOP -> "b"
                com.chessdemo.domain.PieceType.KNIGHT -> "n"
                else -> "q"
            }
        } else ""
        return "$from$to$promo"
    }
}
