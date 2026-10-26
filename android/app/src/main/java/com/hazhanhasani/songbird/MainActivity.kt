package com.hazhanhasani.songbird

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import android.webkit.URLUtil
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.launch
import android.webkit.ValueCallback
import java.io.File

class MainActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("songbird_mobile", Context.MODE_PRIVATE) }
    private var activeBrowser: WebView? = null
    private var configuredOrigin: String? = null
    private var pendingFileSelection: ValueCallback<Array<Uri>>? = null
    private var pendingWebRtc: Pair<PermissionRequest, Array<String>>? = null
    private var pendingApk: File? = null

    private val pickFile = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val callback = pendingFileSelection
        pendingFileSelection = null
        val data = result.data
        val selected = if (result.resultCode == RESULT_OK) {
            val clip = data?.clipData
            when {
                clip != null -> Array(clip.itemCount) { clip.getItemAt(it).uri }
                data?.data != null -> arrayOf(data.data!!)
                else -> null
            }
        } else null
        callback?.onReceiveValue(selected)
    }

    private val mediaPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val pending = pendingWebRtc
        pendingWebRtc = null
        if (pending != null) {
            val (request, resources) = pending
            if (grants.values.all { it }) request.grant(resources) else request.deny()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(8, 24, 37)
        window.navigationBarColor = android.graphics.Color.rgb(8, 24, 37)
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightNavigationBars = false
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        setContent {
            val snackbars = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()
            var server by remember { mutableStateOf(prefs.getString("server_origin", null)) }
            var draft by rememberSaveable { mutableStateOf(server.orEmpty()) }
            var inputError by remember { mutableStateOf<String?>(null) }
            var pageError by remember { mutableStateOf<String?>(null) }
            var pageLoading by remember { mutableStateOf(true) }
            var canGoBack by remember { mutableStateOf(false) }
            var latestRelease by remember { mutableStateOf<AndroidRelease?>(null) }
            var showUpdatePrompt by remember { mutableStateOf(false) }
            var checkingUpdates by remember { mutableStateOf(false) }
            var downloadProgress by remember { mutableIntStateOf(-1) }

            fun checkUpdates(silent: Boolean = false) {
                if (checkingUpdates) return
                checkingUpdates = true
                scope.launch {
                    try {
                        val found = AndroidUpdates.findUpdate(BuildConfig.VERSION_NAME)
                        latestRelease = found
                        showUpdatePrompt = found != null
                        if (found == null && !silent) snackbars.showSnackbar("اپلیکیشن به‌روز است")
                    } catch (error: Exception) {
                        if (!silent) snackbars.showSnackbar(
                            "خطا در بررسی آپدیت: ${error.message ?: "اتصال شبکه"}"
                        )
                    } finally {
                        checkingUpdates = false
                    }
                }
            }

            LaunchedEffect(Unit) { checkUpdates(silent = true) }
            BackHandler(enabled = server != null && canGoBack) {
                activeBrowser?.goBack()
            }

            SongbirdTheme {
                Scaffold(
                    containerColor = Ink,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    snackbarHost = { SnackbarHost(snackbars) }
                ) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        if (server == null) {
                            WelcomeScreen(
                                address = draft,
                                onAddressChange = { draft = it; inputError = null },
                                error = inputError,
                                onConnect = {
                                    val normalized = ServerUrl.normalize(draft)
                                    if (normalized == null) {
                                        inputError = "آدرس معتبر HTTPS وارد کن؛ مثال: chat.example.com"
                                    } else {
                                        prefs.edit().putString("server_origin", normalized).apply()
                                        server = normalized
                                        configuredOrigin = normalized
                                        pageError = null
                                        canGoBack = false
                                    }
                                }
                            )
                        } else {
                            val origin = server!!
                            Column(
                                modifier = Modifier.fillMaxSize().navigationBarsPadding()
                            ) {
                                BrowserHeader(
                                    origin = origin,
                                    loading = pageLoading || checkingUpdates,
                                    onRefresh = { activeBrowser?.reload() },
                                    onUpdates = { checkUpdates() },
                                    onSettings = {
                                        draft = origin
                                        prefs.edit().remove("server_origin").apply()
                                        server = null
                                        configuredOrigin = null
                                        canGoBack = false
                                    }
                                )
                                Box(Modifier.weight(1f)) {
                                    key(origin) {
                                        DisposableEffect(origin) {
                                            onDispose {
                                                activeBrowser?.stopLoading()
                                                activeBrowser?.destroy()
                                                activeBrowser = null
                                            }
                                        }
                                        AndroidView(
                                            factory = { createBrowser(
                                                origin = origin,
                                                onLoading = { pageLoading = it },
                                                onError = { pageError = it },
                                                onHistory = { canGoBack = it }
                                            ) },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    if (pageError != null) {
                                        ConnectionError(
                                            message = pageError!!,
                                            onRetry = { pageError = null; activeBrowser?.reload() },
                                            onServerChange = {
                                                draft = origin
                                                prefs.edit().remove("server_origin").apply()
                                                server = null
                                                configuredOrigin = null
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                val release = latestRelease
                if (showUpdatePrompt && release != null) {
                    AlertDialog(
                        onDismissRequest = { if (downloadProgress < 0) showUpdatePrompt = false },
                        title = { Text("نسخه جدید Songbird") },
                        text = {
                            Column {
                                Text("نسخه ${release.version} آماده نصب است.")
                                if (downloadProgress >= 0) {
                                    Spacer(Modifier.height(12.dp))
                                    LinearProgressIndicator(
                                        progress = { downloadProgress / 100f },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Text("دریافت فایل: $downloadProgress٪")
                                } else {
                                    Text("منبع رسمی: github.com/hazhanhasani/Songbird")
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(
                                enabled = downloadProgress < 0,
                                onClick = {
                                    downloadProgress = 0
                                    scope.launch {
                                        try {
                                            val apk = AndroidUpdates.download(
                                                this@MainActivity, release
                                            ) { downloadProgress = it }
                                            showUpdatePrompt = false
                                            pendingApk = apk
                                            requestApkInstallation()
                                        } catch (error: Exception) {
                                            snackbars.showSnackbar(
                                                "دریافت آپدیت ناموفق بود: ${error.message}"
                                            )
                                        } finally {
                                            downloadProgress = -1
                                        }
                                    }
                                }
                            ) { Text("دانلود و نصب") }
                        },
                        dismissButton = {
                            TextButton(
                                enabled = downloadProgress < 0,
                                onClick = { showUpdatePrompt = false }
                            ) { Text("بعداً") }
                        }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (pendingApk != null && AndroidUpdates.mayInstall(this)) requestApkInstallation()
    }

    private fun requestApkInstallation() {
        val apk = pendingApk ?: return
        if (!AndroidUpdates.mayInstall(this)) {
            AndroidUpdates.requestInstallPermission(this)
            return
        }
        pendingApk = null
        AndroidUpdates.install(this, apk)
    }

    private fun createBrowser(
        origin: String,
        onLoading: (Boolean) -> Unit,
        onError: (String?) -> Unit,
        onHistory: (Boolean) -> Unit
    ): WebView {
        configuredOrigin = origin
        return WebView(this).apply {
            activeBrowser = this
            setBackgroundColor(android.graphics.Color.rgb(8, 24, 37))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.allowFileAccess = false
            settings.allowContentAccess = true
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    onLoading(true)
                    onError(null)
                    onHistory(view.canGoBack())
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    onLoading(false)
                    onHistory(view.canGoBack())
                }

                override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                    super.doUpdateVisitedHistory(view, url, isReload)
                    onHistory(view.canGoBack())
                }

                override fun onReceivedError(
                    view: WebView, request: WebResourceRequest, error: WebResourceError
                ) {
                    if (request.isForMainFrame) {
                        onLoading(false)
                        onError(error.description?.toString() ?: "خطای شبکه")
                    }
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView, request: WebResourceRequest
                ): Boolean {
                    val next = request.url
                    val trusted = Uri.parse(origin)
                    if (next.scheme == "https" && next.host.equals(trusted.host, true)
                        && next.port == trusted.port) return false
                    if (next.scheme in listOf("http", "https", "mailto", "tel")) {
                        try { startActivity(Intent(Intent.ACTION_VIEW, next)) } catch (_: Exception) {}
                    }
                    return true
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest) {
                    runOnUiThread { handleWebRtcPermission(request) }
                }

                override fun onPermissionRequestCanceled(request: PermissionRequest) {
                    if (pendingWebRtc?.first == request) pendingWebRtc = null
                }

                override fun onShowFileChooser(
                    webView: WebView,
                    filePathCallback: ValueCallback<Array<Uri>>,
                    fileChooserParams: FileChooserParams
                ): Boolean {
                    pendingFileSelection?.onReceiveValue(null)
                    pendingFileSelection = filePathCallback
                    val accepted = fileChooserParams.acceptTypes
                        .filter { it.contains('/') && !it.startsWith(".") }
                    val picker = Intent(Intent.ACTION_GET_CONTENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = if (accepted.size == 1) accepted.first() else "*/*"
                        if (accepted.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, accepted.toTypedArray())
                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE,
                            fileChooserParams.mode == FileChooserParams.MODE_OPEN_MULTIPLE)
                    }
                    return try {
                        pickFile.launch(Intent.createChooser(picker, "انتخاب فایل"))
                        true
                    } catch (_: Exception) {
                        pendingFileSelection = null
                        filePathCallback.onReceiveValue(null)
                        false
                    }
                }
            }

            setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mime, _ ->
                val source = Uri.parse(url)
                if (source.scheme != "https") return@DownloadListener
                val trusted = Uri.parse(origin)
                if (!source.host.equals(trusted.host, true) || source.port != trusted.port) {
                    try { startActivity(Intent(Intent.ACTION_VIEW, source)) } catch (_: Exception) {}
                    return@DownloadListener
                }
                try {
                    val request = DownloadManager.Request(source)
                        .setTitle("Songbird download")
                        .setMimeType(mime)
                        .setNotificationVisibility(
                            DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        .setDestinationInExternalFilesDir(
                            this@MainActivity, Environment.DIRECTORY_DOWNLOADS,
                            "songbird-${System.currentTimeMillis()}")
                    val cookie = CookieManager.getInstance().getCookie(url)
                    if (!cookie.isNullOrBlank()) request.addRequestHeader("Cookie", cookie)
                    if (!userAgent.isNullOrBlank()) request.addRequestHeader("User-Agent", userAgent)
                    (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
                } catch (_: Exception) {
                    try { startActivity(Intent(Intent.ACTION_VIEW, source)) } catch (_: Exception) {}
                }
            })
            loadUrl(origin)
        }
    }

    private fun handleWebRtcPermission(request: PermissionRequest) {
        val approved = configuredOrigin?.let(Uri::parse)
        val uri = request.origin
        if (approved == null || uri.scheme != "https" ||
            !uri.host.equals(approved.host, true) || uri.port != approved.port) {
            request.deny()
            return
        }
        val resources = request.resources
        if (resources.isEmpty() || resources.any {
                it != PermissionRequest.RESOURCE_AUDIO_CAPTURE &&
                it != PermissionRequest.RESOURCE_VIDEO_CAPTURE
            }) {
            request.deny()
            return
        }
        val permissions = buildList {
            if (resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
                add(Manifest.permission.RECORD_AUDIO)
            if (resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
                add(Manifest.permission.CAMERA)
        }
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            request.grant(resources)
        } else {
            pendingWebRtc?.first?.deny()
            pendingWebRtc = request to resources
            mediaPermissions.launch(missing.toTypedArray())
        }
    }

    override fun onDestroy() {
        pendingFileSelection?.onReceiveValue(null)
        pendingWebRtc?.first?.deny()
        activeBrowser?.destroy()
        activeBrowser = null
        super.onDestroy()
    }
}
