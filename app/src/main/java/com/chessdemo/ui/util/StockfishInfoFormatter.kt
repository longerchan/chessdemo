package com.chessdemo.ui.util

import com.chessdemo.domain.PieceType

fun formatStockfishInfo(info: String): String {
    val parts = info.split(" ")
    val depth = extractValue(parts, "depth")
    val seldepth = extractValue(parts, "seldepth")
    val nodes = extractValue(parts, "nodes")
    val nps = extractValue(parts, "nps")
    val time = extractValue(parts, "time")

    val scoreStr = when {
        parts.contains("cp") -> {
            val idx = parts.indexOf("cp")
            val cp = parts.getOrNull(idx + 1)?.toIntOrNull() ?: 0
            "eval: ${String.format("%.2f", cp / 100.0)}"
        }
        parts.contains("mate") -> {
            val idx = parts.indexOf("mate")
            val plies = parts.getOrNull(idx + 1) ?: "?"
            "mate in $plies"
        }
        else -> ""
    }

    val nodesStr = nodes.toLongOrNull()?.let {
        if (it >= 1_000_000) String.format("%.1fM", it / 1_000_000.0)
        else if (it >= 1_000) String.format("%.1fK", it / 1_000.0) else "$it"
    } ?: ""

    val npsStr = nps.toLongOrNull()?.let {
        if (it >= 1_000_000) String.format("%.1fMn/s", it / 1_000_000.0)
        else String.format("%dKn/s", it / 1_000)
    } ?: ""

    val timeMs = time.toLongOrNull()?.let {
        if (it >= 1000) String.format("%.1fs", it / 1000.0) else "${it}ms"
    } ?: ""

    val pv = parts.indexOf("pv")
    val pvMoves = if (pv >= 0) {
        parts.subList(pv + 1, parts.size).take(10).joinToString(" ") { formatUciMove(it) }
    } else ""

    return buildString {
        append("depth $depth")
        if (seldepth.isNotEmpty()) append("/$seldepth")
        append("  $scoreStr\n")
        append("nodes $nodesStr  $npsStr  $timeMs")
        if (pvMoves.isNotEmpty()) append("\n").append("pv: $pvMoves")
    }
}

fun formatUciMove(uci: String): String {
    return if (uci.length >= 4) {
        "${uci.substring(0, 2)}→${uci.substring(2, 4)}" +
            if (uci.length >= 5) "=${uci[4].uppercase()}" else ""
    } else uci
}

fun extractMultiPvIndex(info: String): Int? {
    val parts = info.split(" ")
    val idx = parts.indexOf("multipv")
    if (idx < 0 || idx + 1 >= parts.size) return null
    return parts[idx + 1].toIntOrNull()
}

fun extractEvalCp(info: String, pvLines: Map<Int, String>): Int? {
    // Parse from formatted PV line: "d22  +0.20  e2→e4"
    pvLines[1]?.let { line ->
        Regex("""[+-]?\d+\.\d{2}""").find(line)?.let {
            return (it.value.toDouble() * 100).toInt()
        }
    }
    // Parse from formatted thinkingInfo: "eval: 0.20"
    val evalPrefix = "eval: "
    val evalIdx = info.indexOf(evalPrefix)
    if (evalIdx >= 0) {
        val after = info.substring(evalIdx + evalPrefix.length).trimStart()
        val end = after.indexOfAny(charArrayOf(' ', '\n', '\r'))
        val evalStr = if (end >= 0) after.substring(0, end) else after
        return (evalStr.toDoubleOrNull()?.let { (it * 100).toInt() })
    }
    // Fallback: scan entire string for a decimal
    Regex("""[+-]?\d+\.\d{2}""").find(info)?.let {
        return (it.value.toDouble() * 100).toInt()
    }
    return null
}

fun formatAnalysisLine(info: String): String {
    val parts = info.split(" ")
    val depth = extractValue(parts, "depth")
    val scoreStr = when {
        parts.contains("cp") -> {
            val cpIdx = parts.indexOf("cp")
            val cp = parts.getOrNull(cpIdx + 1)?.toIntOrNull() ?: 0
            String.format("+%.2f", cp / 100.0)
        }
        parts.contains("mate") -> {
            val mateIdx = parts.indexOf("mate")
            val plies = parts.getOrNull(mateIdx + 1) ?: "?"
            "#$plies"
        }
        else -> ""
    }
    val pvIdx = parts.indexOf("pv")
    val pvMoves = if (pvIdx >= 0) {
        parts.subList(pvIdx + 1, parts.size).take(6).joinToString(" ") { formatUciMove(it) }
    } else ""
    return if (scoreStr.isNotEmpty() && pvMoves.isNotEmpty()) {
        "d$depth  $scoreStr  $pvMoves"
    } else ""
}

fun extractValue(parts: List<String>, key: String): String {
    val idx = parts.indexOf(key)
    if (idx < 0 || idx + 1 >= parts.size) return ""
    return parts[idx + 1]
}
