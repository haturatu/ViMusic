package app.vimusic.android.utils

import java.util.concurrent.atomic.AtomicLong

class RequestGeneration {
    private val value = AtomicLong()

    fun next(): Long = value.incrementAndGet()

    fun invalidate() {
        value.incrementAndGet()
    }

    fun isCurrent(generation: Long): Boolean = generation == value.get()
}
