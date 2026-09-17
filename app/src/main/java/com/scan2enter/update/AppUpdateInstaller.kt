package com.scan2enter.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class AppUpdateInstaller(
    private val context: Context,
    private val baseUrl: String = "http://192.168.1.30:5055"
) {

    fun downloadApk(): Result<File> = runCatching {
        val updateDir = File(context.cacheDir, "updates")
        if (!updateDir.exists() && !updateDir.mkdirs()) {
            error("Impossibile creare la cartella aggiornamenti")
        }

        val apkFile = File(updateDir, "Scan2Enter.apk")

        val connection =
            URL("${baseUrl.trimEnd('/')}/api/app-update/download")
                .openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 120_000
            connection.instanceFollowRedirects = true

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                error("Gateway HTTP $responseCode")
            }

            connection.inputStream.use { input ->
                apkFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            if (!apkFile.exists() || apkFile.length() <= 0L) {
                error("APK scaricato non valido")
            }

            apkFile
        } finally {
            connection.disconnect()
        }
    }

    fun canInstallPackages(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()
    }

    fun openUnknownAppsSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)
    }

    fun launchInstaller(apkFile: File) {
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(
                apkUri,
                "application/vnd.android.package-archive"
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)
    }
}