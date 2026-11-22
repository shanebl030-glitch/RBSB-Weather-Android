// Menu → Check for updates (0.6), the phone version of the desktop's Help → Check for Updates:
// asks GitHub, shows what's new, downloads the APK, checks it against SHA256SUMS and hands it to
// Android's installer. Created By Shane BlackbuRn
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
import io.github.shanebl030glitch.rbsbweather.background.Installer
import io.github.shanebl030glitch.rbsbweather.data.UpdateClient
import io.github.shanebl030glitch.rbsbweather.data.UpdateInfo
import io.github.shanebl030glitch.rbsbweather.data.UpdateStore
import io.github.shanebl030glitch.rbsbweather.data.Updates
import io.github.shanebl030glitch.rbsbweather.data.Version
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

sealed interface UpdateUi {
    data object Idle : UpdateUi
    data object Checking : UpdateUi
    data class UpToDate(val latest: String) : UpdateUi
    data class Available(val info: UpdateInfo) : UpdateUi
    data class Downloading(val info: UpdateInfo, val done: Long, val total: Long) : UpdateUi
    data class Ready(val info: UpdateInfo, val file: File) : UpdateUi
    data class Failed(val message: String, val info: UpdateInfo? = null) : UpdateUi
}

@Composable
fun UpdatesScreen(state: UiState, model: AppModel, modifier: Modifier = Modifier,
                  client: UpdateClient? = null, store: UpdateStore? = null, dir: File? = null,
                  checkApk: ((File) -> String?)? = null, install: ((File) -> Boolean)? = null, canInstall: (() -> Boolean)? = null) {
    val ctx = LocalContext.current
    val uri = LocalUriHandler.current
    val g = remember { AppGraph.get(ctx) }
    val cl = client ?: g.updateClient
    val st = store ?: g.updateStore
    val folder = dir ?: Installer.updatesDir(ctx)
    val verify = checkApk ?: { f: File -> Installer.checkApk(ctx, f) }
    val doInstall = install ?: { f: File -> Installer.install(ctx, f) }
    val allowed = canInstall ?: { Installer.canInstall(ctx) }
    val scope = rememberCoroutineScope()
    var ui by remember { mutableStateOf<UpdateUi>(UpdateUi.Idle) }
    var cancelled by remember { mutableStateOf(false) }
    var lastOk by remember { mutableStateOf(st.lastOk) }

    fun check() {
        ui = UpdateUi.Checking
        scope.launch {
            ui = withContext(Dispatchers.IO) {
                st.markAttempt()
                try {
                    val info = cl.latest()
                    st.markOk(info.version)
                    if (Updates.isNewer(info)) UpdateUi.Available(info) else UpdateUi.UpToDate(info.version)
                } catch (e: Exception) { UpdateUi.Failed(e.message ?: "Couldn't check for updates.") }
            }
            lastOk = st.lastOk
        }
    }

    fun download(info: UpdateInfo) {
        cancelled = false
        ui = UpdateUi.Downloading(info, 0, info.apkSize)
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    var lastShown = 0L
                    val f = cl.download(info, folder, { done, total ->
                        if (done - lastShown > 256 * 1024 || done == total) { lastShown = done; ui = UpdateUi.Downloading(info, done, total) }
                        !cancelled
                    }, verify)
                    UpdateUi.Ready(info, f)
                } catch (e: Exception) { UpdateUi.Failed(e.message ?: "The download didn't work.", info) }
            }
            ui = result
            if (result is UpdateUi.Ready && allowed()) doInstall(result.file)
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
            is UpdateUi.Available -> Release(u.info, st, onDownload = { download(u.info) }, onOpen = { uri.openUri(u.info.pageUrl) }, onSkip = { st.skipped = u.info.version; ui = UpdateUi.Idle })
            is UpdateUi.Downloading -> {
                Text("Downloading ${u.info.title}…")
                if (u.total > 0) LinearProgressIndicator(progress = { (u.done.toFloat() / u.total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().testTag("downloadProgress"))
                else LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("%.1f of %s MB".format(u.done / 1048576.0, if (u.total > 0) "%.1f".format(u.total / 1048576.0) else "?"), fontSize = 13.sp)
                TextButton(onClick = { cancelled = true }) { Text("Cancel") }
            }
            is UpdateUi.Ready -> {
                Text("Downloaded and checked: it matches its published checksum and is a newer RBSB-Weather for Android.", modifier = Modifier.testTag("updateReady"))
                if (!allowed()) {
                    Text("Android needs your permission once for RBSB-Weather to install its updates: turn on \"Allow from this source\", then come back and tap Install.", fontSize = 13.sp)
                    OutlinedButton(onClick = { Installer.openInstallPermission(ctx) }) { Text("Allow installs from RBSB-Weather") }
                }
                Button(onClick = { if (!doInstall(u.file)) ui = UpdateUi.Failed("Android's installer couldn't be opened.", u.info) }, modifier = Modifier.testTag("install")) { Text("Install") }
                Text("Android asks you to confirm. Your places and settings are kept.", fontSize = 13.sp)
            }
            is UpdateUi.Failed -> {
                Text(u.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("updateError"))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { if (u.info != null) download(u.info) else check() }) { Text("Try again") }
                    if (u.info != null) TextButton(onClick = { uri.openUri(u.info.pageUrl) }) { Text("Open on GitHub") }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Check automatically", fontWeight = FontWeight.Bold)
                Text("Once a day; you get a notification when there's a newer version.", fontSize = 13.sp)
            }
            Switch(state.settings.updateCheck, { model.setUpdateCheck(it) }, modifier = Modifier.testTag("updateAuto"))
        }
        Text("Updates come only from RBSB-Weather for Android's releases on GitHub. Each download is checked against the release's SHA256SUMS " +
            "before it's offered to Android's installer, and Android refuses one that isn't signed like the app you have.", fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        TextButton(onClick = { uri.openUri(Updates.RELEASES_PAGE) }) { Text("All releases on GitHub") }
    }
}

@Composable
private fun Release(info: UpdateInfo, st: UpdateStore, onDownload: () -> Unit, onOpen: () -> Unit, onSkip: () -> Unit) {
    Card(Modifier.fillMaxWidth().testTag("updateAvailable")) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${info.title} is available", fontWeight = FontWeight.Bold)
            if (info.published.length >= 10) Text("Released ${info.published.take(10)}", fontSize = 13.sp)
            if (info.notes.isNotBlank()) Text(info.notes.take(4000), fontSize = 13.sp)
            if (info.installable) Button(onClick = onDownload, modifier = Modifier.testTag("download")) {
                Text("Download and install" + if (info.apkSize > 0) " (%.1f MB)".format(info.apkSize / 1048576.0) else "") }
            else Text("This release has no Android app file attached; open it on GitHub.", fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onOpen) { Text("Open on GitHub") }
                if (st.skipped != info.version) TextButton(onClick = onSkip, modifier = Modifier.testTag("skipVersion")) { Text("Skip this version") }
            }
        }
    }
}
