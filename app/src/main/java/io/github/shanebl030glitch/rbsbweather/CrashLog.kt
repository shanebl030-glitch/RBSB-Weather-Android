// If the app ever closes unexpectedly, the reason is saved, and shown the next time it opens so it
// can be copied or shared with the developer. Nothing is sent anywhere by itself.
// Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.shanebl030glitch.rbsbweather.data.Version
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant

object CrashLog {
    private const val NAME = "last_crash.txt"
    @Volatile private var installed = false

    fun file(ctx: Context) = File(ctx.filesDir, NAME)

    /** The report saved for an uncaught error: app and phone details, then the stack trace. */
    fun report(e: Throwable, thread: String, now: Instant = Instant.now()): String {
        val sw = StringWriter(); e.printStackTrace(PrintWriter(sw))
        return "RBSB-Weather for Android ${Version.NAME} closed unexpectedly\n" +
            "When: $now (UTC)\nThread: $thread\n" +
            "Phone: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
            "Memory: ${Runtime.getRuntime().maxMemory() / 1048576} MB allowed\n\n" + sw.toString().take(30_000)
    }

    /** Save the reason for any crash, then let Android close the app as usual. */
    fun install(ctx: Context) {
        if (installed) return
        installed = true
        val app = ctx.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try { file(app).writeText(report(e, t.name)) } catch (_: Throwable) { }
            previous?.uncaughtException(t, e)
        }
    }

    fun pending(ctx: Context): String? = file(ctx).takeIf { it.isFile }?.let { try { it.readText() } catch (e: Exception) { null } }
    fun clear(ctx: Context) { file(ctx).delete() }
}

/** Shown once after a crash: what happened, with Copy and Share so it can be sent to Shane. */
@Composable
fun CrashDialog(ctx: Context, initial: String?) {
    var text by remember { mutableStateOf(initial) }
    val t = text ?: return
    fun close() { CrashLog.clear(ctx); text = null }
    AlertDialog(
        onDismissRequest = { close() },
        modifier = Modifier.testTag("crashDialog"),
        title = { Text("RBSB-Weather closed unexpectedly last time") },
        text = {
            androidx.compose.foundation.layout.Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                Text("Sorry about that. Please send these details to Shane so it can be fixed (nothing has been sent).", fontSize = 14.sp)
                Text(t.take(4000), fontSize = 11.sp, modifier = Modifier.testTag("crashText"))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, "RBSB-Weather for Android crash report").putExtra(Intent.EXTRA_TEXT, t)
                try { ctx.startActivity(Intent.createChooser(send, "Send crash details").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { }
                close()
            }, modifier = Modifier.testTag("crashShare")) { Text("Share") }
        },
        dismissButton = {
            androidx.compose.foundation.layout.Row {
                TextButton(onClick = {
                    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("RBSB-Weather crash", t))
                }, modifier = Modifier.testTag("crashCopy")) { Text("Copy") }
                TextButton(onClick = { close() }, modifier = Modifier.testTag("crashClose")) { Text("Close") }
            }
        },
    )
}
