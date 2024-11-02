package io.ktor.client.plugins.compression

import io.ktor.util.ContentEncoder
import io.ktor.util.Encoder
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.jvm.javaio.toByteReadChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import org.brotli.dec.BrotliInputStream
import kotlin.coroutines.CoroutineContext

internal object BrotliEncoder : ContentEncoder, Encoder by Brotli {
    override val name: String = "br"
}

private object Brotli : Encoder {
    private fun encode(): Nothing =
        error("BrotliOutputStream not available (https://github.com/google/brotli/issues/715)")

    override fun decode(
        source: ByteReadChannel,
        coroutineContext: CoroutineContext
    ) = BrotliInputStream(source.toInputStream()).toByteReadChannel()

    override fun encode(
        source: ByteReadChannel,
        coroutineContext: CoroutineContext
    ) = encode()

    override fun encode(
        source: ByteWriteChannel,
        coroutineContext: CoroutineContext
    ) = encode()
}
