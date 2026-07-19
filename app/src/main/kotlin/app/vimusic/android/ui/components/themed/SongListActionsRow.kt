package app.vimusic.android.ui.components.themed

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import app.vimusic.android.R

@Composable
fun RowScope.SongListActionsRow(
    filterQuery: String? = null,
    onFilterQueryChange: ((String?) -> Unit)? = null,
    leadingContent: @Composable (RowScope.() -> Unit)? = null,
    trailingContent: @Composable (RowScope.() -> Unit)? = null
) {
    var isFiltering by rememberSaveable { mutableStateOf(filterQuery != null) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    if (onFilterQueryChange != null) AnimatedContent(
        targetState = isFiltering,
        label = ""
    ) { filtering ->
        if (filtering) {
            val focusRequester = remember { FocusRequester() }

            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
            }

            TextField(
                value = filterQuery.orEmpty(),
                onValueChange = onFilterQueryChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    if (filterQuery.isNullOrBlank()) onFilterQueryChange("")
                    focusManager.clearFocus()
                }),
                hintText = stringResource(R.string.filter_placeholder),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onFocusChanged {
                        if (!it.hasFocus) {
                            keyboardController?.hide()
                            if (filterQuery?.isBlank() == true) {
                                onFilterQueryChange(null)
                                isFiltering = false
                            }
                        }
                    }
            )
        } else HeaderIconButton(
            icon = R.drawable.search,
            onClick = { isFiltering = true }
        )
    }

    Spacer(modifier = Modifier.weight(1f))

    leadingContent?.invoke(this)

    trailingContent?.invoke(this)
}
