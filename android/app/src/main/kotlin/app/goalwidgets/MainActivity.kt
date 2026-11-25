package app.goalwidgets

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.goalwidgets.ui.GoalListScreen
import app.goalwidgets.ui.GoalWidgetsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GoalWidgetsTheme {
                GoalListScreen()
            }
        }
    }
}
