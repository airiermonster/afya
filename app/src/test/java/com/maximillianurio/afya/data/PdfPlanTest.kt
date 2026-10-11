package com.maximillianurio.afya.data

import com.maximillianurio.afya.core.Sex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sheet is drawn in one pass, so the page count and the axis arithmetic have to be right before
 * the first stroke. These are the figures the renderer bets the whole document on.
 */
class PdfPlanTest {

    @Test
    fun logPagesCoverEveryWeighIn() {
        assertEquals(1, PdfPlan.logPages(0))
        assertEquals(1, PdfPlan.logPages(1))
        assertEquals(1, PdfPlan.logPages(PdfPlan.LOG_ROWS))
        assertEquals(2, PdfPlan.logPages(PdfPlan.LOG_ROWS + 1))
        assertEquals(4, PdfPlan.logPages(100))
    }

    @Test
    fun pageCountCarriesTheFixedPages() {
        // Summary, composition, tape and notes, method: four pages around the log.
        assertEquals(5, PdfPlan.pages(0))
        assertEquals(6, PdfPlan.pages(PdfPlan.LOG_ROWS + 1))
        assertEquals(4 + PdfPlan.logPages(250), PdfPlan.pages(250))
    }

    @Test
    fun domainKeepsASingleFigureVisible() {
        val (low, high) = PdfPlan.domain(listOf(70.0))
        assertTrue("a flat series must still span a range, got $low..$high", high > low)
        assertTrue(low < 70.0 && high > 70.0)
    }

    @Test
    fun domainPadsAroundTheSpread() {
        val (low, high) = PdfPlan.domain(listOf(60.0, 80.0))
        assertTrue(low < 60.0)
        assertTrue(high > 80.0)
    }

    @Test
    fun domainFallsBackWhenNothingWasRecorded() {
        assertEquals(0.0 to 1.0, PdfPlan.domain(emptyList()))
        assertEquals(0.0 to 1.0, PdfPlan.domain(listOf(Double.NaN)))
    }

    @Test
    fun domainIncludesTheReferenceBand() {
        val (low, high) = PdfPlan.domain(listOf(60.0), extra = listOf(90.0))
        assertTrue("the reference span has to fit under the axis, got $low..$high", high > 90.0)
    }

    @Test
    fun xOfSpreadsTheSeriesAcrossThePlot() {
        val (left, right) = 40f to 400f
        assertEquals(40f, PdfPlan.xOf(0L, 0L, 1000L, left, right), 0.01f)
        assertEquals(400f, PdfPlan.xOf(1000L, 0L, 1000L, left, right), 0.01f)
        assertEquals(220f, PdfPlan.xOf(500L, 0L, 1000L, left, right), 0.01f)
        // One weigh-in has no span to stretch: it is centred rather than drawn off the paper.
        assertEquals(220f, PdfPlan.xOf(7L, 7L, 7L, left, right), 0.01f)
    }

    @Test
    fun yOfRunsDownThePageAndClamps() {
        val (top, bottom) = 100f to 250f
        assertEquals(250f, PdfPlan.yOf(10.0, 10.0, 30.0, top, bottom), 0.01f)
        assertEquals(100f, PdfPlan.yOf(30.0, 10.0, 30.0, top, bottom), 0.01f)
        assertEquals(175f, PdfPlan.yOf(20.0, 10.0, 30.0, top, bottom), 0.01f)
        // A figure outside the axis still prints, at the edge of the plot instead of over the margin.
        assertEquals(100f, PdfPlan.yOf(99.0, 10.0, 30.0, top, bottom), 0.01f)
        assertEquals(250f, PdfPlan.yOf(1.0, 10.0, 30.0, top, bottom), 0.01f)
        assertEquals(175f, PdfPlan.yOf(20.0, 30.0, 30.0, top, bottom), 0.01f)
    }

    @Test
    fun spanSitsInsideItsOwnAxis() {
        val (from, to) = PdfPlan.span("fat", Sex.MALE)!!
        assertTrue("the band has to be inside the bar, got $from..$to", from in 0f..1f && to in 0f..1f)
        assertTrue("$from..$to", from < to)
    }

    @Test
    fun massRowsReadAgainstTheirPercentageSibling() {
        assertEquals(PdfPlan.span("fat", Sex.MALE), PdfPlan.span("fat_mass", Sex.MALE))
        assertEquals(PdfPlan.span("skeletal", Sex.FEMALE), PdfPlan.span("skeletal_mass", Sex.FEMALE))
    }

    @Test
    fun rowsWithoutABandHaveNoSpanToDraw() {
        assertNull(PdfPlan.span("bmr", Sex.MALE))
        assertNull(PdfPlan.span("healthy_range", Sex.MALE))
    }
}
