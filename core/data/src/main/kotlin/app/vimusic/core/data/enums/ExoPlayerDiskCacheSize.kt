package app.vimusic.core.data.enums

import app.vimusic.core.data.utils.mb

@Suppress("EnumEntryName", "unused")
enum class ExoPlayerDiskCacheSize(val bytes: Long) {
    `32MB`(bytes = 32.mb),
    `64MB`(bytes = 64.mb),
    `128MB`(bytes = 128.mb),
    `256MB`(bytes = 256.mb),
    `512MB`(bytes = 512.mb),
    `1GB`(bytes = 1024.mb),
    `2GB`(bytes = 2048.mb),
    `4GB`(bytes = 4096.mb),
    `8GB`(bytes = 8192.mb),
    Unlimited(bytes = 0)
}
