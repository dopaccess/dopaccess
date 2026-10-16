package jp.dopaccess.widget

import jp.dopaccess.widget.data.ApiJson
import jp.dopaccess.widget.data.RealtimeResponse
import jp.dopaccess.widget.data.Tier
import jp.dopaccess.widget.widget.CrowdLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrowdLayoutTest {
    @Test fun sameSeedSamePicture() {
        assertEquals(CrowdLayout.place(20, 1, Tier.NORMAL, 42), CrowdLayout.place(20, 1, Tier.NORMAL, 42))
    }

    @Test fun capsWalkersAndGiants() {
        val f = CrowdLayout.place(500, 9, Tier.GOD_BURST, 1)
        assertEquals(CrowdLayout.MAX_WALKERS, f.count { !it.giant })
        assertEquals(3, f.count { it.giant })
    }

    @Test fun kyomuFiguresAllUsePoseZero() {
        assertTrue(CrowdLayout.place(10, 0, Tier.KYOMU, 7).all { it.pose == 0 })
    }

    @Test fun sortedBackToFront() {
        val ys = CrowdLayout.place(30, 0, Tier.NORMAL, 3).map { it.y }
        assertEquals(ys.sorted(), ys)
    }

    @Test fun parsesWorkerResponse() {
        val json = """{"property":"534668063","tier":"NORMAL","current":83,"baseline":65,"divergence":27.7,
            "crowd":{"unit":4,"walkers":20,"giants":0},"timezone":"Asia/Tokyo","slot":{"dow":5,"hour":9},
            "baselineUpdatedAt":"2026-10-16T09:04:15+09:00","fetchedAt":"2026-10-16T09:05:47+09:00","cached":false}"""
        val r = ApiJson.decodeFromString(RealtimeResponse.serializer(), json)
        assertEquals(83, r.current)
        assertEquals(20, r.crowd.walkers)
        assertEquals(Tier.NORMAL, Tier.parse(r.tier))
        assertEquals(Tier.NORMAL, Tier.parse("SOMETHING_NEW"))
    }
}
