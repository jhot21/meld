package me.jhot.meld

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import me.jhot.meld.ui.navigation.MeldNavGraph
import me.jhot.meld.ui.theme.MeldTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MeldTheme {
                MeldNavGraph()
            }
        }
    }
}
