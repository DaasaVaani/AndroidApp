package `in`.daasavaani.core

import kotlin.test.*
import org.junit.Test

class ReadingPreferencesTest {
    @Test fun configuredDefaultPreservesNativeAppearanceAndEveryDensityIsDistinct() {
        val normal = ReadingPreferences()
        assertTrue(normal.valid())
        assertEquals("normal", normal.density)
        assertEquals(ReadingPolicy.readerFontSizeSp, normal.fontSizeSp)
        assertEquals(ReadingPolicy.readerLineHeightSp.toFloat(), normal.lineHeightSp())
        assertEquals(ReadingPolicy.readerLineSpacingDp.toFloat(), normal.lineSpacingDp())
        val compact = normal.copy(density = "compact")
        val relaxed = normal.copy(density = "relaxed")
        assertTrue(compact.lineHeightSp() < normal.lineHeightSp())
        assertTrue(normal.lineHeightSp() < relaxed.lineHeightSp())
        assertTrue(compact.lineSpacingDp() < normal.lineSpacingDp())
        assertTrue(normal.lineSpacingDp() < relaxed.lineSpacingDp())
        assertEquals(normal.lineHeightSp() * (normal.step(1).fontSizeSp.toFloat() / normal.fontSizeSp),
            normal.step(1).lineHeightSp())
    }

    @Test fun actualBoundsStepsAndBoundNoOpsHaveNoHiddenFallback() {
        val sizes = generateSequence(ReadingPreferences(ReadingPolicy.readerFontMinSp)) {
            if (it.fontSizeSp == ReadingPolicy.readerFontMaxSp) null else it.step(1)
        }.toList()
        assertEquals((ReadingPolicy.readerFontMaxSp - ReadingPolicy.readerFontMinSp) / ReadingPolicy.readerFontStepSp + 1, sizes.size)
        sizes.zipWithNext().forEach { (a, b) -> assertEquals(ReadingPolicy.readerFontStepSp, b.fontSizeSp - a.fontSizeSp) }
        assertEquals(sizes.first(), sizes.first().step(-1))
        assertEquals(sizes.last(), sizes.last().step(1))
        sizes.forEach { assertTrue(it.valid()) }
        assertFalse(ReadingPreferences(ReadingPolicy.readerFontMinSp - 1).valid())
        assertFalse(ReadingPreferences(ReadingPolicy.readerFontMaxSp + 1).valid())
        assertFalse(ReadingPreferences(ReadingPolicy.readerFontMinSp + 1).valid())
        assertFalse(ReadingPreferences(density = "future").valid())
        assertFailsWith<IllegalArgumentException> { ReadingPreferences().step(0) }
        assertFailsWith<IllegalArgumentException> { ReadingPreferences(density = "future").lineHeightSp() }
    }
}
