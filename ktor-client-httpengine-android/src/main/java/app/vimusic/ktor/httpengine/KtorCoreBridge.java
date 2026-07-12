package app.vimusic.ktor.httpengine;

import io.ktor.client.engine.UtilsKt;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;

/** JVM bridge for Ktor's internal per-call context factory. */
public final class KtorCoreBridge {
    private KtorCoreBridge() {}

    public static Object callContext(Continuation<? super CoroutineContext> continuation) {
        return UtilsKt.callContext(continuation);
    }
}
