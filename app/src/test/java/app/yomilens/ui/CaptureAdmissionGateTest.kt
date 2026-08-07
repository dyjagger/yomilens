package app.yomilens.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureAdmissionGateTest {
    @Test
    fun staleTimerCannotEnterDuringBusyTranslationOrAnotherCapture() {
        val gate = CaptureAdmissionGate()

        assertTrue(gate.tryAcquire(isBusy = false, isCameraStreaming = true))
        assertFalse(gate.tryAcquire(isBusy = false, isCameraStreaming = true))
        gate.complete()
        assertFalse(gate.tryAcquire(isBusy = true, isCameraStreaming = true))
        assertTrue(gate.tryAcquire(isBusy = false, isCameraStreaming = true))
    }

    @Test
    fun lifecycleStopRejectsTheNextAutomaticCaptureCycle() {
        val gate = CaptureAdmissionGate()

        assertFalse(gate.tryAcquire(isBusy = false, isCameraStreaming = false))
        assertTrue(gate.tryAcquire(isBusy = false, isCameraStreaming = true))
        gate.complete()
        assertFalse(gate.tryAcquire(isBusy = false, isCameraStreaming = false))
    }
}
