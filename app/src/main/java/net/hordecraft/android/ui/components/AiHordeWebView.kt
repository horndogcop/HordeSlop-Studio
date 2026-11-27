package net.hordeslop.studio.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import net.hordeslop.studio.domain.GenerationUrlParser
import net.hordeslop.studio.domain.ParsedImageMetadata
import net.hordeslop.studio.ui.theme.HordeColors

private const val AI_HORDE_HOME_URL = "https://aihorde.net"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AiHordeWebViewPane(
    onApplyParsedMetadata: (ParsedImageMetadata, String) -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf(AI_HORDE_HOME_URL) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var loadProgress by remember { mutableIntStateOf(0) }

    val parsedUrl = remember(currentUrl) { GenerationUrlParser.parseString(currentUrl) }
    val hasStudioParams = parsedUrl?.hasMetadata == true

    BackHandler(enabled = canGoBack) {
        webViewRef?.goBack()
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.apply {
                stopLoading()
                destroy()
            }
            webViewRef = null
        }
    }

    fun handleNavigation(view: WebView?, rawUrl: String): Boolean {
        if (rawUrl.isBlank()) return true
        if (rawUrl == "about:blank") return false
        val uri = runCatching { Uri.parse(rawUrl) }.getOrNull() ?: return true
        return when (uri.scheme?.lowercase()) {
            "https" -> false
            "http" -> {
                val upgraded = rawUrl.replaceFirst("http://", "https://", ignoreCase = true)
                view?.loadUrl(upgraded)
                true
            }
            else -> {
                GenerationUrlParser.forwardToBrowser(context, rawUrl)
                true
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(HordeColors.BarSurface)
                .border(1.dp, HordeColors.BorderSubtle)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            IconButton(
                onClick = { webViewRef?.goBack() },
                enabled = canGoBack,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Outlined.ChevronLeft,
                    contentDescription = "Back",
                    tint = if (canGoBack) HordeColors.TextPrimary else HordeColors.TextMuted.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(
                onClick = { webViewRef?.goForward() },
                enabled = canGoForward,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = "Forward",
                    tint = if (canGoForward) HordeColors.TextPrimary else HordeColors.TextMuted.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(
                onClick = {
                    if (isLoading) webViewRef?.stopLoading() else webViewRef?.reload()
                },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    if (isLoading) Icons.Outlined.Close else Icons.Outlined.Refresh,
                    contentDescription = if (isLoading) "Stop" else "Reload",
                    tint = HordeColors.TextSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
            IconButton(
                onClick = { webViewRef?.loadUrl(AI_HORDE_HOME_URL) },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Outlined.Home,
                    contentDescription = "aihorde.net Home",
                    tint = HordeColors.TextSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(HordeColors.InputSurface, RoundedCornerShape(8.dp))
                    .border(1.dp, HordeColors.BorderSubtle, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) {
                Text(
                    text = currentUrl.removePrefix("https://"),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = HordeColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (hasStudioParams && parsedUrl != null) {
                Button(
                    onClick = {
                        onApplyParsedMetadata(
                            parsedUrl.metadata,
                            "Applied settings from ${parsedUrl.metadata.generatorSource} page",
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = HordeColors.TealPrimary),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.padding(start = 4.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.Send,
                        contentDescription = null,
                        tint = Color(0xFF020617),
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = " Apply",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF020617),
                    )
                }
            }

            IconButton(
                onClick = {
                    if (!GenerationUrlParser.forwardToBrowser(context, currentUrl)) {
                        onShowMessage("No external browser available")
                    }
                },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.OpenInNew,
                    contentDescription = "Open in browser",
                    tint = HordeColors.TextSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        if (isLoading && loadProgress in 1..99) {
            LinearProgressIndicator(
                progress = { loadProgress / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = HordeColors.TealPrimary,
                trackColor = HordeColors.BarSurface,
            )
        }

        AndroidView(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    setBackgroundColor(android.graphics.Color.parseColor("#0F131A"))
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        builtInZoomControls = true
                        displayZoomControls = false
                        allowFileAccess = false
                        allowContentAccess = false
                        @Suppress("DEPRECATION")
                        allowFileAccessFromFileURLs = false
                        @Suppress("DEPRECATION")
                        allowUniversalAccessFromFileURLs = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        setSupportMultipleWindows(false)
                    }
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            loadProgress = newProgress
                            isLoading = newProgress < 100
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        @Suppress("OVERRIDE_DEPRECATION")
                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                            return handleNavigation(view, url.orEmpty())
                        }

                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val url = request?.url?.toString().orEmpty()
                            return handleNavigation(view, url)
                        }

                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            if (!url.isNullOrBlank()) currentUrl = url
                            canGoBack = view?.canGoBack() == true
                            canGoForward = view?.canGoForward() == true
                            isLoading = true
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            if (!url.isNullOrBlank()) currentUrl = url
                            canGoBack = view?.canGoBack() == true
                            canGoForward = view?.canGoForward() == true
                            isLoading = false
                        }

                        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                            if (!url.isNullOrBlank()) currentUrl = url
                            canGoBack = view?.canGoBack() == true
                            canGoForward = view?.canGoForward() == true
                        }
                    }
                    loadUrl(AI_HORDE_HOME_URL)
                    webViewRef = this
                }
            },
            update = { view ->
                webViewRef = view
            },
        )
    }
}
