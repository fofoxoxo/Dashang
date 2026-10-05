package com.dashang.game.model

import com.dashang.game.model.PieceType.*

/** Pure, stateless rules engine. All functions take and return immutable values. */
object DashangEngine {

    private val KNIGHT_OFFSETS = listOf(
        1 to 2, 2 to 1, 2 to -1, 1 to -2, -1 to -2, -2 to -1, -2 to 1, -1 to 2
    )
    private val ORTHOGONAL = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
    private val DIAGONAL = listOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)
    private val ALL_DIRS = ORTHOGONAL + DIAGONAL

    // ---------------------------------------------------------------- setup

    fun initialState(): GameState {
        val back = listOf(AGENT, ROOK, KNIGHT, BISHOP, QUEEN, KING, BISHOP, KNIGHT, ROOK, DEMOCRAT)
        val cells = arrayOfNulls<Piece>(BOARD_SIZE * BOARD_SIZE)
        for (f in 0 until BOARD_SIZE) {
            cells[Position(f, 0).index] = Piece(back[f], PieceColor.WHITE)
            cells[Position(f, 1).index] = Piece(PAWN, PieceColor.WHITE)
            cells[Position(f, BOARD_SIZE - 2).index] = Piece(PAWN, PieceColor.BLACK)
            cells[Position(f, BOARD_SIZE - 1).index] = Piece(back[f], PieceColor.BLACK)
        }
        return GameState(board = cells.toList())
    }

    // ------------------------------------------------------------ public API

    fun legalMoves(state: GameState): List<Move> {
        if (state.status != GameStatus.ACTIVE) return emptyList()
        return legalMovesFor(state.board, state.turn, state.cooldowns)
    }

    fun legalMovesFrom(state: GameState, from: Position): List<Move> =
        legalMoves(state).filter { it.from == from }

    fun applyMove(state: GameState, move: Move): GameState {
        val mover = requireNotNull(state.board[move.from.index]) { "No piece on ${move.from}" }

        // Cooldown machine: tick the mover's timers first, then start a new one if an ability was used.
        var cooldowns = state.cooldowns.tick(state.turn)
        if (move.kind == MoveKind.SWAP || move.kind == MoveKind.TELEPORT) {
            cooldowns = cooldowns.start(mover)
        }

        val newBoard = applyToBoard(state.board, move)
        val next = state.turn.opposite
        val inCheck = isKingAttacked(newBoard, next)
        val hasMoves = legalMovesFor(newBoard, next, cooldowns).isNotEmpty()

        val status = when {
            hasMoves -> GameStatus.ACTIVE
            inCheck -> if (state.turn == PieceColor.WHITE) GameStatus.WHITE_WINS else GameStatus.BLACK_WINS
            else -> GameStatus.STALEMATE
        }
        return GameState(newBoard, next, cooldowns, status, inCheck, move)
    }

    fun findKing(board: Board, color: PieceColor): Position? {
        val i = board.indexOf(Piece(KING, color))
        return if (i >= 0) Position.fromIndex(i) else null
    }

    // ------------------------------------------------------- move generation

    private fun legalMovesFor(board: Board, color: PieceColor, cooldowns: CooldownState): List<Move> {
        val out = ArrayList<Move>()
        for (i in board.indices) {
            val piece = board[i] ?: continue
            if (piece.color != color) continue
            for (m in pseudoMoves(board, Position.fromIndex(i), piece, cooldowns)) {
                if (!isKingAttacked(applyToBoard(board, m), color)) out += m
            }
        }
        return out
    }

    /** Immunity rule: only pawns and kings may capture Agent / Democrat. */
    private fun canCapture(attacker: PieceType, victim: Piece): Boolean =
        !victim.type.isSpecial || attacker == PAWN || attacker == KING

    private fun MutableList<Move>.addStep(board: Board, from: Position, to: Position, piece: Piece) {
        if (!to.isValid) return
        val target = board[to.index]
        if (target == null || (target.color != piece.color && canCapture(piece.type, target))) {
            add(Move(from, to))
        }
    }

    private fun MutableList<Move>.addSlides(
        board: Board, from: Position, piece: Piece, dirs: List<Pair<Int, Int>>
    ) {
        for ((df, dr) in dirs) {
            var p = from.offset(df, dr)
            while (p.isValid) {
                val occ = board[p.index]
                if (occ == null) {
                    add(Move(from, p))
                } else {
                    if (occ.color != piece.color && canCapture(piece.type, occ)) add(Move(from, p))
                    break // every piece (including immune specials) blocks sliders
                }
                p = p.offset(df, dr)
            }
        }
    }

    private fun pseudoMoves(
        board: Board, from: Position, piece: Piece, cooldowns: CooldownState
    ): List<Move> {
        val out = ArrayList<Move>()
        val color = piece.color
        when (piece.type) {
            PAWN -> {
                val dir = color.pawnDirection
                val one = from.offset(0, dir)
                if (one.isValid) {
                    val front = board[one.index]
                    if (front == null) {
                        out += Move(from, one)
                        val two = from.offset(0, 2 * dir)
                        if (from.rank == color.pawnStartRank && two.isValid && board[two.index] == null) {
                            out += Move(from, two)
                        }
                    } else if (front.color != color && front.type.isSpecial) {
                        // Anti-Special Straight Elimination
                        out += Move(from, one)
                    }
                }
                for (df in intArrayOf(-1, 1)) { // Standard diagonal attack
                    val t = from.offset(df, dir)
                    if (!t.isValid) continue
                    val victim = board[t.index]
                    if (victim != null && victim.color != color) out += Move(from, t)
                }
            }
            KNIGHT -> for ((df, dr) in KNIGHT_OFFSETS) out.addStep(board, from, from.offset(df, dr), piece)
            BISHOP -> out.addSlides(board, from, piece, DIAGONAL)
            ROOK -> out.addSlides(board, from, piece, ORTHOGONAL)
            QUEEN -> out.addSlides(board, from, piece, ALL_DIRS)
            KING -> for ((df, dr) in ALL_DIRS) out.addStep(board, from, from.offset(df, dr), piece)
            AGENT -> {
                stepToEmpty(board, from, out)
                if (cooldowns.isReady(piece)) {
                    for (i in board.indices) {
                        val other = board[i] ?: continue
                        if (other.color == color && (other.type == ROOK || other.type == KNIGHT || other.type == BISHOP)) {
                            out += Move(from, Position.fromIndex(i), MoveKind.SWAP)
                        }
                    }
                }
            }
            DEMOCRAT -> {
                stepToEmpty(board, from, out)
                if (cooldowns.isReady(piece)) {
                    for (i in board.indices) {
                        if (board[i] != null) continue
                        val to = Position.fromIndex(i)
                        val adjacent = maxOf(kotlin.math.abs(to.file - from.file), kotlin.math.abs(to.rank - from.rank)) == 1
                        if (!adjacent) out += Move(from, to, MoveKind.TELEPORT) // adjacent = plain step, no cooldown
                    }
                }
            }
        }
        return out
    }

    /** Agent / Democrat have zero attack: one king-like step onto EMPTY squares only. */
    private fun stepToEmpty(board: Board, from: Position, out: MutableList<Move>) {
        for ((df, dr) in ALL_DIRS) {
            val t = from.offset(df, dr)
            if (t.isValid && board[t.index] == null) out += Move(from, t)
        }
    }

    // ---------------------------------------------------- board mechanics

    private fun applyToBoard(board: Board, move: Move): Board {
        val b = board.toMutableList()
        val mover = b[move.from.index]!!
        when (move.kind) {
            MoveKind.SWAP -> {
                val other = b[move.to.index]
                b[move.to.index] = mover
                b[move.from.index] = other
            }
            else -> {
                b[move.from.index] = null
                b[move.to.index] =
                    if (mover.type == PAWN && move.to.rank == mover.color.promotionRank)
                        Piece(QUEEN, mover.color) // auto-promotion
                    else mover
            }
        }
        return b
    }

    fun isKingAttacked(board: Board, color: PieceColor): Boolean {
        val king = findKing(board, color) ?: return false
        return isSquareAttacked(board, king, color.opposite)
    }

    /** Agent and Democrat never attack, so they are simply ignored as attackers. */
    fun isSquareAttacked(board: Board, target: Position, by: PieceColor): Boolean {
        val dir = by.pawnDirection
        for (df in intArrayOf(-1, 1)) {
            val p = target.offset(df, -dir)
            if (p.isValid && board[p.index] == Piece(PAWN, by)) return true
        }
        for ((df, dr) in KNIGHT_OFFSETS) {
            val p = target.offset(df, dr)
            if (p.isValid && board[p.index] == Piece(KNIGHT, by)) return true
        }
        for ((df, dr) in ALL_DIRS) {
            val p = target.offset(df, dr)
            if (p.isValid && board[p.index] == Piece(KING, by)) return true
        }
        for ((df, dr) in ORTHOGONAL) if (rayHits(board, target, df, dr, by, ROOK)) return true
        for ((df, dr) in DIAGONAL) if (rayHits(board, target, df, dr, by, BISHOP)) return true
        return false
    }

    private fun rayHits(
        board: Board, from: Position, df: Int, dr: Int, by: PieceColor, slider: PieceType
    ): Boolean {
        var p = from.offset(df, dr)
        while (p.isValid) {
            val occ = board[p.index]
            if (occ != null) return occ.color == by && (occ.type == slider || occ.type == QUEEN)
            p = p.offset(df, dr)
        }
        return false
    }
}