package com.ratsey.thelist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ratsey.thelist.ui.ListViewModel
import com.ratsey.thelist.ui.TrolleyRoot
import com.ratsey.thelist.ui.theme.TrolleyTheme

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
