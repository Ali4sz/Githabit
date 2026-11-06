package com.githabit.prototype

import android.os.Bundle
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.githabit.prototype.ui.GitHabitApp

class MainActivity : ComponentActivity() {
    private val model: GitHabitViewModel by viewModels { GitHabitViewModel.Factory }
    private val timeChanges = setOf(Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)
    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Protected system actions only; never trust date/zone values from extras.
            if (intent.action in timeChanges) model.refreshDate()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { GitHabitApp(model) }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(this, timeReceiver,
            IntentFilter().apply { timeChanges.forEach(::addAction) }, ContextCompat.RECEIVER_NOT_EXPORTED)
        model.startForegroundRefresh()
    }

    override fun onResume() {
        super.onResume()
        model.refreshDate()
    }

    override fun onStop() {
        model.stopForegroundRefresh()
        unregisterReceiver(timeReceiver)
        super.onStop()
    }
}
