package jp.dopaccess.widget.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import jp.dopaccess.widget.data.SettingsStore
import jp.dopaccess.widget.data.Snapshot
import jp.dopaccess.widget.ui.SettingsActivity
import jp.dopaccess.widget.work.RefreshWorker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DopaWidget : GlanceAppWidget() {
    // Exact: LocalSize is the real widget size (used to pick the count's font size).
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val store = SettingsStore(context)
        val snap = store.snapshot()
        provideContent { Content(snap) }
    }

    @Composable
    private fun Content(snap: Snapshot) {
        val size = LocalSize.current
        val r = snap.reading
        val faded = snap.error != null
        val time = r?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it.fetchedAtMs)) } ?: "--:--"
        // Stale or failed: grey the text out so old numbers don't pass for live ones.
        val fg = ColorProvider(if (faded) Color(0xFF9AA3B0) else Color.White)
        val sub = ColorProvider(if (faded) Color(0xFF7D8590) else Color(0xFFC9D3E0))
        val countSp = if (size.height >= 160.dp) 56.sp else 44.sp

        Box(
            modifier = GlanceModifier.fillMaxSize().appWidgetBackground().cornerRadius(16.dp)
                .background(Color(0xFF1C2A44)).clickable(actionRunCallback<RefreshAction>()),
        ) {
            // The count is the one thing that must read at a glance, so it gets the biggest type.
            Row(modifier = GlanceModifier.padding(start = 12.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    r?.total?.toString() ?: "--",
                    style = TextStyle(color = fg, fontWeight = FontWeight.Bold, fontSize = countSp),
                )
                Column(modifier = GlanceModifier.padding(start = 8.dp)) {
                    Text(
                        if (r != null) "人 · $time" + (snap.error?.let { " · $it" } ?: "") else (snap.error ?: "⚙で設定"),
                        style = TextStyle(color = sub, fontSize = 12.sp),
                        maxLines = 1,
                    )
                    r?.let { g ->
                        Text(
                            "💻 ${g.desktop}  📱 ${g.mobile}",
                            style = TextStyle(color = fg, fontWeight = FontWeight.Bold, fontSize = 14.sp),
                            maxLines = 1,
                        )
                    }
                }
            }
            Box(modifier = GlanceModifier.fillMaxSize().padding(6.dp), contentAlignment = Alignment.TopEnd) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (snap.refreshing) {
                        CircularProgressIndicator(color = fg, modifier = GlanceModifier.size(18.dp).padding(end = 4.dp))
                    }
                    Text("⚙", style = TextStyle(color = sub, fontSize = 18.sp),
                        modifier = GlanceModifier.clickable(actionStartActivity<SettingsActivity>()))
                }
            }
        }
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        if (SettingsStore(context).tryManual()) {
            DopaWidget().update(context, glanceId) // show the spinner right away
            RefreshWorker.refreshNow(context)
        }
    }
}

class DopaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DopaWidget()
}
