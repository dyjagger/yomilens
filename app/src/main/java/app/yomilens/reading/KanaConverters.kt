package app.yomilens.reading

import app.yomilens.text.JapaneseScript

object KanaScripts {
    fun katakanaToHiragana(text: String): String = buildString(text.length) {
        text.forEach { character ->
            append(
                if (character.code in 0x30A1..0x30F6) {
                    (character.code - 0x60).toChar()
                } else {
                    character
                },
            )
        }
    }

    fun hiraganaToKatakana(text: String): String = buildString(text.length) {
        text.forEach { character ->
            append(
                if (character.code in 0x3041..0x3096) {
                    (character.code + 0x60).toChar()
                } else {
                    character
                },
            )
        }
    }

    fun containsKanji(text: String): Boolean = JapaneseScript.containsKanji(text)
}

object HepburnRomanizer {
    private val digraphs = mapOf(
        "キャ" to "kya", "キュ" to "kyu", "キョ" to "kyo",
        "ギャ" to "gya", "ギュ" to "gyu", "ギョ" to "gyo",
        "シャ" to "sha", "シュ" to "shu", "ショ" to "sho", "シェ" to "she",
        "ジャ" to "ja", "ジュ" to "ju", "ジョ" to "jo", "ジェ" to "je",
        "チャ" to "cha", "チュ" to "chu", "チョ" to "cho", "チェ" to "che",
        "ニャ" to "nya", "ニュ" to "nyu", "ニョ" to "nyo",
        "ヒャ" to "hya", "ヒュ" to "hyu", "ヒョ" to "hyo",
        "ビャ" to "bya", "ビュ" to "byu", "ビョ" to "byo",
        "ピャ" to "pya", "ピュ" to "pyu", "ピョ" to "pyo",
        "ミャ" to "mya", "ミュ" to "myu", "ミョ" to "myo",
        "リャ" to "rya", "リュ" to "ryu", "リョ" to "ryo",
        "ファ" to "fa", "フィ" to "fi", "フェ" to "fe", "フォ" to "fo",
        "ティ" to "ti", "トゥ" to "tu", "ディ" to "di", "ドゥ" to "du",
        "ウィ" to "wi", "ウェ" to "we", "ウォ" to "wo",
        "ツァ" to "tsa", "ツィ" to "tsi", "ツェ" to "tse", "ツォ" to "tso",
        "ヴァ" to "va", "ヴィ" to "vi", "ヴェ" to "ve", "ヴォ" to "vo",
    )

    private val monographs = mapOf(
        'ア' to "a", 'イ' to "i", 'ウ' to "u", 'エ' to "e", 'オ' to "o",
        'カ' to "ka", 'キ' to "ki", 'ク' to "ku", 'ケ' to "ke", 'コ' to "ko",
        'ガ' to "ga", 'ギ' to "gi", 'グ' to "gu", 'ゲ' to "ge", 'ゴ' to "go",
        'サ' to "sa", 'シ' to "shi", 'ス' to "su", 'セ' to "se", 'ソ' to "so",
        'ザ' to "za", 'ジ' to "ji", 'ズ' to "zu", 'ゼ' to "ze", 'ゾ' to "zo",
        'タ' to "ta", 'チ' to "chi", 'ツ' to "tsu", 'テ' to "te", 'ト' to "to",
        'ダ' to "da", 'ヂ' to "ji", 'ヅ' to "zu", 'デ' to "de", 'ド' to "do",
        'ナ' to "na", 'ニ' to "ni", 'ヌ' to "nu", 'ネ' to "ne", 'ノ' to "no",
        'ハ' to "ha", 'ヒ' to "hi", 'フ' to "fu", 'ヘ' to "he", 'ホ' to "ho",
        'バ' to "ba", 'ビ' to "bi", 'ブ' to "bu", 'ベ' to "be", 'ボ' to "bo",
        'パ' to "pa", 'ピ' to "pi", 'プ' to "pu", 'ペ' to "pe", 'ポ' to "po",
        'マ' to "ma", 'ミ' to "mi", 'ム' to "mu", 'メ' to "me", 'モ' to "mo",
        'ヤ' to "ya", 'ユ' to "yu", 'ヨ' to "yo",
        'ラ' to "ra", 'リ' to "ri", 'ル' to "ru", 'レ' to "re", 'ロ' to "ro",
        'ワ' to "wa", 'ヰ' to "i", 'ヱ' to "e", 'ヲ' to "o", 'ン' to "n",
        'ァ' to "a", 'ィ' to "i", 'ゥ' to "u", 'ェ' to "e", 'ォ' to "o",
        'ヮ' to "wa", 'ヴ' to "vu",
    )

    private val punctuation = mapOf(
        '。' to ".", '、' to ",", '・' to "·",
        '「' to "\"", '」' to "\"", '『' to "\"", '』' to "\"",
        '（' to "(", '）' to ")", '！' to "!", '？' to "?", '：' to ":",
    )

    fun romanize(text: String): String {
        val katakana = KanaScripts.hiraganaToKatakana(text)
        val output = StringBuilder(katakana.length * 2)
        var index = 0
        var geminateNext = false

        while (index < katakana.length) {
            val character = katakana[index]

            if (character == 'ッ') {
                geminateNext = true
                index += 1
                continue
            }

            if (character == 'ー') {
                output.lastOrNull { it.lowercaseChar() in "aeiou" }?.let(output::append)
                index += 1
                continue
            }

            if (character == 'ン') {
                val next = syllableAt(katakana, index + 1)
                val nextStartsWithVowelOrY = next
                    ?.firstOrNull()
                    ?.let { it in "aeiouy" }
                    ?: false
                output.append(if (nextStartsWithVowelOrY) "n'" else "n")
                index += 1
                continue
            }

            val pair = katakana.substring(index, minOf(index + 2, katakana.length))
            val mappedPair = digraphs[pair]
            val syllable = mappedPair ?: monographs[character]

            if (syllable != null) {
                if (geminateNext) {
                    output.append(geminatePrefix(syllable))
                    geminateNext = false
                }
                output.append(syllable)
                index += if (mappedPair != null) 2 else 1
                continue
            }

            output.append(punctuation[character] ?: character)
            geminateNext = false
            index += 1
        }

        return output.toString()
    }

    private fun syllableAt(text: String, index: Int): String? {
        if (index >= text.length) return null
        val pair = text.substring(index, minOf(index + 2, text.length))
        return digraphs[pair] ?: monographs[text[index]]
    }

    private fun geminatePrefix(syllable: String): String = when {
        syllable.startsWith("ch") -> "t"
        syllable.firstOrNull()?.let { it in "bcdfghjkmprstvwxyz" } == true -> {
            syllable.first().toString()
        }
        else -> ""
    }
}
