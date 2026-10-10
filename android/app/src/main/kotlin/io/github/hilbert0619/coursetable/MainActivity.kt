package io.github.hilbert0619.coursetable

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.hilbert0619.coursetable.ui.RootScreen
import io.github.hilbert0619.coursetable.ui.theme.StudyHourTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StudyHourTheme {
                RootScreen()
            }
        }
    }
}
