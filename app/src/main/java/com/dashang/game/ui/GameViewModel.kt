package com.dashang.game.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dashang.game.ai.DashangBot
import com.dashang.game.ai.MinimaxBot
import com.dashang.game.model.DashangEngine
import com.dashang.game.model.GameState
import com.dashang.game.model.GameStatus
import com.dashang.game.model.Move
import com.dashang.game.model.MoveKind
import com.dashang.game.model.Piece
import com.dashang.game.model.PieceColor
import com.dashang.game.model.PieceType
import com.dashang.game.model.Position
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Human always plays White; the AI (if any) plays Black. */
enum class Opponent(val label: String) {
    HUMAN("2P"),
    AI_EASY("Easy"),
    AI_MEDIUM("Medium"),
    AI_HARD("Hard");

    fun createBot(): DashangBot? = when (this) {
        HUMAN -> null
        AI_EASY -> MinimaxBot(maxDepth = 1, randomness = 80, name = "Easy")
        AI_MEDIUM -> MinimaxBot(maxDepth = 3, timeLimitMs = 2000, name = "Medium")
        AI_HARD -> MinimaxBot(maxDepth = 4, timeLimitMs = 4000, name = "Hard")
    }
}

data class GameUiState(
    val game: GameState = DashangEngine.initialState(),
    val selected: Position? = null,
    val movesFromSelected: List<Move> = emptyList(),
    val swapMode: Boolean = false,
    val hint: String? = null,
    val opponent: Opponent = Opponent.AI_MEDIUM,
    val canUndo: Boolean = false,
    val isBotThinking: Boolean = false
) {
    /** Normal mode hides swap targets; swap mode shows only swap targets. */
    val visibleMoves: List<Move>
        get() = if (swapMode) movesFromSelected.filter { it.kind == MoveKind.SWAP }
        else movesFromSelected.filter { it.kind != MoveKind.SWAP }
}

sealed interface GameIntent {
    data class SquareTapped(val pos: Position) : GameIntent
    data class SetOpponent(val opponent: Opponent) : GameIntent
    data object NewGame : GameIntent
    data object Undo : GameIntent
}

class GameViewModel : ViewModel() {

    private val humanColor = PieceColor.WHITE
    private val botColor = PieceColor.BLACK

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private var bot: DashangBot? = _uiState.value.opponent.createBot()
    private var history: List<GameState> = emptyList() // state BEFORE each played move
    private var botJob: Job? = null
    private var lastTapPos: Position? = null
    private var lastTapTime = 0L

    fun onIntent(intent: GameIntent) {
        when (intent) {
            is GameIntent.SquareTapped -> onTap(intent.pos)
            is GameIntent.SetOpponent -> newGame(intent.opponent)
            GameIntent.NewGame -> newGame(_uiState.value.opponent)
            GameIntent.Undo -> undo()
        }
    }

    // ------------------------------------------------------------ game flow

    private fun newGame(opponent: Opponent) {
        botJob?.cancel()
        bot = opponent.createBot()
        history = emptyList()
        _uiState.value = GameUiState(opponent = opponent)
    }

    private fun isBotTurn(game: GameState) = bot != null && game.turn == botColor

    private fun play(move: Move) {
        history = history + _uiState.value.game
        _uiState.update {
            GameUiState(
                game = DashangEngine.applyMove(it.game, move),
                opponent = it.opponent,
                canUndo = true
            )
        }
        scheduleBotIfNeeded()
    }

    private fun scheduleBotIfNeeded() {
        val game = _uiState.value.game
        val currentBot = bot ?: return
        if (game.turn != botColor || game.status != GameStatus.ACTIVE) return
        botJob?.cancel()
        botJob = viewModelScope.launch {
            _uiState.update { it.copy(isBotThinking = true) }
            delay(300)
            val move = currentBot.chooseMove(game)
            if (move != null) play(move) else _uiState.update { it.copy(isBotThinking = false) }
        }
    }

    /** Reverts one move of each player (two plies). Vs AI: back to the human's previous turn. */
    private fun undo() {
        if (history.isEmpty()) return
        botJob?.cancel()
        val keep = if (bot != null) {
            history.indexOfLast { it.turn == humanColor }
        } else {
            maxOf(0, history.size - 2)
        }
        if (keep < 0) return
        val restored = history[keep]
        history = history.take(keep)
        _uiState.update {
            GameUiState(game = restored, opponent = it.opponent, canUndo = history.isNotEmpty())
        }
        scheduleBotIfNeeded()
    }

    // ----------------------------------------------------------- tap logic

    private fun onTap(pos: Position) {
        val ui = _uiState.value
        val game = ui.game

        val now = System.currentTimeMillis()
        val isDoubleTap = pos == lastTapPos && now - lastTapTime < DOUBLE_TAP_MS
        lastTapPos = pos
        lastTapTime = now

        if (game.status != GameStatus.ACTIVE || isBotTurn(game)) return
        val piece = game.pieceAt(pos)

        // Double-tap on own Agent -> swap mode
        if (isDoubleTap && piece == Piece(PieceType.AGENT, game.turn)) {
            lastTapTime = 0L
            enterSwapMode(game, pos)
            return
        }

        val move = ui.visibleMoves.firstOrNull { it.to == pos }
        if (move != null) {
            play(move)
            return
        }

        if (piece != null && piece.color == game.turn && pos != ui.selected) {
            select(game, pos, piece)
        } else {
            _uiState.update {
                it.copy(selected = null, movesFromSelected = emptyList(), swapMode = false, hint = null)
            }
        }
    }

    private fun select(game: GameState, pos: Position, piece: Piece) {
        val moves = DashangEngine.legalMovesFrom(game, pos)
        val swapHint = if (piece.type == PieceType.AGENT && moves.any { it.kind == MoveKind.SWAP })
            "Double-tap the Agent to swap with a Rook, Knight or Bishop" else null
        _uiState.update {
            it.copy(selected = pos, movesFromSelected = moves, swapMode = false, hint = swapHint)
        }
    }

    private fun enterSwapMode(game: GameState, pos: Position) {
        val agent = Piece(PieceType.AGENT, game.turn)
        val remaining = game.cooldowns.remaining(agent)
        val moves = DashangEngine.legalMovesFrom(game, pos)
        when {
            remaining > 0 -> _uiState.update {
                it.copy(
                    selected = pos, movesFromSelected = moves, swapMode = false,
                    hint = "Agent swap cooldown: $remaining turn(s) left"
                )
            }
            moves.none { it.kind == MoveKind.SWAP } -> _uiState.update {
                it.copy(
                    selected = pos, movesFromSelected = moves, swapMode = false,
                    hint = "No legal swap available right now"
                )
            }
            else -> _uiState.update {
                it.copy(
                    selected = pos, movesFromSelected = moves, swapMode = true,
                    hint = "Swap: tap your Rook, Knight or Bishop. Tap elsewhere to cancel."
                )
            }
        }
    }

    private companion object {
        const val DOUBLE_TAP_MS = 450L
    }
}