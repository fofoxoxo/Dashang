package com.dashang.game.ai

import com.dashang.game.model.DashangEngine
import com.dashang.game.model.GameState
import com.dashang.game.model.GameStatus
import com.dashang.game.model.Move
import com.dashang.game.model.PieceColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Anything that can play a side. The ViewModel only depends on this. */
interface DashangBot {
    val name: String
    /** Return null only when there are no legal moves. */
    suspend fun chooseMove(state: GameState): Move?
}

/** Heuristic hook: higher = better for [perspective]. */
fun interface PositionEvaluator {
    fun evaluate(state: GameState, perspective: PieceColor): Int
}

/** Baseline heuristic: material balance only. Replace or extend with positional terms. */
object MaterialEvaluator : PositionEvaluator {
    private const val WIN_SCORE = 1_000_000

    override fun evaluate(state: GameState, perspective: PieceColor): Int {
        when (state.status) {
            GameStatus.WHITE_WINS -> return if (perspective == PieceColor.WHITE) WIN_SCORE else -WIN_SCORE
            GameStatus.BLACK_WINS -> return if (perspective == PieceColor.BLACK) WIN_SCORE else -WIN_SCORE
            GameStatus.STALEMATE -> return 0
            GameStatus.ACTIVE -> Unit
        }
        var score = 0
        for (p in state.board) {
            if (p == null) continue
            score += if (p.color == perspective) p.type.value else -p.type.value
        }
        return score
    }
}

/**
 * Template for Minimax / alpha-beta bots. Implement [search] using
 * DashangEngine.legalMoves / applyMove and [evaluator]. It runs off the main thread.
 */
abstract class SearchBot(
    protected val evaluator: PositionEvaluator = MaterialEvaluator,
    protected val maxDepth: Int = 3
) : DashangBot {

    final override suspend fun chooseMove(state: GameState): Move? = withContext(Dispatchers.Default) {
        if (DashangEngine.legalMoves(state).isEmpty()) null else search(state, maxDepth)
    }

    protected abstract fun search(state: GameState, depth: Int): Move?

    /** Override to add move ordering (captures first, etc.) for better pruning. */
    protected open fun orderedMoves(state: GameState): List<Move> = DashangEngine.legalMoves(state)
}

/** Placeholder opponent to verify the wiring. */
class RandomBot : DashangBot {
    override val name = "Random"
    override suspend fun chooseMove(state: GameState): Move? =
        DashangEngine.legalMoves(state).randomOrNull()
}