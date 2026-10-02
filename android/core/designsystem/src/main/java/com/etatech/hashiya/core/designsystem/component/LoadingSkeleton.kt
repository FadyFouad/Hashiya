package com.etatech.hashiya.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

const val LOADING_SKELETON_TAG = "loading_skeleton"

/** Static placeholder rows shaped like result cards. Not animated, so screenshots stay stable. */
@Composable
fun LoadingSkeleton(modifier: Modifier = Modifier, rows: Int = 4) {
    Column(modifier.testTag(LOADING_SKELETON_TAG).padding(horizontal = 12.dp)) {
        repeat(rows) {
            Column(
                Modifier
                    .padding(vertical = 6.dp)
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                    .padding(12.dp)
            ) {
                SkeletonLine(0.85f)
                SkeletonLine(0.6f)
                SkeletonLine(0.35f)
            }
        }
    }
}

@Composable
private fun SkeletonLine(widthFraction: Float) {
    Box(
        Modifier
            .padding(vertical = 4.dp)
            .fillMaxWidth(widthFraction)
            .height(10.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(5.dp))
    )
}
