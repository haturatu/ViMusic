package app.vimusic.providers.innertube

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function

data class JavaScriptChallenge(
    val timestamp: String,
    val source: String,
    val functionName: String
) {
    private val cache = mutableMapOf<String, String>()
    private val mutex = Mutex()

    suspend fun decode(cipher: String) = mutex.withLock {
        cache.getOrPut(cipher) {
            with(Context.enter()) {
                optimizationLevel = -1
                val scope = initSafeStandardObjects()
                evaluateString(scope, source, functionName, 1, null)
                val function = scope.get(functionName, scope) as Function
                function.call(this, scope, scope, arrayOf(cipher)).toString()
            }
        }
    }
}
