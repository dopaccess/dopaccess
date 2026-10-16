package jp.dopaccess.widget

import jp.dopaccess.widget.data.Spot
import jp.dopaccess.widget.widget.MapLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapLayoutTest {
    @Test fun oneSpritePerPersonWhenQuiet() {
        val f = MapLayout.figures(listOf(Spot(13, "mobile", 3), Spot(27, "desktop", 1)))
        assertEquals(4, f.size)
        assertEquals(3, f.count { it.pref == 13 && !it.desktop })
    }

    @Test fun keepsHeadcountWhenBusy() {
        val f = MapLayout.figures((1..47).map { Spot(it, "mobile", 30) })
        assertEquals(47 * 30, f.sumOf { it.people })
        assertTrue(f.size < 100)
    }

    @Test fun unknownPlaceGoesToCentralHonshu() {
        val f = MapLayout.figures(listOf(Spot(0, "desktop", 1), Spot(-1, "desktop", 2)))
        assertEquals(listOf(20, -1, -1), f.map { it.pref })
    }

    @Test fun emptyIsEmpty() {
        assertTrue(MapLayout.figures(emptyList()).isEmpty())
        assertTrue(MapLayout.figures(listOf(Spot(13, "mobile", 0))).isEmpty())
    }
}
