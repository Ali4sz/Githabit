package com.githabit.prototype

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.githabit.prototype.ui.GitHabitApp

class MainActivity : ComponentActivity() {
    private val model: GitHabitViewModel by viewModels { GitHabitViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { GitHabitApp(model) }
    }

    override fun onResume() {
        super.onResume()
        model.refreshDate()
    }
}
