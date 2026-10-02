package com.etatech.hashiya.feature.library.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.etatech.hashiya.core.designsystem.icon.HashiyaIcons
import com.etatech.hashiya.feature.library.R

internal const val LIBRARY_SEARCH_FIELD_TAG = "library_search_field"

/** The Library's search box. The keyboard's Search key searches at once and hides the keyboard. */
@Composable
internal fun LibrarySearchField(
    text: String,
    onTextChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    TextField(
        value = text,
        onValueChange = onTextChange,
        placeholder = { Text(stringResource(R.string.library_search_hint)) },
        leadingIcon = { Icon(HashiyaIcons.Search, contentDescription = null) },
        trailingIcon = {
            if (text.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(HashiyaIcons.Close, contentDescription = stringResource(R.string.library_search_clear))
                }
            }
        },
        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = {
                onSearch()
                focusManager.clearFocus()
            }
        ),
        shape = RoundedCornerShape(12.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .testTag(LIBRARY_SEARCH_FIELD_TAG)
    )
}
