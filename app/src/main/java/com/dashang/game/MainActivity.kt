package com.dashang.game

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.dashang.game.model.DashangEngine
import com.dashang.game.ui.DashangBoardUI

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val engine = DashangEngine()

        setContent {
            MaterialTheme {
                Surface {
                    DashangBoardUI(engine = engine, onMoveExecuted = {
                        // Move execution listener
                    })
                }
            }
        }
    }
}
