package app.vimusic.compose.persist

import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf

@JvmInline
value class PersistMap(val map: MutableMap<String, MutableState<*>> = hashMapOf()) {
    fun clean(prefix: String) = map.keys.removeAll { it.startsWith(prefix) }
}

val LocalPersistMap = compositionLocalOf<PersistMap?> {
    Log.e("PersistMap", "Tried to reference uninitialized PersistMap, stacktrace:")
    runCatching { error("Stack:") }.exceptionOrNull()?.printStackTrace()
    null
}
