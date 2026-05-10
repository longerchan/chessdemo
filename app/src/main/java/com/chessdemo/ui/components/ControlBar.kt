package com.chessdemo.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chessdemo.ai.StockfishAI
import com.chessdemo.ui.NavMode
import com.chessdemo.ui.GameMode

@Composable
fun ControlBar(
    navMode: NavMode,
    onNavModeChange: (NavMode) -> Unit,
    gameMode: GameMode,
    onGameModeClick: () -> Unit,
    difficulty: StockfishAI.Difficulty,
    onDifficultyClick: () -> Unit,
    onReset: () -> Unit,
    onFlipBoard: () -> Unit,
    boardFlipped: Boolean,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onGoBack: () -> Unit,
    onGoForward: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
            shape = RoundedCornerShape(8.dp)) {
            Text("重置", fontSize = 12.sp)
        }
        OutlinedButton(onClick = onGoBack, enabled = canGoBack, modifier = Modifier.weight(0.7f),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
            shape = RoundedCornerShape(8.dp)) {
            Text("◀", fontSize = 14.sp)
        }
        OutlinedButton(onClick = onGoForward, enabled = canGoForward, modifier = Modifier.weight(0.7f),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
            shape = RoundedCornerShape(8.dp)) {
            Text("▶", fontSize = 14.sp)
        }
        OutlinedButton(onClick = onFlipBoard, modifier = Modifier.weight(0.7f),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
            shape = RoundedCornerShape(8.dp)) {
            Text("↕", fontSize = 14.sp)
        }
        OutlinedButton(
            onClick = {
                onNavModeChange(when (navMode) {
                    NavMode.PLAYING -> NavMode.ANALYSIS
                    NavMode.ANALYSIS -> NavMode.PLAYING
                    else -> NavMode.PLAYING
                })
            },
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            val label = when (navMode) {
                NavMode.PLAYING -> "对弈"; NavMode.ANALYSIS -> "分析"; NavMode.EDITING -> "编辑"
            }
            Text(label, fontSize = 12.sp)
        }
        OutlinedButton(onClick = onGameModeClick, modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
            shape = RoundedCornerShape(8.dp)) {
            val label = when (gameMode) {
                GameMode.PLAY_WHITE -> "执白"; GameMode.PLAY_BLACK -> "执黑"
                GameMode.TWO_PLAYERS -> "双人"; GameMode.COMPUTER_VS_COMPUTER -> "机机"
            }
            Text(label, fontSize = 12.sp)
        }
        val diffColor = when (difficulty) {
            StockfishAI.Difficulty.BEGINNER -> MaterialTheme.colorScheme.primary
            StockfishAI.Difficulty.EASY -> Color(0xFF4CAF50)
            StockfishAI.Difficulty.MEDIUM -> Color(0xFFFF9800)
            StockfishAI.Difficulty.HARD -> Color(0xFFE91E63)
            StockfishAI.Difficulty.GRANDMASTER -> Color(0xFF9C27B0)
        }
        OutlinedButton(onClick = onDifficultyClick, modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
            shape = RoundedCornerShape(8.dp)) {
            Text(difficulty.label, fontSize = 12.sp, color = diffColor)
        }
    }
}
