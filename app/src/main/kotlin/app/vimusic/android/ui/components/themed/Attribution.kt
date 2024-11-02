package app.vimusic.android.ui.components.themed

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.vimusic.android.LocalPlayerAwareWindowInsets
import app.vimusic.android.R
import app.vimusic.android.utils.align
import app.vimusic.android.utils.disabled
import app.vimusic.android.utils.secondary
import app.vimusic.android.utils.semiBold
import app.vimusic.core.ui.LocalAppearance

@Composable
fun Attribution(
    text: String,
    modifier: Modifier = Modifier
) = Column {
    val (_, typography) = LocalAppearance.current
    val windowInsets = LocalPlayerAwareWindowInsets.current

    val endPaddingValues = windowInsets
        .only(WindowInsetsSides.End)
        .asPaddingValues()

    val attributionsIndex = text.lastIndexOf("\n\n${stringResource(R.string.from_wikipedia)}")

    var expanded by rememberSaveable { mutableStateOf(false) }
    var overflow by rememberSaveable { mutableStateOf(false) }

    AnimatedContent(
        targetState = expanded,
        label = ""
    ) { isExpanded ->
        Row(
            modifier = modifier
                .padding(endPaddingValues)
                .let {
                    if (overflow) it.clickable {
                        expanded = !expanded
                    } else it
                }
        ) {
            BasicText(
                text = stringResource(R.string.quote_open),
                style = typography.xxl.semiBold,
                modifier = Modifier
                    .offset(y = (-8).dp)
                    .align(Alignment.Top)
            )
            BasicText(
                text = if (attributionsIndex == -1) text else text.substring(0, attributionsIndex),
                style = typography.xxs.secondary,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .weight(1f),
                maxLines = if (isExpanded) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = {
                    if (!expanded) overflow = it.hasVisualOverflow
                }
            )

            BasicText(
                text = stringResource(R.string.quote_close),
                style = typography.xxl.semiBold,
                modifier = Modifier
                    .offset(y = 4.dp)
                    .align(Alignment.Bottom)
            )
        }
    }

    if (attributionsIndex != -1) BasicText(
        text = stringResource(R.string.wikipedia_cc_by_sa),
        style = typography.xxs.disabled.align(TextAlign.End),
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp)
            .padding(endPaddingValues)
    )
}
