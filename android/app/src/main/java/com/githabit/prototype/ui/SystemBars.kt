package com.githabit.prototype.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView

/** Use the same resolved choice for icons AND the API 26–28 navigation-bar scrim. */
@Composable
internal fun GitHabitSystemBars(dark: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, dark) {
        if (!view.isInEditMode) view.context.activity()?.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.Transparent.toArgb(), Color.Transparent.toArgb()) { dark },
            navigationBarStyle = SystemBarStyle.auto(LightColors.surface.toArgb(), DarkColors.surface.toArgb()) { dark },
        )
        // The Activity owns its window. Do not restore an obsolete theme on recomposition.
        onDispose { }
    }
}

private tailrec fun Context.activity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.takeUnless { it === this }?.activity()
    else -> null
}
