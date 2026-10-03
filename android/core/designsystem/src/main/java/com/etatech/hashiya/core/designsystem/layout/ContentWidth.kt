package com.etatech.hashiya.core.designsystem.layout

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The widest a single column of content gets (Details, Settings): wider lines get hard to read. */
val ContentMaxWidth = 840.dp

/** The widest a text field or a full-width button gets, so neither stretches across a large window. */
val ControlMaxWidth = 480.dp

/** Fills the width up to [maxWidth] and centers the content beyond it. No change on phones. */
fun Modifier.centeredMaxWidth(maxWidth: Dp = ContentMaxWidth): Modifier =
    fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = maxWidth).fillMaxWidth()

/** Material's side margin: 16dp on compact windows, 24dp from medium width. */
@Composable
fun horizontalMargin(): Dp = if (currentLayoutClass().isAtLeastMedium) 24.dp else 16.dp
