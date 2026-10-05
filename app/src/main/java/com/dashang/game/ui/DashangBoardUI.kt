package com.dashang.game.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dashang.game.model.BOARD_SIZE
import com.dashang.game.model.DashangEngine
import com.dashang.game.model.GameState
import com.dashang.game.model.GameStatus
import com.dashang.game.model.MoveKind
import com.dashang.game.model.Piece
import com.dashang.game.model.PieceColor
import com.dashang.game.model.PieceType
import com.dashang.game.model.Position

private val LightTile = Color(0xFFEBD9B4)
private val DarkTile = Color(0xFF9C6B3F)
private val SelectedTint = Color(0x883B82F6)
private val LastMoveTint = Color(0x66F6E05E)
private val CheckTint = Color(0xAAE53935)
private val StepDot = Color(0xCC2E7D32)
private val CaptureRing = Color(0xFFD32F2F)
private val SwapRing = Color(0xFF00ACC1)
private val TeleportDot = Color(0xCC8E24AA)

// ------------------------------------------------------------------ screen

@Composable
fun DashangScreen(
    state: GameUiState,
    onIntent: (GameIntent) -> Unit,
    modifier: Modifier = Modifier
) {
    val game = state.game
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PlayerPanel(PieceColor.BLACK, game)
        DashangBoard(
            state = state,
            onSquareTap = { onIntent(GameIntent.SquareTapped(it)) },
            modifier = Modifier.fillMaxWidth()
        )
        PlayerPanel(PieceColor.WHITE, game)
        StatusBadge(game, state.isBotThinking)
        state.hint?.let {
            Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.tertiary)
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("vs", fontSize = 13.sp)
            Opponent.values().forEach { opp ->
                FilterChip(
                    selected = state.opponent == opp,
                    onClick = { onIntent(GameIntent.SetOpponent(opp)) },
                    label = { Text(opp.label, fontSize = 12.sp) }
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { onIntent(GameIntent.Undo) },
                enabled = state.canUndo
            ) { Text("Undo") }
            Button(onClick = { onIntent(GameIntent.NewGame) }) { Text("New Game") }
        }
    }
}

// ------------------------------------------------------------------ badges

@Composable
private fun PlayerPanel(color: PieceColor, game: GameState) {
    val active = game.turn == color && game.status == GameStatus.ACTIVE
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                color.displayName,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        CooldownBadge("Agent", game.cooldowns.remaining(Piece(PieceType.AGENT, color)))
        CooldownBadge("Democrat", game.cooldowns.remaining(Piece(PieceType.DEMOCRAT, color)))
    }
}

@Composable
private fun CooldownBadge(label: String, remaining: Int) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (remaining == 0) Color(0xFF1F6F43) else Color(0xFF7A2E2E)
    ) {
        Text(
            text = if (remaining == 0) "$label: Ready" else "$label: wait $remaining",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            fontSize = 12.sp,
            color = Color.White
        )
    }
}

@Composable
private fun StatusBadge(game: GameState, botThinking: Boolean) {
    val text = when (game.status) {
        GameStatus.WHITE_WINS -> "Checkmate. White wins."
        GameStatus.BLACK_WINS -> "Checkmate. Black wins."
        GameStatus.STALEMATE -> "Stalemate. Draw."
        GameStatus.ACTIVE ->
            (if (game.inCheck) "Check! " else "") +
                "${game.turn.displayName} to move" + if (botThinking) " (AI thinking...)" else ""
    }
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

// ------------------------------------------------------------------ board

@Composable
fun DashangBoard(
    state: GameUiState,
    onSquareTap: (Position) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnTap by rememberUpdatedState(onSquareTap)
    val paint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    }

    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val cell = size.width / BOARD_SIZE.toFloat()
                    val file = (offset.x / cell).toInt()
                    val rank = BOARD_SIZE - 1 - (offset.y / cell).toInt() // white at the bottom
                    val pos = Position(file, rank)
                    if (pos.isValid) currentOnTap(pos)
                }
            }
    ) {
        val cell = size.width / BOARD_SIZE
        val game = state.game
        val checkedKing = if (game.inCheck) DashangEngine.findKing(game.board, game.turn) else null
        fun topLeft(p: Position) = Offset(p.file * cell, (BOARD_SIZE - 1 - p.rank) * cell)
        fun center(p: Position) = topLeft(p) + Offset(cell / 2, cell / 2)

        // 1) tiles + highlights + coordinates
        for (rank in 0 until BOARD_SIZE) for (file in 0 until BOARD_SIZE) {
            val pos = Position(file, rank)
            val isLight = (file + rank) % 2 == 1 // a1 is dark
            val tile = if (isLight) LightTile else DarkTile
            drawRect(tile, topLeft(pos), Size(cell, cell))
            val last = game.lastMove
            if (last != null && (last.from == pos || last.to == pos)) drawRect(LastMoveTint, topLeft(pos), Size(cell, cell))
            if (state.selected == pos) drawRect(SelectedTint, topLeft(pos), Size(cell, cell))
            if (checkedKing == pos) drawRect(CheckTint, topLeft(pos), Size(cell, cell))

            val labelColor = (if (isLight) DarkTile else LightTile).toArgb()
            if (file == 0) drawGlyph(paint, "${rank + 1}", topLeft(pos).x + cell * 0.04f, topLeft(pos).y + cell * 0.2f, cell * 0.17f, labelColor, Paint.Align.LEFT)
            if (rank == 0) drawGlyph(paint, "${'a' + file}", topLeft(pos).x + cell * 0.96f, topLeft(pos).y + cell * 0.95f, cell * 0.17f, labelColor, Paint.Align.RIGHT)
        }

        // 2) pieces
        for (i in game.board.indices) {
            val piece = game.board[i] ?: continue
            drawPiece(paint, piece, center(Position.fromIndex(i)), cell, game.cooldowns.remaining(piece))
        }

        // 3) legal-target markers on top (normal mode or swap mode)
        for (m in state.visibleMoves) {
            val c = center(m.to)
            val occupied = game.pieceAt(m.to) != null
            when {
                m.kind == MoveKind.SWAP ->
                    drawCircle(SwapRing, cell * 0.44f, c, style = Stroke(cell * 0.08f))
                occupied ->
                    drawCircle(CaptureRing, cell * 0.44f, c, style = Stroke(cell * 0.08f))
                m.kind == MoveKind.TELEPORT ->
                    drawCircle(TeleportDot, cell * 0.15f, c)
                else ->
                    drawCircle(StepDot, cell * 0.15f, c)
            }
        }
    }
}

private fun DrawScope.drawPiece(paint: Paint, piece: Piece, c: Offset, cell: Float, cooldown: Int) {
    val white = piece.color == PieceColor.WHITE
    val radius = cell * 0.38f
    val fill = if (white) Color(0xFFF4EFE6) else Color(0xFF2B2B33)
    val ring = when (piece.type) {
        PieceType.AGENT -> Color(0xFF2BB3A3)
        PieceType.DEMOCRAT -> Color(0xFFE07B26)
        else -> if (white) Color(0xFF8A8576) else Color(0xFF9A9AA8)
    }
    drawCircle(fill, radius, c)
    drawCircle(ring, radius, c, style = Stroke(cell * if (piece.type.isSpecial) 0.07f else 0.04f))
    drawGlyph(paint, piece.type.symbol, c.x, c.y, cell * 0.42f, if (white) 0xFF1E1E24.toInt() else 0xFFF1F1F5.toInt())

    if (piece.type.isSpecial && cooldown > 0) {
        val b = c + Offset(radius * 0.8f, -radius * 0.8f)
        drawCircle(Color(0xFFD32F2F), cell * 0.16f, b)
        drawGlyph(paint, "$cooldown", b.x, b.y, cell * 0.2f, Color.White.toArgb())
    }
}

private fun DrawScope.drawGlyph(
    paint: Paint, text: String, x: Float, y: Float, textSize: Float, argb: Int,
    align: Paint.Align = Paint.Align.CENTER
) {
    paint.textSize = textSize
    paint.color = argb
    paint.textAlign = align
    val baseline = y - (paint.ascent() + paint.descent()) / 2f
    drawContext.canvas.nativeCanvas.drawText(text, x, baseline, paint)
}