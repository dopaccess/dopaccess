package jp.dopaccess.widget.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import jp.dopaccess.widget.data.Fetcher
import jp.dopaccess.widget.data.Ga4
import jp.dopaccess.widget.data.GoogleAuth
import jp.dopaccess.widget.data.Property
import jp.dopaccess.widget.data.SettingsStore
import jp.dopaccess.widget.widget.DopaWidget
import jp.dopaccess.widget.work.RefreshWorker
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = SettingsStore(applicationContext)
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface(Modifier.fillMaxSize()) { Screen(store) }
            }
        }
    }

    @Composable
    private fun Screen(store: SettingsStore) {
        val scope = rememberCoroutineScope()
        var properties by remember { mutableStateOf<List<Property>>(emptyList()) }
        var selected by remember { mutableStateOf<Property?>(null) }
        var savedName by remember { mutableStateOf("") }
        var interval by remember { mutableIntStateOf(30) }
        var status by remember { mutableStateOf("") }

        fun loadProperties(token: String) = scope.launch {
            status = "サイト一覧を読み込み中…"
            status = try {
                properties = Ga4(token).properties()
                selected = properties.firstOrNull { it.id == store.settings().propertyId } ?: selected
                if (properties.isEmpty()) "見られるGA4プロパティがありません" else ""
            } catch (e: Ga4.HttpError) {
                if (e.code == 403) "GA4 Admin API が無効か、権限がありません" else "読み込み失敗: HTTP ${e.code}"
            } catch (e: Exception) {
                "読み込み失敗: ${e.javaClass.simpleName}"
            }
        }

        val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
            when (val t = GoogleAuth.fromIntent(applicationContext, res.data)) {
                is GoogleAuth.Token.Ok -> loadProperties(t.accessToken)
                is GoogleAuth.Token.NeedsConsent -> status = "許可されませんでした"
                is GoogleAuth.Token.Fail -> status = "ログイン失敗: ${t.message}"
            }
        }

        fun signIn() = scope.launch {
            status = "ログイン中…"
            when (val t = GoogleAuth.token(applicationContext)) {
                is GoogleAuth.Token.Ok -> loadProperties(t.accessToken)
                is GoogleAuth.Token.NeedsConsent -> consent.launch(IntentSenderRequest.Builder(t.intent.intentSender).build())
                is GoogleAuth.Token.Fail -> status = "ログイン失敗: ${t.message}"
            }
        }

        LaunchedEffect(Unit) {
            val s = store.settings()
            interval = s.intervalMin
            savedName = s.propertyName
            if (s.propertyId.isNotBlank()) selected = Property(s.propertyId, s.propertyName, "")
        }

        Column(
            Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Dopaccess", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Googleアカウントでログインして、表示するサイトを選んでください。データはこのスマホがGoogleから直接読み取るだけで、ほかのサーバーには送りません。",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = { signIn() }) {
                Text(if (properties.isEmpty()) "Googleでログインしてサイトを選ぶ" else "サイト一覧を再読み込み")
            }
            if (properties.isEmpty() && savedName.isNotBlank()) Text("表示中のサイト: $savedName")
            properties.forEach { p ->
                Row(
                    Modifier.fillMaxWidth().clickable { selected = p },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected?.id == p.id, onClick = { selected = p })
                    Column {
                        Text(p.name)
                        Text("${p.account} · ${p.id}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Text("自動更新の間隔（Android の省電力で遅れることがあります）")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 60).forEach { m ->
                    FilterChip(selected = interval == m, onClick = { interval = m }, label = { Text("${m}分") })
                }
            }
            Text("ウィジェットをタップすると更新、右上の歯車でこの画面が開きます。")
            Button(enabled = selected != null, onClick = {
                scope.launch {
                    val p = selected ?: return@launch
                    store.save(p, interval)
                    savedName = p.name
                    status = "保存しました。取得中…"
                    val r = Fetcher.fetch(applicationContext, p.id)
                    when (r) {
                        is Fetcher.Result.Ok -> store.saveResult(r.reading, null)
                        is Fetcher.Result.Fail -> store.saveResult(null, r.message)
                    }
                    RefreshWorker.schedule(applicationContext, interval)
                    DopaWidget().updateAll(applicationContext)
                    status = when (r) {
                        is Fetcher.Result.Ok -> "保存しました: いま ${r.reading.total} 人（PC ${r.reading.desktop} / スマホ ${r.reading.mobile}）"
                        is Fetcher.Result.Fail -> "保存しましたが取得に失敗: ${r.message}"
                    }
                }
            }) { Text("保存") }
            if (status.isNotEmpty()) Text(status)
        }
    }
}
