package com.githabit.prototype.ui

import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape

/** Add before a Material focus target; never adds a second focus/click/semantics node. */
@Composable
internal fun Modifier.focusOutline(
    shape: Shape = MaterialTheme.shapes.small,
    color: Color = MaterialTheme.colorScheme.primary,
): Modifier {
    var focused by remember { mutableStateOf(false) }
    return onFocusChanged { focused = it.isFocused }
        .then(if (focused) Modifier.border(GitHabitSize.focusBorder, color, shape) else Modifier)
}
