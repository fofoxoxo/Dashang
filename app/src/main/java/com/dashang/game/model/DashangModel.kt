package com.dashang.game.model

const val BOARD_SIZE = 10
const val ABILITY_COOLDOWN_TURNS = 5

enum class PieceColor(val displayName: String) {
    WHITE("White"),
    BLACK("Black");

    val opposite: PieceColor get() = if (this == WHITE) BLACK else WHITE
    val pawnDirection: Int get() = if (this == WHITE) 1 else -1
    val pawnStartRank: Int get() = if (this == WHITE) 1 else BOARD_SIZE - 2
    val promotionRank: Int get() = if (this == WHITE) BOARD_SIZE - 1 else 0
}

enum class PieceType(val symbol: String, val value: Int) {
    PAWN("P", 100),
    KNIGHT("N", 320),
    BISHOP("B", 330),
    ROOK("R", 500),
    QUEEN("Q", 900),
    KING("K", 0),
    AGENT("A", 350),
    DEMOCRAT("D", 350);

    val isSpecial: Boolean get() = this == AGENT || this == DEMOCRAT
}

data class Piece(val type: PieceType, val color: PieceColor)

/** file 0..9 = a..j, rank 0..9 = rank 1..10. */
data class Position(val file: Int, val rank: Int) {
    val isValid: Boolean get() = file in 0 until BOARD_SIZE && rank in 0 until BOARD_SIZE
    val index: Int get() = rank * BOARD_SIZE + file

    fun offset(df: Int, dr: Int) = Position(file + df, rank + dr)

    override fun toString() = "${'a' + file}${rank + 1}"

    companion object {
        fun fromIndex(i: Int) = Position(i % BOARD_SIZE, i / BOARD_SIZE)
    }
}

typealias Board = List<Piece?>

/**
 * Ability cooldowns, keyed by the special piece (each side has exactly one Agent and one Democrat).
 * A value N means "N more of this player's turns must pass before the ability is usable again".
 */
data class CooldownState(val turns: Map<Piece, Int> = emptyMap()) {
    fun remaining(piece: Piece): Int = turns[piece] ?: 0
    fun isReady(piece: Piece): Boolean = remaining(piece) == 0

    /** Called when [color] finishes a turn. */
    fun tick(color: PieceColor): CooldownState = CooldownState(
        turns.mapValues { (p, v) -> if (p.color == color && v > 0) v - 1 else v }
    )

    fun start(piece: Piece, duration: Int = ABILITY_COOLDOWN_TURNS): CooldownState =
        CooldownState(turns + (piece to duration))
}

enum class MoveKind { STEP, SWAP, TELEPORT }

data class Move(val from: Position, val to: Position, val kind: MoveKind = MoveKind.STEP)

enum class GameStatus { ACTIVE, WHITE_WINS, BLACK_WINS, STALEMATE }

data class GameState(
    val board: Board,
    val turn: PieceColor = PieceColor.WHITE,
    val cooldowns: CooldownState = CooldownState(),
    val status: GameStatus = GameStatus.ACTIVE,
    val inCheck: Boolean = false,
    val lastMove: Move? = null
) {
    fun pieceAt(pos: Position): Piece? = if (pos.isValid) board[pos.index] else null
}