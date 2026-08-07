package app.yomilens.ui

import android.graphics.Bitmap
import java.util.concurrent.atomic.AtomicInteger

/** Reference-counted ownership shared by the ViewModel and the active composition. */
class RetainedCameraFrame(val bitmap: Bitmap) {
    private val references = AtomicInteger(1)

    fun retain(): Boolean {
        while (true) {
            val current = references.get()
            if (current <= 0) return false
            if (references.compareAndSet(current, current + 1)) return true
        }
    }

    fun release() {
        val remaining = references.decrementAndGet()
        check(remaining >= 0) { "Camera frame released too many times" }
        if (remaining == 0 && !bitmap.isRecycled) bitmap.recycle()
    }
}
