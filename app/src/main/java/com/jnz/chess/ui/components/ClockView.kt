package com.jnz.chess.ui.components

import androidx.compose.foundation.clickable
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

@Composable
fun ClockBar(
    whiteTimeMs: Long, blackTimeMs: Long,
    whiteActive: Boolean, blackActive: Boolean,
    isWhiteTurn: Boolean,
    onWhiteClick: () -> Unit, onBlackClick: () -> Unit,
    boardFlipped: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val (topTime, topActive, topOnClick) = if (boardFlipped) {
            Triple(whiteTimeMs, whiteActive, onWhiteClick)
        } else {
            Triple(blackTimeMs, blackActive, onBlackClick)
        }
        val (bottomTime, bottomActive, bottomOnClick) = if (boardFlipped) {
            Triple(blackTimeMs, blackActive, onBlackClick)
        } else {
            Triple(whiteTimeMs, whiteActive, onWhiteClick)
        }
        ClockDisplay(timeMs = topTime, isActive = topActive, modifier = Modifier.clickable { topOnClick() })
        ClockDisplay(timeMs = bottomTime, isActive = bottomActive, modifier = Modifier.clickable { bottomOnClick() })
    }
}

@Composable
fun ClockDisplay(timeMs: Long, isActive: Boolean, modifier: Modifier = Modifier) {
    val mins = timeMs / 60000
    val secs = (timeMs % 60000) / 1000
    val bgColor = if (isActive) Color(0xFF4CAF50) else Color(0xFF333333)
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = bgColor), shape = RoundedCornerShape(6.dp)) {
        Text(
            text = String.format(java.util.Locale.US, "%d:%02d", mins, secs),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        )
    }
}
