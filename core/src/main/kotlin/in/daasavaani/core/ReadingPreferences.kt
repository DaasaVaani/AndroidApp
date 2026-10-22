package `in`.daasavaani.core

data class ReadingPreferences(
    val fontSizeSp: Int = ReadingPolicy.readerFontSizeSp,
    val density: String = ReadingPolicy.defaultDensity,
) {
    fun valid() = fontSizeSp in ReadingPolicy.readerFontMinSp..ReadingPolicy.readerFontMaxSp &&
        (fontSizeSp - ReadingPolicy.readerFontMinSp) % ReadingPolicy.readerFontStepSp == 0 &&
        density in ReadingPolicy.supportedDensities

    fun step(direction: Int): ReadingPreferences {
        require(valid() && direction in setOf(-1, 1))
        return copy(fontSizeSp = (fontSizeSp.toLong() + direction.toLong() * ReadingPolicy.readerFontStepSp)
            .coerceIn(ReadingPolicy.readerFontMinSp.toLong(), ReadingPolicy.readerFontMaxSp.toLong()).toInt())
    }

    private fun multiplier(): Float {
        require(valid())
        return when (density) {
            "compact" -> ReadingPolicy.densityCompactPercent
            "normal" -> ReadingPolicy.densityNormalPercent
            else -> ReadingPolicy.densityRelaxedPercent
        } / 100f
    }
    fun lineHeightSp() = ReadingPolicy.readerLineHeightSp * (fontSizeSp.toFloat() / ReadingPolicy.readerFontSizeSp) * multiplier()
    fun lineSpacingDp() = ReadingPolicy.readerLineSpacingDp * multiplier()
}
