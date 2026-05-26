package com.jnz.chess.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import com.jnz.chess.ai.StockfishAI
import com.jnz.chess.ui.GameMode

@Composable
fun DifficultyDialog(currentDifficulty: StockfishAI.Difficulty, onDismiss: () -> Unit, onSelect: (StockfishAI.Difficulty) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择难度", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                StockfishAI.Difficulty.entries.forEach { diff ->
                    val color = when (diff) {
                        StockfishAI.Difficulty.BEGINNER -> MaterialTheme.colorScheme.primary
                        StockfishAI.Difficulty.EASY -> Color(0xFF4CAF50)
                        StockfishAI.Difficulty.MEDIUM -> Color(0xFFFF9800)
                        StockfishAI.Difficulty.HARD -> Color(0xFFE91E63)
                        StockfishAI.Difficulty.GRANDMASTER -> Color(0xFF9C27B0)
                    }
                    val isSelected = diff == currentDifficulty
                    Row(modifier = Modifier.fillMaxWidth().clickable { onSelect(diff) }.padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(text = diff.label, fontSize = 16.sp,
                            color = if (isSelected) color else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        Spacer(Modifier.weight(1f))
                        if (isSelected) Text("✓", color = color, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {}, dismissButton = {}
    )
}

@Composable
fun GameModeDialog(currentMode: GameMode, onDismiss: () -> Unit, onSelect: (GameMode) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择模式", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                val options = listOf(
                    GameMode.PLAY_WHITE to "人执白 vs AI", GameMode.PLAY_BLACK to "AI vs 人执黑",
                    GameMode.TWO_PLAYERS to "双人对弈", GameMode.COMPUTER_VS_COMPUTER to "AI vs AI",
                )
                options.forEach { (mode, label) ->
                    val isSelected = mode == currentMode
                    Row(modifier = Modifier.fillMaxWidth().clickable { onSelect(mode) }.padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(text = label, fontSize = 16.sp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        Spacer(Modifier.weight(1f))
                        if (isSelected) Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {}, dismissButton = {}
    )
}

@Composable
fun TimeControlDialog(initialMinutes: Long, initialIncrement: Long, onDismiss: () -> Unit,
    onApply: (minutes: Long, increment: Long, movesToGo: Int) -> Unit) {
    var minutes by remember { mutableStateOf(initialMinutes.toInt()) }
    var increment by remember { mutableStateOf(initialIncrement.toInt()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("时间控制", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("每方时间（分钟）:")
                BasicTextField(value = minutes.toString(), onValueChange = { minutes = it.toIntOrNull() ?: minutes },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                Text("每步加时（秒）:")
                BasicTextField(value = increment.toString(), onValueChange = { increment = it.toIntOrNull() ?: increment },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
            }
        },
        confirmButton = { Button(onClick = { onApply(minutes.toLong(), increment.toLong(), 0) }) { Text("确定") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun FenImportDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    var fen by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入 FEN", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("输入 FEN 字符串:")
                Spacer(Modifier.height(8.dp))
                BasicTextField(value = fen, onValueChange = { fen = it },
                    modifier = Modifier.fillMaxWidth().height(80.dp),
                    textStyle = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                    decorationBox = { innerTextField ->
                        Box(modifier = Modifier.border(1.dp, Color.Gray, RoundedCornerShape(4.dp)).padding(8.dp)) {
                            innerTextField()
                        }
                    })
            }
        },
        confirmButton = {
            Button(onClick = { keyboardController?.hide(); if (fen.trim().isNotEmpty()) onImport(fen.trim()) },
                enabled = fen.trim().isNotEmpty()) { Text("导入") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun PgnImportDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    var pgnText by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入 PGN", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("粘贴 PGN 文本:")
                Spacer(Modifier.height(8.dp))
                BasicTextField(value = pgnText, onValueChange = { pgnText = it },
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    textStyle = TextStyle(fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                    decorationBox = { innerTextField ->
                        Box(modifier = Modifier.border(1.dp, Color.Gray, RoundedCornerShape(4.dp)).padding(8.dp)) {
                            innerTextField()
                        }
                    })
            }
        },
        confirmButton = {
            Button(onClick = { keyboardController?.hide(); if (pgnText.trim().isNotEmpty()) onImport(pgnText.trim()) },
                enabled = pgnText.trim().isNotEmpty()) { Text("导入") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("取消") } }
    )
}
