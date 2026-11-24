package dev.amor.emomonitor

import dev.amor.emomonitor.ui.now.levelFraction
import org.junit.Assert.assertEquals
import org.junit.Test

class LevelScaleTest {

    @Test
    fun mapsBoundariesToSegmentEdges() {
        assertEquals(0f, levelFraction(0.0), 1e-6f)
        assertEquals(1f / 7f, levelFraction(35.0), 1e-6f)
        assertEquals(2f / 7f, levelFraction(75.0), 1e-6f)
    }

    @Test
    fun interpolatesInsideSegment() {
        val expected = ((1 + 13.5 / 40.0) / 7.0).toFloat()
        assertEquals(expected, levelFraction(48.5), 1e-6f)
    }

    @Test
    fun clampsAtTopOfLastSegment() {
        val expected = ((5 + 99.0 / 100.0) / 7.0).toFloat()
        assertEquals(expected, levelFraction(349.0), 1e-6f)
        assertEquals(1f, levelFraction(500.0), 1e-6f)
        assertEquals(1f, levelFraction(800.0), 1e-6f)
    }
}
