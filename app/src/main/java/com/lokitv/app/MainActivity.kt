package com.lokitv.app

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var downloadId: Long = -1L
    private var downloadedFileName: String = ""

    private val onDownloadComplete = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (id == downloadId && downloadedFileName.isNotEmpty()) {
                val file = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), downloadedFileName)
                if (!file.exists() || file.length() < 1024L) {
                    Toast.makeText(
                        context,
                        "El archivo descargado no es un APK válido o está incompleto.",
                        Toast.LENGTH_LONG
                    ).show()
                    return
                }
                Toast.makeText(
                    context,
                    "Descarga completa. Abriendo instalador...",
                    Toast.LENGTH_SHORT
                ).show()
                installApk(downloadedFileName)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(onDownloadComplete, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(onDownloadComplete, filter)
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = "$userAgentString LokiTV-AndroidTV"
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                if (isApkUrl(url)) {
                    startApkDownload(url, webView.settings.userAgentString, "", "application/vnd.android.package-archive")
                    return true
                }
                return false
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (!url.isNullOrBlank() && isApkUrl(url)) {
                    startApkDownload(url, webView.settings.userAgentString, "", "application/vnd.android.package-archive")
                    return true
                }
                return false
            }
        }

        webView.webChromeClient = WebChromeClient()

        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            startApkDownload(url, userAgent, contentDisposition, mimeType)
        }

        webView.loadUrl("https://loki-tv-app.vercel.app")
    }

    private fun isApkUrl(url: String): Boolean {
        val clean = url.substringBefore("?").lowercase()
        return clean.endsWith(".apk")
    }

    private fun startApkDownload(
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimeType: String
    ) {
        try {
            var fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
            if (!fileName.endsWith(".apk", ignoreCase = true)) {
                fileName = "app_lokitv_${System.currentTimeMillis()}.apk"
            }
            downloadedFileName = fileName

            val downloadsDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val destFile = File(downloadsDir, fileName)
            if (destFile.exists()) {
                destFile.delete()
            }

            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setMimeType("application/vnd.android.package-archive")
                if (userAgent.isNotBlank()) {
                    addRequestHeader("User-Agent", userAgent)
                }
                setTitle(fileName)
                setDescription("Descargando aplicación desde LOKI TV...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalFilesDir(
                    this@MainActivity,
                    Environment.DIRECTORY_DOWNLOADS,
                    fileName
                )
            }

            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadId = dm.enqueue(request)
            Toast.makeText(this, "Descargando $fileName...", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Descarga directa no disponible (${e.message}). Abriendo enlace...",
                Toast.LENGTH_LONG
            ).show()
            openExternalUrl(url)
        }
    }

    private fun openExternalUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(this, "No se pudo abrir el enlace de descarga.", Toast.LENGTH_LONG).show()
        }
    }

    private fun installApk(fileName: String) {
        try {
            val file = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), fileName)
            if (!file.exists()) return

            val apkUri: Uri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                file
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(installIntent)
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Error al iniciar instalador: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(onDownloadComplete)
        } catch (_: Exception) {
        }
    }
}