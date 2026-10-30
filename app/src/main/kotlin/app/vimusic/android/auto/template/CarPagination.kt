package app.vimusic.android.auto.template

/** Page only after scrolling towards an edge; ignore initial and stale visibility callbacks. */
internal class CarPagination(private val changed: () -> Unit) {
    var page = 0
        private set
    var pageCount = 1
        private set
    var generation = 0
        private set
    private var itemCount = 0
    private var pageSize = 1
    private var firstVisible: Int? = null

    fun update(count: Int, size: Int) {
        if (count != itemCount || size != pageSize) { firstVisible = null; generation++ }
        itemCount = count
        pageSize = size.coerceAtLeast(1)
        pageCount = ((count + pageSize - 1) / pageSize).coerceAtLeast(1)
        val clamped = page.coerceIn(0, pageCount - 1)
        if (clamped != page) { page = clamped; firstVisible = null; generation++ }
    }

    fun previous() = select(page - 1)
    fun next() = select(page + 1)
    private fun select(destination: Int) {
        if (destination !in 0 until pageCount || destination == page) return
        page = destination
        firstVisible = null
        generation++
        changed()
    }

    fun onVisible(expectedGeneration: Int, start: Int, end: Int) {
        if (expectedGeneration != generation) return
        val oldStart = firstVisible
        firstVisible = start
        if (oldStart == null) return
        val last = minOf(pageSize, itemCount - page * pageSize) - 1
        if (start > oldStart && end >= last) next()
        else if (start < oldStart && start == 0) previous()
    }
}
