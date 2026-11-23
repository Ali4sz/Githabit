package com.githabit.prototype.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.githabit.prototype.R

/** A bounded read-only preview is never an edit. Replacement requires an explicit user action. */
@Composable
internal fun LegacyTextInput(value: String, onValueChange: (String) -> Unit, label: String,
    modifier: Modifier = Modifier, enabled: Boolean = true, singleLine: Boolean = false,
    maxLines: Int = Int.MAX_VALUE, supportingText: (@Composable () -> Unit)? = null) {
    val large = value.length > 4096
    Column {
        OutlinedTextField(displayPreview(value, 4096), onValueChange, modifier = modifier,
            label = { Text(label) }, enabled = enabled, readOnly = large,
            singleLine = singleLine, maxLines = maxLines, supportingText = supportingText)
        if (large) {
            Text(stringResource(R.string.legacy_text_retained), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { onValueChange("") }, enabled = enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = GitHabitSize.minimumTouch).focusOutline()) {
                Text(stringResource(R.string.legacy_text_replace))
            }
        }
    }
}
