// Menu → Test connection (0.6), as on the desktop's Help menu: a plain-text report of where any
// delay comes from, with Copy and Share. Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.shanebl030glitch.rbsbweather.data.Version

/** "Wi-Fi", "mobile data", "Wi-Fi via a VPN" or "no network". */
fun networkKind(ctx: Context): String = try {
    val cm = ctx.getSystemService(ConnectivityManager::class.java)
    val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
    if (caps == null) "no network" else {
        val kind = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile data"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "a network"
        }
        kind + (if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) " via a VPN" else "") +
            (if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) " (Android says it has no internet)" else "")
    }
} catch (e: Exception) { "unknown" }

fun connectionHeader(ctx: Context) = listOf(
    "RBSB-Weather for Android ${Version.NAME} connection test – ${Version.AUTHOR_CREDIT}",
    "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) on ${Build.MANUFACTURER} ${Build.MODEL}",
    "Network: ${networkKind(ctx)}",
)

@Composable
fun ConnectionScreen(state: UiState, model: AppModel, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val report = state.connection.joinToString("\n")
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("connection"),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Checks each weather service separately: finding it (DNS), connecting over IPv4 and IPv6, the secure handshake, " +
            "then a real place search and a forecast from each provider you use. It shows where any delay comes from. " +
            "No API key appears in the report.", fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { model.testConnection(connectionHeader(ctx)) }, enabled = !state.connectionRunning,
                modifier = Modifier.testTag("runTest")) { Text(if (state.connection.isEmpty()) "Run the test" else "Run again") }
            OutlinedButton(onClick = {
                (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("RBSB-Weather connection test", report))
                model.say("Copied the report.")
            }, enabled = report.isNotEmpty() && !state.connectionRunning) { Text("Copy") }
            OutlinedButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, "RBSB-Weather connection test").putExtra(Intent.EXTRA_TEXT, report)
                try { ctx.startActivity(Intent.createChooser(send, "Share the report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
            }, enabled = report.isNotEmpty() && !state.connectionRunning) { Text("Share") }
        }
        if (state.connectionRunning) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (report.isNotEmpty()) SelectionContainer {
            Text(report, fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.testTag("connectionReport"))
        }
    }
}
