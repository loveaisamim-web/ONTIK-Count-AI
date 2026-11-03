package com.ontik.countai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ontik.countai.ui.navigation.OntikNavGraph
import com.ontik.countai.ui.theme.OntikCountAITheme
import com.ontik.countai.ui.theme.WhiteBackground
import com.ontik.countai.ui.viewmodel.CountViewModel
import com.ontik.countai.ui.viewmodel.CountViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as OntikCountApp
        val factory = CountViewModelFactory(
            application = app,
            countRepository = app.countRepository,
            settingsRepository = app.settingsRepository
        )

        setContent {
            OntikCountAITheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = WhiteBackground
                ) {
                    val countViewModel: CountViewModel = viewModel(factory = factory)
                    OntikNavGraph(viewModel = countViewModel)
                }
            }
        }
    }
}
