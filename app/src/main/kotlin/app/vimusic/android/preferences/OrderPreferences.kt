package app.vimusic.android.preferences

import app.vimusic.android.GlobalPreferencesHolder
import app.vimusic.core.data.enums.AlbumSortBy
import app.vimusic.core.data.enums.ArtistSortBy
import app.vimusic.core.data.enums.PlaylistSortBy
import app.vimusic.core.data.enums.SongSortBy
import app.vimusic.core.data.enums.SortOrder

object OrderPreferences : GlobalPreferencesHolder() {
    var songSortOrder by enum(SortOrder.Descending)
    var localSongSortOrder by enum(SortOrder.Descending)
    var playlistSortOrder by enum(SortOrder.Descending)
    var albumSortOrder by enum(SortOrder.Descending)
    var artistSortOrder by enum(SortOrder.Descending)

    var songSortBy by enum(SongSortBy.DateAdded)
    var localSongSortBy by enum(SongSortBy.DateAdded)
    var playlistSortBy by enum(PlaylistSortBy.DateAdded)
    var albumSortBy by enum(AlbumSortBy.DateAdded)
    var artistSortBy by enum(ArtistSortBy.DateAdded)
}
