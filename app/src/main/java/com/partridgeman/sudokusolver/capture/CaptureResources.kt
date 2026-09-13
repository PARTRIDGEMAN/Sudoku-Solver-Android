package com.partridgeman.sudokusolver.capture

/** Single-threaded ownership: release in reverse order, including after partial setup failures. */
internal class CaptureResources : AutoCloseable {
    private val releases = ArrayDeque<() -> Unit>()
    private var closed = false

    fun <T> own(resource: T, release: (T) -> Unit): T {
        check(!closed) { "Capture resources already closed" }
        releases.addFirst { release(resource) }
        return resource
    }

    override fun close() {
        if (closed) return
        closed = true
        var failure: Exception? = null
        while (releases.isNotEmpty()) {
            try {
                releases.removeFirst().invoke()
            } catch (error: Exception) {
                if (failure == null) failure = error else failure.addSuppressed(error)
            }
        }
        failure?.let { throw it }
    }
}
