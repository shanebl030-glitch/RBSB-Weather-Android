// Hands a verified update to Android's installer. Android shows its own "Install?" screen, and
// refuses an APK signed with a different key than the installed app. Created By Shane BlackbuRn
package io.github.shanebl030glitch.rbsbweather.background

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File

object Installer {
    fun updatesDir(ctx: Context) = File(ctx.cacheDir, "updates")

    /** Null if `f` is a newer RBSB-Weather for Android; otherwise why it isn't installed. */
    fun checkApk(ctx: Context, f: File): String? {
        val pm = ctx.packageManager
        @Suppress("DEPRECATION")
        val pi = pm.getPackageArchiveInfo(f.path, 0) ?: return "The download isn't an Android app, so it wasn't installed."
        if (pi.packageName != ctx.packageName) return "The download is a different app (${pi.packageName}), so it wasn't installed."
        @Suppress("DEPRECATION")
        val mine = pm.getPackageInfo(ctx.packageName, 0)
        val newCode = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
        val curCode = if (Build.VERSION.SDK_INT >= 28) mine.longVersionCode else mine.versionCode.toLong()
        if (newCode <= curCode) return "The download isn't newer than the version you have, so it wasn't installed."
        return null
    }

    /** May this app hand APKs to the installer? (Android asks once: "Install unknown apps".) */
    fun canInstall(ctx: Context) = Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(ctx: Context) {
        try {
            ctx.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) { }
    }

    fun install(ctx: Context, f: File): Boolean = try {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) { false }
}
