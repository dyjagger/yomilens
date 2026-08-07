package app.yomilens.ui

import java.util.concurrent.atomic.AtomicBoolean

/** Serializes automatic captures and rejects stale timer callbacks after state/lifecycle changes. */
internal class CaptureAdmissionGate {
    private val captureInFlight = AtomicBoolean(false)

    fun tryAcquire(isBusy: Boolean, isCameraStreaming: Boolean): Boolean =
        !isBusy && isCameraStreaming && captureInFlight.compareAndSet(false, true)

    fun complete() {
        captureInFlight.set(false)
    }
}
