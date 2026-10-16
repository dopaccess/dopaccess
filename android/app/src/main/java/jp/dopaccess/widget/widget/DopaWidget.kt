package jp.dopaccess.widget.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import jp.dopaccess.widget.data.SettingsStore
import jp.dopaccess.widget.data.Snapshot
import jp.dopaccess.widget.data.Tier
import jp.dopaccess.widget.ui.SettingsActivity
import jp.dopaccess.widget.work.RefreshWorker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DopaWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(
        setOf(DpSize(110.dp, 110.dp), DpSize(250.dp, 110.dp), DpSize(250.dp, 180.dp)),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = SettingsStore(context)
        val snap = store.snapshot()
        val mild = store.settings().mild
        provideContent { Content(snap, mild) }
    }

    @Composable
    private fun Content(snap: Snapshot, mild: Boolean) {
        val ctx = LocalContext.current
        val size = LocalSize.current
        val density = ctx.resources.displayMetrics.density
        val dark = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val r = snap.response
        val faded = r?.stale == true || snap.error != null
        val bmp = CrowdRenderer.render((size.width.value * density).toInt(), (size.height.value * density).toInt(), r, dark, faded, mild)
        val time = if (snap.updatedAtMs > 0) SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(snap.updatedAtMs)) else "--:--"
        val tier = r?.let { Tier.parse(it.tier) } ?: Tier.ERROR
        val fg = ColorProvider(if (CrowdRenderer.lightSky(tier, dark)) Color(0xFF102030) else Color.White)
        val countSp = if (size.height >= 160.dp) 56.sp else 44.sp

        Box(modifier = GlanceModifier.fillMaxSize().clickable(actionRunCallback<RefreshAction>())) {
            Image(
                provider = ImageProvider(bmp),
                contentDescription = tier.name,
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier.fillMaxSize(),
            )
            // The count is the one thing that must read at a glance, so it gets the biggest type.
            Row(modifier = GlanceModifier.padding(start = 10.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    r?.current?.toString() ?: "--",
                    style = TextStyle(color = fg, fontWeight = FontWeight.Bold, fontSize = countSp),
                )
                Column(modifier = GlanceModifier.padding(start = 6.dp)) {
                    Text(label(tier), style = TextStyle(color = fg, fontWeight = FontWeight.Bold, fontSize = 13.sp))
                    Text(
                        if (r != null) "人 · $time" else (snap.error ?: "未設定"),
                        style = TextStyle(color = fg, fontSize = 11.sp),
                        maxLines = 1,
                    )
                }
            }
            Box(modifier = GlanceModifier.fillMaxSize().padding(6.dp), contentAlignment = Alignment.TopEnd) {
                Text("⚙", style = TextStyle(color = fg, fontSize = 18.sp),
                    modifier = GlanceModifier.clickable(actionStartActivity<SettingsActivity>()))
            }
        }
    }

    private fun label(t: Tier) = when (t) {
        Tier.KYOMU -> "虚無"
        Tier.KANKOTORI -> "閑古鳥"
        Tier.NORMAL -> "平常運転"
        Tier.WAKUWAKU -> "ワクワク"
        Tier.BAKU_AGE -> "爆上げ"
        Tier.GOD_BURST -> "神バースト"
        Tier.ERROR -> "通信エラー"
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        if (SettingsStore(context).tryManual()) RefreshWorker.refreshNow(context)
    }
}

class DopaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DopaWidget()
}
