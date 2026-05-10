package com.chessdemo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chessdemo.ai.StockfishAI
import com.chessdemo.ui.NavMode
import com.chessdemo.ui.GameMode

@Composable
fun ThinkingPanel(info: String, analysisPvLines: Map<Int, String>, gameMode: GameMode, navMode: NavMode) {
    if (gameMode == GameMode.TWO_PLAYERS && navMode == NavMode.PLAYING) return

    val label = when (navMode) {
        NavMode.ANALYSIS -> "分析模式"
        else -> "Stockfish (${StockfishAI.difficulty.label})"
    }

    Card(
        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp, max = 200.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF888888))
            Spacer(Modifier.height(4.dp))
            if (navMode == NavMode.ANALYSIS && analysisPvLines.isNotEmpty()) {
                analysisPvLines.toSortedMap().forEach { (index, line) ->
                    val prefix = if (analysisPvLines.size > 1) "$index. " else ""
                    Text(text = "$prefix$line", fontSize = 12.sp, color = Color(0xFF00CC66),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, lineHeight = 15.sp)
                }
            } else if (info.isNotEmpty()) {
                Text(text = info, fontSize = 13.sp, color = Color(0xFF00CC66),
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, lineHeight = 16.sp)
            } else {
                Text(text = "Waiting...", fontSize = 13.sp, color = Color(0xFF00CC66),
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, lineHeight = 16.sp)
            }
        }
    }
}
