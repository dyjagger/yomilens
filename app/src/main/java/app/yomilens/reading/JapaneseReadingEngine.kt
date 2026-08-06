package app.yomilens.reading

import app.yomilens.model.ReadingLine
import app.yomilens.model.ReadingToken
import com.atilika.kuromoji.ipadic.Token
import com.atilika.kuromoji.ipadic.Tokenizer

class JapaneseReadingEngine(
    tokenizerFactory: () -> Tokenizer = { Tokenizer() },
) {
    private val tokenizer by lazy(LazyThreadSafetyMode.SYNCHRONIZED, tokenizerFactory)

    fun annotate(text: String): List<ReadingLine> = text
        .lineSequence()
        .map { line ->
            ReadingLine(
                tokens = if (line.isBlank()) {
                    emptyList()
                } else {
                    tokenizer.tokenize(line).map(::toReadingToken)
                },
            )
        }
        .toList()

    fun romanize(lines: List<ReadingLine>): String = lines.joinToString("\n") { line ->
        buildString {
            line.tokens.forEachIndexed { index, token ->
                val romanized = romanizeToken(token)
                if (index > 0 && shouldInsertSpace(line.tokens[index - 1], token)) {
                    append(' ')
                }
                append(romanized)
            }
        }
    }

    private fun toReadingToken(token: Token): ReadingToken {
        val reading = token.reading.takeUnless { it == "*" }.orEmpty()
        val surface = token.surface
        return ReadingToken(
            surface = surface,
            furigana = reading
                .takeIf { it.isNotBlank() && KanaScripts.containsKanji(surface) }
                ?.let(KanaScripts::katakanaToHiragana),
            readingKatakana = reading.ifBlank { surface },
            isParticle = token.partOfSpeechLevel1 == "助詞",
        )
    }

    private fun romanizeToken(token: ReadingToken): String {
        if (token.isParticle) {
            when (token.surface) {
                "は" -> return "wa"
                "へ" -> return "e"
                "を" -> return "o"
            }
        }
        return HepburnRomanizer.romanize(token.readingKatakana)
    }

    private fun shouldInsertSpace(previous: ReadingToken, current: ReadingToken): Boolean {
        val closing = "。、！？!?.,:：）」』】]"
        val opening = "（「『【["
        val currentStartsWithClosing = current.surface.firstOrNull()?.let(closing::contains) ?: false
        val previousEndsWithOpening = previous.surface.lastOrNull()?.let(opening::contains) ?: false
        return !currentStartsWithClosing && !previousEndsWithOpening
    }
}
