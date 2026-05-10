package com.chessdemo.domain

data class Move(
    val fromRow: Int,
    val fromCol: Int,
    val toRow: Int,
    val toCol: Int,
    val promotionType: PieceType = PieceType.QUEEN,
    val isCastling: Boolean = false,
    val isEnPassant: Boolean = false,
) {
    override fun toString(): String {
        val files = "abcdefgh"
        val from = "${files[fromCol]}${8 - fromRow}"
        val to = "${files[toCol]}${8 - toRow}"
        val promo = if (promotionType != PieceType.QUEEN) "=${promotionType.symbol}" else ""
        return "$from$to$promo"
    }
}
