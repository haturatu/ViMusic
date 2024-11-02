@file:OptIn(UnstableApi::class)

package app.vimusic.android.service

import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi

class PlayableFormatNotFoundException(cause: Throwable? = null) : PlaybackException(
    /* message = */ "Playable format not found",
    /* cause = */ cause,
    /* errorCode = */ ERROR_CODE_IO_FILE_NOT_FOUND
)

class UnplayableException(cause: Throwable? = null) : PlaybackException(
    /* message = */ "Unplayable",
    /* cause = */ cause,
    /* errorCode = */ ERROR_CODE_IO_UNSPECIFIED
)

class LoginRequiredException(cause: Throwable? = null) : PlaybackException(
    /* message = */ "Login required",
    /* cause = */ cause,
    /* errorCode = */ ERROR_CODE_AUTHENTICATION_EXPIRED
)

class VideoIdMismatchException(cause: Throwable? = null) : PlaybackException(
    /* message = */ "Requested video ID doesn't match returned video ID",
    /* cause = */ cause,
    /* errorCode = */ ERROR_CODE_IO_UNSPECIFIED
)

class RestrictedVideoException(cause: Throwable? = null) : PlaybackException(
    /* message = */ "This video is restricted",
    /* cause = */ cause,
    /* errorCode = */ ERROR_CODE_PARENTAL_CONTROL_RESTRICTED
)
