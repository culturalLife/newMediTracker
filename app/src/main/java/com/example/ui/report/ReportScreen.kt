package com.example.ui.report

import android.content.Context
import android.content.Intent
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full-screen viewer for an exported HTML report.
 *
 * - Renders the report inside a WebView via [WebView.loadDataWithBaseURL] so no file
 *   needs to be written to disk just to display it.
 * - The "Share" action writes the HTML to the app's cache and exposes it through
 *   the configured FileProvider so any compatible app (Gmail, Drive, browser) can
 *   open or send it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    profileName: String,
    html: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Health Report",
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = profileName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("report_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { shareHtml(context, profileName, html) },
                        modifier = Modifier.testTag("report_share_button")
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = "Share report")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("report_webview"),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.apply {
                            javaScriptEnabled = false
                            cacheMode = WebSettings.LOAD_NO_CACHE
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            builtInZoomControls = true
                            displayZoomControls = false
                        }
                        loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                    }
                },
                update = { webView ->
                    webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                }
            )
        }
    }
}

/**
 * Writes the HTML to a file in the app's cache directory and fires Intent.ACTION_SEND
 * so the user can email, save to Drive, or print it. Uses FileProvider for safe URI exposure.
 */
private fun shareHtml(context: Context, profileName: String, html: String) {
    val safeName = profileName
        .lowercase(Locale.US)
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')
        .ifBlank { "profile" }
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val dir = File(context.cacheDir, "reports").apply { mkdirs() }
    val file = File(dir, "meditracker_report_${safeName}_$timestamp.html")
    FileWriter(file).use { it.write(html) }

    val authority = context.packageName + ".fileprovider"
    val uri = FileProvider.getUriForFile(context, authority, file)

    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/html"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "MediTracker Health Report — $profileName")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(sendIntent, "Share report"))
}
