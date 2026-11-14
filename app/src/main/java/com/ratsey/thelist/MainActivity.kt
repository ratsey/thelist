package com.ratsey.thelist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ratsey.thelist.ui.ListViewModel
import com.ratsey.thelist.ui.LocalCurrency
import com.ratsey.thelist.ui.TheListRoot
import com.ratsey.thelist.ui.theme.TheListTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as TheListApp).container

        setContent {
            TheListTheme {
                val viewModel: ListViewModel = viewModel(factory = ListViewModel.factory(container))
                // Provided once here so every row, sheet and preview writes money the same way.
                val currency by viewModel.currency.collectAsStateWithLifecycle()
                CompositionLocalProvider(LocalCurrency provides currency) {
                    TheListRoot(viewModel)
                }
            }
        }
    }
}
