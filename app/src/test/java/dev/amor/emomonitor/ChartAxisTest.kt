package dev.amor.emomonitor

import dev.amor.emomonitor.ui.trend.chartAxis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartAxisTest {

    @Test
    fun keepsMinimumSpanAndNonNegativeFloor() {
        val axis = chartAxis(48.5, 52.0)
        assertTrue(axis.hi - axis.lo >= 40f)
        assertTrue(axis.lo >= 0f)
    }

    @Test
    fun gridValuesAreIntegersOnNiceSteps() {
        val choices = listOf(10, 20, 25, 50, 100)
        listOf(0.0 to 0.0, 40.0 to 52.0, 100.0 to 134.0, 200.0 to 210.0, 420.0 to 425.0).forEach { (min, max) ->
            val axis = chartAxis(min, max)
            assertTrue("step from choices", axis.step in choices)
            assertEquals("lo on step", 0f, axis.lo % axis.step)
            assertEquals("hi on step", 0f, axis.hi % axis.step)
            assertTrue("at least one inner line", axis.hi - axis.lo >= axis.step)
        }
    }
}
