package app.trolley.lists

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import app.trolley.lists.ui.ListViewModel
import app.trolley.lists.ui.TrolleyRoot
import app.trolley.lists.ui.theme.TrolleyTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as TrolleyApp).container

        setContent {
            TrolleyTheme {
                val viewModel: ListViewModel = viewModel(factory = ListViewModel.factory(container))
                TrolleyRoot(viewModel)
            }
        }
    }
}
