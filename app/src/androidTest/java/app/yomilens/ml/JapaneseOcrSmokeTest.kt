package app.yomilens.ml

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JapaneseOcrSmokeTest {
    @Test
    fun bundledModelReadsAHighContrastJapaneseSample() = runBlocking {
        val bitmap = Bitmap.createBitmap(1_600, 420, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            drawText(
                "日本語を勉強します",
                55f,
                275f,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    textSize = 175f
                },
            )
        }
        val recognizer = TextRecognition.getClient(
            JapaneseTextRecognizerOptions.Builder().build(),
        )

        try {
            val recognized = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().text
            val cleaned = JapaneseTextCleaner.clean(recognized)
            assertTrue("Recognized text was: $recognized", cleaned.contains("日本語"))
        } finally {
            recognizer.close()
            bitmap.recycle()
        }
    }
}
