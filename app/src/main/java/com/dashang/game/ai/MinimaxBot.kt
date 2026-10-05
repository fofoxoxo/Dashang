package com.dashang.game.ai

import com.dashang.game.model.BOARD_SIZE
import com.dashang.game.model.DashangEngine
import com.dashang.game.model.GameState
import com.dashang.game.model.GameStatus
import com.dashang.game.model.Move
import com.dashang.game.model.MoveKind
import com.dashang.game.model.Piece
import com.dashang.game.model.PieceColor
import com.dashang.game.model.PieceType
import com.dashang.game.model.Position
import kotlin.math.abs
import kotlin.random.Random

/** Material + simple positional terms (centre control, pawn advance, king stays home). */
object HeuristicEvaluator : PositionEvaluator {
    override fun evaluate(state: GameState, perspective: PieceColor): Int {
        if (state.status != GameStatus.ACTIVE) return MaterialEvaluator.evaluate(state, perspective)
        var score = 0
        for (i in state.board.indices) {
            val p = state.board[i] ?: continue
            val v = p.type.value + positional(p, Position.fromIndex(i))
            score += if (p.color == perspective) v else -v
        }
        if (state.inCheck) score += if (state.turn == perspective) -25 else 25
        return score
    }

    private fun positional(p: Piece, pos: Position): Int {
        val centre = 18 - (abs(2 * pos.file - 9) + abs(2 * pos.rank - 9)) // 0..16
        return when (p.type) {
            PieceType.PAWN -> {
                val advance = if (p.color == PieceColor.WHITE) pos.rank - 1 else BOARD_SIZE - 2 - pos.rank
                advance * 8 + centre / 4
            }
            PieceType.KNIGHT, PieceType.BISHOP -> centre * 3
            PieceType.QUEEN -> centre
            PieceType.KING -> {
                val home = if (p.color == PieceColor.WHITE) 0 else BOARD_SIZE - 1
                -abs(pos.rank - home) * 10
            }
            else -> 0
        }
    }
}

/**
 * Negamax + alpha-beta with iterative deepening and a time limit.
 * If time runs out mid-depth, the best move from the last fully searched depth is used.
 */
class MinimaxBot(
    evaluator: PositionEvaluator = HeuristicEvaluator,
    maxDepth: Int = 3,
    private val timeLimitMs: Long = 3000L,
    private val randomness: Int = 0,
    override val name: String = "Minimax"
) : SearchBot(evaluator, maxDepth) {

    private val eval: PositionEvaluator = evaluator

    override fun search(state: GameState, depth: Int): Move? {
        val deadline = System.nanoTime() + timeLimitMs * 1_000_000L
        var ordered = orderedMoves(state)
        var best: Move? = null

        for (d in 1..depth) {
            val searcher = Searcher(deadline)
            var alpha = -INF
            var bestThisDepth: Move? = null
            for (m in ordered) {
                val noise = if (randomness > 0) Random.nextInt(randomness + 1) else 0
                val score = -searcher.negamax(DashangEngine.applyMove(state, m), d - 1, -INF, -alpha, 1) + noise
                if (searcher.timedOut) break
                if (bestThisDepth == null || score > alpha) {
                    alpha = score
                    bestThisDepth = m
                }
            }
            if (searcher.timedOut) break
            best = bestThisDepth
            val b = bestThisDepth ?: break
            ordered = listOf(b) + ordered.filter { it != b }
        }
        return best ?: ordered.first()
    }

    private inner class Searcher(private val deadline: Long) {
        var timedOut = false
        private var nodes = 0

        fun negamax(state: GameState, depth: Int, alphaIn: Int, beta: Int, ply: Int): Int {
            if ((++nodes and 127) == 0 && System.nanoTime() > deadline) timedOut = true
            if (timedOut) return 0
            when (state.status) {
                GameStatus.ACTIVE -> Unit
                GameStatus.STALEMATE -> return 0
                else -> return -(MATE_SCORE - ply) // side to move has been checkmated
            }
            if (depth == 0) return eval.evaluate(state, state.turn)

            var alpha = alphaIn
            var best = -INF
            for (m in orderedMoves(state)) {
                val score = -negamax(DashangEngine.applyMove(state, m), depth - 1, -beta, -alpha, ply + 1)
                if (timedOut) return 0
                if (score > best) best = score
                if (best > alpha) alpha = best
                if (alpha >= beta) break
            }
            return best
        }
    }

    /** Captures first, ability moves last; Democrat teleports are pruned to a few central squares. */
    override fun orderedMoves(state: GameState): List<Move> {
        val all = DashangEngine.legalMoves(state)
        val (teleports, others) = all.partition { it.kind == MoveKind.TELEPORT }
        val kept = if (teleports.size > MAX_TELEPORTS)
            teleports.sortedBy { centreDistance(it.to) }.take(MAX_TELEPORTS)
        else teleports
        return (others + kept).sortedByDescending { moveScore(state, it) }
    }

    private fun moveScore(state: GameState, m: Move): Int {
        if (m.kind != MoveKind.STEP) return -10
        val mover = state.pieceAt(m.from) ?: return 0
        var s = 0
        val victim = state.pieceAt(m.to)
        if (victim != null) s += 1000 + 10 * victim.type.value - mover.type.value
        if (mover.type == PieceType.PAWN && m.to.rank == mover.color.promotionRank) s += 800
        return s
    }

    private fun centreDistance(p: Position) = abs(2 * p.file - 9) + abs(2 * p.rank - 9)

    private companion object {
        const val INF = 1_000_000_000
        const val MATE_SCORE = 100_000
        const val MAX_TELEPORTS = 6
    }
}