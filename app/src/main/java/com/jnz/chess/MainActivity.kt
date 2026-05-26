package com.jnz.chess

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jnz.chess.ui.GameScreen
import com.jnz.chess.ui.GameViewModel
import com.jnz.chess.ui.theme.ChessDemoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChessDemoTheme {
                val viewModel: GameViewModel = viewModel()
                GameScreen(viewModel = viewModel)
            }
        }
    }
}
