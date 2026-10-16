package jp.dopaccess.widget.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.ui.Alignment
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import jp.dopaccess.widget.data.DopaClient
import jp.dopaccess.widget.data.Settings
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

    @androidx.compose.runtime.Composable
    private fun Screen(store: SettingsStore) {
        val scope = rememberCoroutineScope()
        var url by remember { mutableStateOf("") }
        var secret by remember { mutableStateOf("") }
        var hasSecret by remember { mutableStateOf(false) }
        var property by remember { mutableStateOf("") }
        var interval by remember { mutableIntStateOf(30) }
        var absolute by remember { mutableStateOf(false) }
        var mild by remember { mutableStateOf(false) }
        var status by remember { mutableStateOf("") }

        LaunchedEffect(Unit) {
            val s = store.settings()
            url = s.workerUrl; property = s.property; interval = s.intervalMin; hasSecret = s.apiSecret != null
            absolute = s.absoluteScale; mild = s.mild
        }

        Column(
            Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Dopaccess", style = MaterialTheme.typography.headlineMedium)
            OutlinedTextField(url, { url = it }, label = { Text("Worker URL") },
                placeholder = { Text("https://dopaccess.example.workers.dev") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(secret, { secret = it },
                label = { Text(if (hasSecret) "API secret（保存済み・変更時のみ入力）" else "API secret") },
                singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(property, { property = it }, label = { Text("GA4 プロパティ ID（空なら既定）") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth())
            Text("自動更新の間隔（Android の省電力で遅れることがあります）")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 60).forEach { m ->
                    FilterChip(selected = interval == m, onClick = { interval = m }, label = { Text("${m}分") })
                }
            }
            ToggleRow("人数そのままの規模で表示（オフ：いつもの同じ時間帯と比べて表示）", absolute) { absolute = it }
            ToggleRow("マイルド演出（虚無のときに倒れずお昼寝）", mild) { mild = it }
            Text("ウィジェットをタップすると更新、右上の歯車でこの画面が開きます。")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    scope.launch {
                        status = "接続中…"
                        val cur = store.settings()
                        val s = Settings(url.trim().trimEnd('/'), secret.ifBlank { null } ?: cur.apiSecret, property.trim(), interval, absolute, mild)
                        status = when (val r = DopaClient(s).realtime()) {
                            is DopaClient.Result.Ok -> "OK: ${r.body.current} 人 / ${r.body.tier}"
                            is DopaClient.Result.Fail -> "失敗: ${r.message}"
                        }
                    }
                }) { Text("接続テスト") }
                Button(onClick = {
                    scope.launch {
                        store.save(url, secret, property, interval, absolute, mild)
                        if (secret.isNotBlank()) hasSecret = true
                        secret = ""
                        RefreshWorker.schedule(applicationContext, interval)
                        RefreshWorker.refreshNow(applicationContext)
                        DopaWidget().updateAll(applicationContext)
                        status = "保存しました"
                    }
                }) { Text("保存") }
            }
            if (status.isNotEmpty()) Text(status)
        }
    }

    @androidx.compose.runtime.Composable
    private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}
