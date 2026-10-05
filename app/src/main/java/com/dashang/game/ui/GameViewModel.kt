package com.dashang.game.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dashang.game.ai.DashangBot
import com.dashang.game.model.DashangEngine
import com.dashang.game.model.GameState
import com.dashang.game.model.GameStatus
import com.dashang.game.model.Move
import com.dashang.game.model.PieceColor
import com.dashang.game.model.Position
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GameUiState(
    val game: GameState = DashangEngine.initialState(),
    val selected: Position? = null,
    val movesFromSelected: List<Move> = emptyList(),
    val isBotThinking: Boolean = false
)

sealed interface GameIntent {
    data class SquareTapped(val pos: Position) : GameIntent
    data object NewGame : GameIntent
}

class GameViewModel : ViewModel() {

    /**
     * Plug AI opponents in here, e.g. mapOf(PieceColor.BLACK to RandomBot()).
     * Empty map = two humans on one device.
     */
    private val bots: Map<PieceColor, DashangBot> = emptyMap()

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()
    private var botJob: Job? = null

    fun onIntent(intent: GameIntent) {
        when (intent) {
            is GameIntent.SquareTapped -> onTap(intent.pos)
            GameIntent.NewGame -> {
                botJob?.cancel()
                _uiState.value = GameUiState()
                scheduleBotIfNeeded()
            }
        }
    }

    private fun onTap(pos: Position) {
        val ui = _uiState.value
        val game = ui.game
        if (game.status != GameStatus.ACTIVE || bots.containsKey(game.turn)) return

        val move = ui.movesFromSelected.firstOrNull { it.to == pos }
        if (move != null) {
            play(move)
            return
        }
        val piece = game.pieceAt(pos)
        if (piece != null && piece.color == game.turn && pos != ui.selected) {
            _uiState.update {
                it.copy(selected = pos, movesFromSelected = DashangEngine.legalMovesFrom(game, pos))
            }
        } else {
            _uiState.update { it.copy(selected = null, movesFromSelected = emptyList()) }
        }
    }

    private fun play(move: Move) {
        _uiState.update { GameUiState(game = DashangEngine.applyMove(it.game, move)) }
        scheduleBotIfNeeded()
    }

    private fun scheduleBotIfNeeded() {
        val game = _uiState.value.game
        val bot = bots[game.turn] ?: return
        if (game.status != GameStatus.ACTIVE) return
        botJob?.cancel()
        botJob = viewModelScope.launch {
            _uiState.update { it.copy(isBotThinking = true) }
            delay(400)
            val move = bot.chooseMove(game)
            if (move != null) play(move) else _uiState.update { it.copy(isBotThinking = false) }
        }
    }
}