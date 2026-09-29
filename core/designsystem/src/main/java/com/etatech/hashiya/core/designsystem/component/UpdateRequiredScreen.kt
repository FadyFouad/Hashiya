package com.etatech.hashiya.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.etatech.hashiya.core.designsystem.R
import com.etatech.hashiya.core.designsystem.icon.HashiyaIcons

/** Shown instead of the whole app when this build is no longer supported. [onUpdate] opens the store page. */
@Composable
fun UpdateRequiredScreen(onUpdate: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.safeDrawingPadding(), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = HashiyaIcons.Update,
                title = stringResource(R.string.designsystem_update_required_title),
                message = stringResource(R.string.designsystem_update_required_message),
                actionLabel = stringResource(R.string.designsystem_update_required_action),
                onAction = onUpdate
            )
        }
    }
}
