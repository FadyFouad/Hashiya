package com.etatech.hashiya.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class BadgeKind { OpenAccess, InLibrary }

@Composable
fun StatusBadge(text: String, kind: BadgeKind, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (kind) {
        BadgeKind.OpenAccess -> colors.secondaryContainer to colors.onSecondaryContainer
        BadgeKind.InLibrary -> colors.surfaceContainerHigh to colors.onSurfaceVariant
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = content,
        modifier = modifier
            .background(container, RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    )
}
