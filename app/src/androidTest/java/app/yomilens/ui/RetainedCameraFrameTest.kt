package app.yomilens.ui

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.yomilens.ml.JapaneseOcrResult
import com.google.android.gms.tasks.TaskCompletionSource
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RetainedCameraFrameTest {
    @Test
    fun waitsForCompositionReferenceBeforeRecycling() {
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        val frame = RetainedCameraFrame(bitmap)

        assertTrue(frame.retain())
        frame.release()
        assertFalse(bitmap.isRecycled)

        frame.release()
        assertTrue(bitmap.isRecycled)
        assertFalse(frame.retain())
    }

    @Test
    fun cancelledOcrConsumerRecyclesAResultThatArrivesLater() = runBlocking {
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        val source = TaskCompletionSource<JapaneseOcrResult>()
        val consumer = launch { source.task.awaitOwnedResult() }
        yield()

        consumer.cancelAndJoin()
        source.setResult(JapaneseOcrResult("", emptyList(), bitmap))
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()

        assertTrue(bitmap.isRecycled)
    }
}
