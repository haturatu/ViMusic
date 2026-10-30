package app.vimusic.android.auto.template

/** Manual pages sized to the connected car host's content limit. */
internal class CarPagination(private val changed: () -> Unit) {
    var page = 0
        private set
    var pageCount = 1
        private set

    fun update(count: Int, size: Int) {
        val pageSize = size.coerceAtLeast(1)
        pageCount = ((count + pageSize - 1) / pageSize).coerceAtLeast(1)
        page = page.coerceIn(0, pageCount - 1)
    }

    fun previous() = select(page - 1)
    fun next() = select(page + 1)
    private fun select(destination: Int) {
        if (destination !in 0 until pageCount || destination == page) return
        page = destination
        changed()
    }
}
