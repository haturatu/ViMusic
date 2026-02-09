package app.vimusic.android.utils

import android.os.Parcel
import android.os.Parcelable
import android.os.Process
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.rememberSaveable
import app.vimusic.core.ui.utils.stateListSaver
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.datetime.Instant
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import java.io.IOException
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val logcatDateTimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

@Immutable
sealed interface Logcat : Parcelable {
    val id: Int

    companion object {
        // @formatter:off
        @Suppress("MaximumLineLength")
        private val regex = "^(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}.\\d{3})\\s+(\\w)/(.+?)\\(\\s*(\\d+)\\): (.*)$".toRegex()
        // @formatter:on

        private fun String.toLine(id: Int) = runCatching {
            val results = regex.find(this)?.groups ?: return@runCatching null
            val (timestamp, level, tag, pid, message) = results.drop(1).take(5)
                .mapNotNull { it?.value }

            val parsedInstant = LocalDateTime.parse(timestamp, logcatDateTimeFormat)
                .toInstant(ZoneOffset.UTC)

            FormattedLine(
                timestamp = Instant.fromEpochMilliseconds(parsedInstant.toEpochMilli()),
                level = FormattedLine.Level.codeLut[level.firstOrNull()]
                    ?: FormattedLine.Level.Unknown,
                tag = tag,
                pid = pid.toLongOrNull() ?: 0,
                message = message,
                id = id
            )
        }.getOrNull() ?: RawLine(
            raw = this,
            id = id
        )

        fun logAsFlow() = flow {
            val proc =
                Runtime.getRuntime()
                    .exec("/system/bin/logcat -v time,year --pid=${Process.myPid()}")
            val reader = proc.inputStream.bufferedReader()
            val ctx = currentCoroutineContext()

            var id = 0

            @Suppress("LoopWithTooManyJumpStatements", "SwallowedException")
            while (ctx.isActive) {
                try {
                    emit((reader.readLine() ?: break).toLine(id++))
                } catch (e: IOException) {
                    break
                }
            }

            proc.destroy()
        }.flowOn(Dispatchers.IO)
    }

    @Immutable
    @Parcelize
    data class FormattedLine(
        val timestamp: @WriteWith<InstantParceler> Instant,
        val level: Level,
        val tag: String,
        val pid: Long,
        val message: String,
        override val id: Int
    ) : Logcat {
        @Parcelize
        enum class Level(val code: Char?) : Parcelable {
            Error('E'),
            Warning('W'),
            Debug('D'),
            Info('I'),
            Unknown(null);

            companion object {
                internal val codeLut = entries.associateBy { it.code }
            }
        }
    }

    @Immutable
    @Parcelize
    data class RawLine(
        val raw: String,
        override val id: Int
    ) : Logcat
}

@Composable
fun logcat(): ImmutableList<Logcat> {
    val lines = rememberSaveable(saver = stateListSaver()) { mutableStateListOf<Logcat>() }

    LaunchedEffect(Unit) {
        Logcat.logAsFlow().onFirst {
            lines.clear()
        }.collect {
            lines += it
            if (lines.size > 8192) lines.removeAt(0)
        }
    }

    return lines.toList().toImmutableList()
}

object InstantParceler : Parceler<Instant> {
    override fun Instant.write(parcel: Parcel, flags: Int) =
        parcel.writeLong(toEpochMilliseconds())

    override fun create(parcel: Parcel) = Instant.fromEpochMilliseconds(parcel.readLong())
}
