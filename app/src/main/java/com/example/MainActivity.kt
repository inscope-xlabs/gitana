package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.ui.RepoCloneScreen
import com.example.ui.RepoCloneViewModel
import com.example.ui.theme.RepoCloneTheme

class MainActivity : ComponentActivity() {

    private val viewModel: RepoCloneViewModel by viewModels {
        RepoCloneViewModel.factory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RepoCloneTheme {
                RepoCloneScreen(viewModel = viewModel)
            }
        }
    }
}
