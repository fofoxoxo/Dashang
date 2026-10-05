package com.dashang.game.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dashang.game.model.*

@Composable
fun DashangBoardUI(
    engine: DashangEngine,
    onMoveExecuted: () -> Unit
) {
    var selectedPos by remember { mutableStateOf<Position?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Status Bar
        Text(text = "Turn: ${engine.turn}", fontSize = 20.sp)
        Text(text = "W-Agent: ${engine.cooldowns.whiteAgent} | W-Democrat: ${engine.cooldowns.whiteDemocrat}", fontSize = 14.sp)
        Text(text = "B-Agent: ${engine.cooldowns.blackAgent} | B-Democrat: ${engine.cooldowns.blackDemocrat}", fontSize = 14.sp)

        Spacer(modifier = Modifier.height(16.dp))

        // 10x10 Interactive Board
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        ) {
            val tileSize = constraints.maxWidth / 10f

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val col = (offset.x / tileSize).toInt().coerceIn(0, 9)
                            val row = (offset.y / tileSize).toInt().coerceIn(0, 9)
                            val tappedPos = Position(row, col)

                            if (selectedPos == null) {
                                if (engine.board[row][col]?.color == engine.turn) {
                                    selectedPos = tappedPos
                                }
                            } else {
                                val from = selectedPos!!
                                if (engine.isValidMove(from, tappedPos)) {
                                    engine.makeMove(from, tappedPos)
                                    onMoveExecuted()
                                }
                                selectedPos = null
                            }
                        }
                    }
            ) {
                // Draw 10x10 Grid Tiles
                for (row in 0..9) {
                    for (col in 0..9) {
                        val isLight = (row + col) % 2 == 0
                        val color = if (isLight) Color(0xFFEEEED2) else Color(0xFF769656)

                        drawRect(
                            color = color,
                            topLeft = Offset(col * tileSize, row * tileSize),
                            size = Size(tileSize, tileSize)
                        )

                        // Highlight Selection
                        if (selectedPos?.row == row && selectedPos?.col == col) {
                            drawRect(
                                color = Color.Yellow.copy(alpha = 0.5f),
                                topLeft = Offset(col * tileSize, row * tileSize),
                                size = Size(tileSize, tileSize)
                            )
                        }

                        // Draw Piece Labels
                        val piece = engine.board[row][col]
                        if (piece != null) {
                            val textColor = if (piece.color == PieceColor.WHITE) Color.White else Color.Black
                            // Note: Production UI uses SVG/PNG Drawables instead of text
                        }
                    }
                }
            }
        }
    }
}
