// Menu → Check for updates (0.6), the phone version of the desktop's Help → Check for Updates:
// asks GitHub, shows what's new and the release's published SHA-256, and opens the download in the
// phone's browser; Android installs it as any downloaded app. (0.6.0 downloaded and installed it
// itself; Google Play Protect blocked that as possibly harmful, so 0.6.1 leaves it to the browser.)
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.shanebl030glitch.rbsbweather.AppGraph
import io.github.shanebl030glitch.rbsbweather.data.UpdateClient
import io.github.shanebl030glitch.rbsbweather.data.UpdateInfo
import io.github.shanebl030glitch.rbsbweather.data.UpdateStore
import io.github.shanebl030glitch.rbsbweather.data.Updates
import io.github.shanebl030glitch.rbsbweather.data.Version
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

sealed interface UpdateUi {
    data object Idle : UpdateUi
    data object Checking : UpdateUi
    data class UpToDate(val latest: String) : UpdateUi
    data class Available(val info: UpdateInfo, val sha256: String?) : UpdateUi
    data class Failed(val message: String) : UpdateUi
}

@Composable
fun UpdatesScreen(state: UiState, model: AppModel, modifier: Modifier = Modifier,
                  client: UpdateClient? = null, store: UpdateStore? = null, openUrl: ((String) -> Unit)? = null) {
    val ctx = LocalContext.current
    val uri = LocalUriHandler.current
    val g = remember { AppGraph.get(ctx) }
    val cl = client ?: g.updateClient
    val st = store ?: g.updateStore
    val open = openUrl ?: { u: String -> try { uri.openUri(u) } catch (e: Exception) { } }
    val scope = rememberCoroutineScope()
    var ui by remember { mutableStateOf<UpdateUi>(UpdateUi.Idle) }
    var lastOk by remember { mutableStateOf(st.lastOk) }

    fun check() {
        ui = UpdateUi.Checking
        scope.launch {
            ui = withContext(Dispatchers.IO) {
                st.markAttempt()
                try {
                    val info = cl.latest()
                    st.markOk(info.version)
                    if (Updates.isNewer(info)) UpdateUi.Available(info, cl.checksum(info)) else UpdateUi.UpToDate(info.version)
                } catch (e: Exception) { UpdateUi.Failed(e.message ?: "Couldn't check for updates.") }
            }
            lastOk = st.lastOk
        }
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("updates"),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("You have RBSB-Weather for Android ${Version.NAME}.", fontWeight = FontWeight.Bold)
        if (lastOk > 0) Text("Last checked " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date((lastOk * 1000).toLong())) + ".", fontSize = 13.sp)
        when (val u = ui) {
            UpdateUi.Idle -> Button(onClick = { check() }, modifier = Modifier.testTag("checkNow")) { Text("Check now") }
            UpdateUi.Checking -> { Text("Asking GitHub…"); LinearProgressIndicator(Modifier.fillMaxWidth()) }
            is UpdateUi.UpToDate -> {
                Text("You have the newest version." + if (u.latest != Version.NAME) " (GitHub's newest release is ${u.latest}.)" else "", modifier = Modifier.testTag("upToDate"))
                OutlinedButton(onClick = { check() }) { Text("Check again") }
            }
            is UpdateUi.Available -> Release(u.info, u.sha256, st, onDownload = { if (Updates.hostAllowed(u.info.apkUrl)) open(u.info.apkUrl) },
                onOpen = { open(u.info.pageUrl) }, onSkip = { st.skipped = u.info.version; ui = UpdateUi.Idle })
            is UpdateUi.Failed -> {
                Text(u.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("updateError"))
                OutlinedButton(onClick = { check() }) { Text("Try again") }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Check automatically", fontWeight = FontWeight.Bold)
                Text("Once a day; you get a notification when there's a newer version.", fontSize = 13.sp)
            }
            Switch(state.settings.updateCheck, { model.setUpdateCheck(it) }, modifier = Modifier.testTag("updateAuto"))
        }
        Text("Updates come only from RBSB-Weather for Android's releases on GitHub. The app checks and tells you; your browser downloads " +
            "the new version and Android installs it, as when you first installed the app. Android only installs it over this one if " +
            "it's signed the same way, and your places and settings are kept.", fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        TextButton(onClick = { open(Updates.RELEASES_PAGE) }) { Text("All releases on GitHub") }
    }
}

@Composable
private fun Release(info: UpdateInfo, sha256: String?, st: UpdateStore, onDownload: () -> Unit, onOpen: () -> Unit, onSkip: () -> Unit) {
    Card(Modifier.fillMaxWidth().testTag("updateAvailable")) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${info.title} is available", fontWeight = FontWeight.Bold)
            if (info.published.length >= 10) Text("Released ${info.published.take(10)}", fontSize = 13.sp)
            if (info.notes.isNotBlank()) Text(info.notes.take(4000), fontSize = 13.sp)
            if (info.installable) {
                Button(onClick = onDownload, modifier = Modifier.testTag("download")) {
                    Text("Download from GitHub" + if (info.apkSize > 0) " (%.1f MB)".format(info.apkSize / 1048576.0) else "") }
                Text("Your browser downloads ${info.apkName}. When it's finished, open it (from the notification or Files → Downloads) " +
                    "and tap Install.", fontSize = 13.sp)
                if (sha256 != null) Text("To check the download: its SHA-256 should be\n$sha256", fontSize = 11.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, modifier = Modifier.testTag("updateSha"))
            } else Text("This release has no Android app file attached; open it on GitHub.", fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onOpen) { Text("Open on GitHub") }
                if (st.skipped != info.version) TextButton(onClick = onSkip, modifier = Modifier.testTag("skipVersion")) { Text("Skip this version") }
            }
        }
    }
}
