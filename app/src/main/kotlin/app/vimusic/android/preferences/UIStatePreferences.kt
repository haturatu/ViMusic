package app.vimusic.android.preferences

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SnapshotMutationPolicy
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import app.vimusic.android.GlobalPreferencesHolder
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

object UIStatePreferences : GlobalPreferencesHolder() {
    var homeScreenTabIndex by int(0)
    var searchResultScreenTabIndex by int(0)

    var artistScreenTabIndexProperty = int(0)
    var artistScreenTabIndex by artistScreenTabIndexProperty

    private var visibleTabs by json(mapOf<String, List<String>>())

    @Composable
    fun mutableTabStateOf(
        key: String,
        default: ImmutableList<String> = persistentListOf()
    ): MutableState<ImmutableList<String>> = remember(key, default, visibleTabs) {
        mutableStateOf(
            value = visibleTabs.getOrDefault(key, default).toImmutableList(),
            policy = object : SnapshotMutationPolicy<ImmutableList<String>> {
                override fun equivalent(
                    a: ImmutableList<String>,
                    b: ImmutableList<String>
                ): Boolean {
                    val eq = a == b
                    if (!eq) visibleTabs += key to b
                    return eq
                }
            }
        )
    }
}
