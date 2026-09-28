package com.etatech.hashiya.feature.search.components

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
import com.etatech.hashiya.feature.search.R

internal const val SEARCH_FIELD_TAG = "search_field"

@Composable
internal fun SearchField(text: String, onTextChange: (String) -> Unit, onSearchAction: () -> Unit, modifier: Modifier = Modifier) {
    val focusManager = LocalFocusManager.current
    TextField(
        value = text,
        onValueChange = onTextChange,
        placeholder = { Text(stringResource(R.string.search_placeholder)) },
        leadingIcon = { Icon(HashiyaIcons.Search, contentDescription = null) },
        trailingIcon = {
            if (text.isNotEmpty()) {
                IconButton(onClick = { onTextChange("") }) {
                    Icon(HashiyaIcons.Close, contentDescription = stringResource(R.string.search_clear))
                }
            }
        },
        textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Content),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(
            onSearch = {
                onSearchAction()
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
            .testTag(SEARCH_FIELD_TAG)
    )
}
