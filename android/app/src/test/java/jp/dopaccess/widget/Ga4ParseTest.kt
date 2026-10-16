package jp.dopaccess.widget

import jp.dopaccess.widget.data.AppJson
import jp.dopaccess.widget.data.Ga4
import jp.dopaccess.widget.data.Property
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class Ga4ParseTest {
    private fun obj(s: String) = AppJson.parseToJsonElement(s).jsonObject

    @Test fun realtimeSplitsDesktopFromPhonesAndTablets() {
        val r = Ga4.parseRealtime(obj("""{"rows":[
            {"dimensionValues":[{"value":"mobile"}],"metricValues":[{"value":"30"}]},
            {"dimensionValues":[{"value":"desktop"}],"metricValues":[{"value":"50"}]},
            {"dimensionValues":[{"value":"tablet"}],"metricValues":[{"value":"5"}]}]}"""), 1L)
        assertEquals(85, r.total); assertEquals(50, r.desktop); assertEquals(35, r.mobile)
    }

    @Test fun realtimeWithNoRowsIsZero() {
        assertEquals(0, Ga4.parseRealtime(obj("{}"), 1L).total)
    }

    @Test fun propertiesAreFlattenedAcrossAccounts() {
        val ps = Ga4.parseProperties(obj("""{"accountSummaries":[
            {"displayName":"A","propertySummaries":[{"property":"properties/1","displayName":"one"}]},
            {"displayName":"B","propertySummaries":[{"property":"properties/2","displayName":"two"}]},
            {"displayName":"C"}]}"""))
        assertEquals(listOf(Property("1", "one", "A"), Property("2", "two", "B")), ps)
    }
}
