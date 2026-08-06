package app.yomilens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import app.yomilens.ui.YomiLensRoute
import app.yomilens.ui.YomiLensViewModel
import app.yomilens.ui.theme.YomiLensTheme

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<YomiLensViewModel> { YomiLensViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            YomiLensTheme {
                YomiLensRoute(viewModel)
            }
        }
    }
}
