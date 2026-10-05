package com.dashang.game.model

import kotlin.math.abs

// 1. Piece Types & Colors
enum class PieceColor { WHITE, BLACK }

enum class PieceType(val code: String) {
    PAWN("P"),
    ROOK("R"),
    KNIGHT("N"),
    BISHOP("B"),
    QUEEN("Q"),
    KING("K"),
    AGENT("A"),       // Special Unit (Corner)
    DEMOCRAT("D")     // Special Unit (Corner)
}

data class Piece(
    val type: PieceType,
    val color: PieceColor
)

data class Position(val row: Int, val col: Int)

// 2. Cooldown State Tracker (5-Move Rule)
data class CooldownState(
    val whiteAgent: Int = 0,
    val whiteDemocrat: Int = 0,
    val blackAgent: Int = 0,
    val blackDemocrat: Int = 0
)

// 3. Game Board Engine State
class DashangEngine {
    val board = Array(10) { Array<Piece?>(10) { null } }
    var turn = PieceColor.WHITE
    var cooldowns = CooldownState()

    init {
        setupBoard()
    }

    private fun setupBoard() {
        // Black Back-Rank (Row 0)
        board[0][0] = Piece(PieceType.AGENT, PieceColor.BLACK)
        board[0][1] = Piece(PieceType.ROOK, PieceColor.BLACK)
        board[0][2] = Piece(PieceType.KNIGHT, PieceColor.BLACK)
        board[0][3] = Piece(PieceType.BISHOP, PieceColor.BLACK)
        board[0][4] = Piece(PieceType.QUEEN, PieceColor.BLACK)
        board[0][5] = Piece(PieceType.KING, PieceColor.BLACK)
        board[0][6] = Piece(PieceType.BISHOP, PieceColor.BLACK)
        board[0][7] = Piece(PieceType.KNIGHT, PieceColor.BLACK)
        board[0][8] = Piece(PieceType.ROOK, PieceColor.BLACK)
        board[0][9] = Piece(PieceType.DEMOCRAT, PieceColor.BLACK)

        // Black Pawns (Row 1)
        for (col in 0..9) board[1][col] = Piece(PieceType.PAWN, PieceColor.BLACK)

        // White Pawns (Row 8)
        for (col in 0..9) board[8][col] = Piece(PieceType.PAWN, PieceColor.WHITE)

        // White Back-Rank (Row 9)
        board[9][0] = Piece(PieceType.AGENT, PieceColor.WHITE)
        board[9][1] = Piece(PieceType.ROOK, PieceColor.WHITE)
        board[9][2] = Piece(PieceType.KNIGHT, PieceColor.WHITE)
        board[9][3] = Piece(PieceType.BISHOP, PieceColor.WHITE)
        board[9][4] = Piece(PieceType.QUEEN, PieceColor.WHITE)
        board[9][5] = Piece(PieceType.KING, PieceColor.WHITE)
        board[9][6] = Piece(PieceType.BISHOP, PieceColor.WHITE)
        board[9][7] = Piece(PieceType.KNIGHT, PieceColor.WHITE)
        board[9][8] = Piece(PieceType.ROOK, PieceColor.WHITE)
        board[9][9] = Piece(PieceType.DEMOCRAT, PieceColor.WHITE)
    }

    // Move Validation Logic
    fun isValidMove(from: Position, to: Position): Boolean {
        val piece = board[from.row][from.col] ?: return false
        if (piece.color != turn) return false
        val target = board[to.row][to.col]

        // Target cannot be own piece (Except Agent Swap)
        if (target != null && target.color == piece.color && piece.type != PieceType.AGENT) return false

        return when (piece.type) {
            PieceType.PAWN -> validatePawnMove(from, to, piece.color, target)
            PieceType.DEMOCRAT -> validateDemocratMove(to, piece.color)
            PieceType.AGENT -> validateAgentSwap(from, to, piece.color)
            else -> validateStandardCaptureRestrictions(target)
        }
    }

    private fun validatePawnMove(from: Position, to: Position, color: PieceColor, target: Piece?): Boolean {
        val direction = if (color == PieceColor.WHITE) -1 else 1
        val rowDiff = to.row - from.row
        val colDiff = abs(to.col - from.col)

        // Regular 1-step forward
        if (colDiff == 0 && rowDiff == direction && target == null) return true

        // Standard Diagonal Capture
        if (colDiff == 1 && rowDiff == direction && target != null) {
            // Special Units are IMMUNE to Rook/Knight/Bishop/Queen, but CAN be captured by Pawn/King
            return true
        }

        // DUAL ATTACK VECTOR: Pawn straight elimination for Democrat & Agent
        if (colDiff == 0 && rowDiff == direction && target != null) {
            return target.type == PieceType.DEMOCRAT || target.type == PieceType.AGENT
        }

        return false
    }

    private fun validateDemocratMove(to: Position, color: PieceColor): Boolean {
        val currentCooldown = if (color == PieceColor.WHITE) cooldowns.whiteDemocrat else cooldowns.blackDemocrat
        if (currentCooldown > 0) return false

        // Democrat CANNOT capture direct, lands only on EMPTY squares
        return board[to.row][to.col] == null
    }

    private fun validateAgentSwap(from: Position, to: Position, color: PieceColor): Boolean {
        val currentCooldown = if (color == PieceColor.WHITE) cooldowns.whiteAgent else cooldowns.blackAgent
        if (currentCooldown > 0) return false

        val target = board[to.row][to.col] ?: return false
        
        // Agent can SWAP ONLY with own Rook, Knight, or Bishop
        if (target.color != color) return false
        return target.type == PieceType.ROOK || target.type == PieceType.KNIGHT || target.type == PieceType.BISHOP
    }

    private fun validateStandardCaptureRestrictions(target: Piece?): Boolean {
        if (target == null) return true
        
        // Democrat & Agent IMMUNITY: Regular pieces cannot capture them
        if (target.type == PieceType.DEMOCRAT || target.type == PieceType.AGENT) {
            return false // Only Pawns & King can eliminate special units
        }
        return true
    }

    fun makeMove(from: Position, to: Position) {
        val piece = board[from.row][from.col] ?: return
        val target = board[to.row][to.col]

        // Handle Agent Swap
        if (piece.type == PieceType.AGENT && target != null && target.color == piece.color) {
            board[from.row][from.col] = target
            board[to.row][to.col] = piece
            resetAgentCooldown(piece.color)
        } else {
            // Handle Democrat Teleport
            if (piece.type == PieceType.DEMOCRAT) {
                resetDemocratCooldown(piece.color)
            }
            board[to.row][to.col] = piece
            board[from.row][from.col] = null
        }

        decrementCooldowns()
        turn = if (turn == PieceColor.WHITE) PieceColor.BLACK else PieceColor.WHITE
    }

    private fun resetAgentCooldown(color: PieceColor) {
        cooldowns = if (color == PieceColor.WHITE) cooldowns.copy(whiteAgent = 5) else cooldowns.copy(blackAgent = 5)
    }

    private fun resetDemocratCooldown(color: PieceColor) {
        cooldowns = if (color == PieceColor.WHITE) cooldowns.copy(whiteDemocrat = 5) else cooldowns.copy(blackDemocrat = 5)
    }

    private fun decrementCooldowns() {
        if (turn == PieceColor.WHITE) {
            cooldowns = cooldowns.copy(
                whiteAgent = (cooldowns.whiteAgent - 1).coerceAtLeast(0),
                whiteDemocrat = (cooldowns.whiteDemocrat - 1).coerceAtLeast(0)
            )
        } else {
            cooldowns = cooldowns.copy(
                blackAgent = (cooldowns.blackAgent - 1).coerceAtLeast(0),
                blackDemocrat = (cooldowns.blackDemocrat - 1).coerceAtLeast(0)
            )
        }
    }
}
