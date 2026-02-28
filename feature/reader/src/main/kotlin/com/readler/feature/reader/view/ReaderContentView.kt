package com.readler.feature.reader.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.readler.core.reader.ReaderDocument
import com.readler.core.reader.locator.ReaderLocatorCodec
import com.readler.feature.reader.prefs.ReaderPreferences
import com.readler.feature.reader.prefs.ReaderScrollMode
import com.readler.feature.reader.prefs.ReaderThemeMode
import java.io.File

class ReaderContentView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private var onProgressChanged: ((locator: String, percent: Float) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null
    private var lastRenderedDocument: ReaderDocument? = null
    private var lastRenderedPreferences: ReaderPreferences? = null
    private var lastRenderedError: String? = null

    private val messageText = TextView(context).apply {
        gravity = Gravity.CENTER
        textSize = 16f
        text = "Open a book to start reading"
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    }

    private val epubView = EpubReaderWebView(context)
    private val pdfView = PdfReaderPageView(context)

    init {
        addView(messageText)
        addView(epubView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(pdfView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        showMessage("Open a book to start reading")
    }

    fun setCallbacks(
        onProgressChanged: (locator: String, percent: Float) -> Unit,
        onError: (String) -> Unit
    ) {
        this.onProgressChanged = onProgressChanged
        this.onError = onError

        epubView.setCallbacks(onProgressChanged, onError)
        pdfView.setCallbacks(onProgressChanged, onError)
    }

    fun render(document: ReaderDocument?, preferences: ReaderPreferences, errorMessage: String?) {
        if (lastRenderedDocument == document && lastRenderedPreferences == preferences && lastRenderedError == errorMessage) {
            return
        }

        lastRenderedDocument = document
        lastRenderedPreferences = preferences
        lastRenderedError = errorMessage

        if (!errorMessage.isNullOrBlank()) {
            showMessage(errorMessage)
            return
        }

        when (document) {
            null -> showMessage("No document loaded")
            is ReaderDocument.Epub -> {
                messageText.visibility = View.GONE
                epubView.visibility = View.VISIBLE
                pdfView.visibility = View.GONE
                epubView.render(document, preferences)
            }
            is ReaderDocument.Pdf -> {
                messageText.visibility = View.GONE
                epubView.visibility = View.GONE
                pdfView.visibility = View.VISIBLE
                pdfView.render(document, preferences)
            }
        }
    }

    fun release() {
        epubView.release()
        pdfView.release()
    }

    private fun showMessage(message: String) {
        messageText.text = message
        messageText.visibility = View.VISIBLE
        epubView.visibility = View.GONE
        pdfView.visibility = View.GONE
    }
}

private class EpubReaderWebView(context: Context) : WebView(context) {
    private var onProgressChanged: ((locator: String, percent: Float) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null

    private var pendingRestorePercent: Float? = null
    private var lastLoadedHtmlContent: String? = null
    private var lastThemeMode: ReaderThemeMode? = null
    private var lastScrollMode: ReaderScrollMode? = null
    private var lastFontFamily: com.readler.feature.reader.prefs.ReaderFontFamily? = null
    private var lastLineSpacing: com.readler.feature.reader.prefs.ReaderLineSpacing? = null

    init {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        isVerticalScrollBarEnabled = true

        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                restoreScrollIfNeeded()
                reportProgress()
            }
        }

        setOnScrollChangeListener { _, _, _, _, _ ->
            reportProgress()
        }
    }

    fun setCallbacks(
        onProgressChanged: (locator: String, percent: Float) -> Unit,
        onError: (String) -> Unit
    ) {
        this.onProgressChanged = onProgressChanged
        this.onError = onError
    }

    fun render(document: ReaderDocument.Epub, preferences: ReaderPreferences) {
        // Font scale can be applied via textZoom without full HTML reload
        settings.textZoom = (preferences.fontScale * 100f).toInt()

        // Only do a full HTML reload if the content or layout-affecting preferences changed
        val needsFullReload = lastLoadedHtmlContent != document.htmlContent
            || lastThemeMode != preferences.themeMode
            || lastScrollMode != preferences.scrollMode
            || lastFontFamily != preferences.fontFamily
            || lastLineSpacing != preferences.lineSpacing

        if (needsFullReload) {
            lastLoadedHtmlContent = document.htmlContent
            lastThemeMode = preferences.themeMode
            lastScrollMode = preferences.scrollMode
            lastFontFamily = preferences.fontFamily
            lastLineSpacing = preferences.lineSpacing

            val themedHtml = wrapWithThemeAndMode(document.htmlContent, preferences)
            pendingRestorePercent = ReaderLocatorCodec.decodeEpubScrollPercent(document.initialLocator)
            loadDataWithBaseURL(null, themedHtml, "text/html", "utf-8", null)
        }
    }

    fun release() {
        stopLoading()
        clearHistory()
        clearCache(true)
        destroy()
    }

    private fun restoreScrollIfNeeded() {
        val percent = pendingRestorePercent ?: return
        pendingRestorePercent = null

        post {
            val contentPx = (contentHeight * scale).toInt()
            val availableScroll = (contentPx - height).coerceAtLeast(0)
            val y = (availableScroll * percent).toInt()
            scrollTo(0, y)
        }
    }

    private fun reportProgress() {
        val contentPx = (contentHeight * scale).toInt()
        val availableScroll = (contentPx - height).coerceAtLeast(1)
        val percent = (scrollY.toFloat() / availableScroll.toFloat()).coerceIn(0f, 1f)
        onProgressChanged?.invoke(ReaderLocatorCodec.encodeEpubScrollPercent(percent), percent)
    }

    private fun wrapWithThemeAndMode(rawHtml: String, preferences: ReaderPreferences): String {
        val (bg, fg) = when (preferences.themeMode) {
            ReaderThemeMode.DARK -> "#121212" to "#e6e6e6"
            ReaderThemeMode.LIGHT -> "#ffffff" to "#1a1a1a"
            ReaderThemeMode.SYSTEM -> "#ffffff" to "#1a1a1a"
        }

        val modeCss = when (preferences.scrollMode) {
            ReaderScrollMode.CONTINUOUS -> "body { overflow-y: auto; }"
            ReaderScrollMode.PAGED -> "body { column-width: 95vw; column-gap: 24px; overflow-x: auto; overflow-y: hidden; } section { break-inside: avoid; }"
        }

        return """
            <html>
            <head>
                <meta charset="utf-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                <style>
                    html, body {
                        margin: 0;
                        padding: 16px;
                        background: $bg;
                        color: $fg;
                        line-height: ${preferences.lineSpacing.value};
                        font-family: ${preferences.fontFamily.css};
                    }
                    img { max-width: 100%; height: auto; }
                    $modeCss
                </style>
            </head>
            <body>$rawHtml</body>
            </html>
        """.trimIndent()
    }
}

private class PdfReaderPageView(context: Context) : LinearLayout(context) {
    private val previousButton = Button(context).apply { text = "Previous" }
    private val nextButton = Button(context).apply { text = "Next" }
    private val pageLabel = TextView(context).apply { gravity = Gravity.CENTER }
    private val imageView = ImageView(context).apply {
        adjustViewBounds = true
        setBackgroundColor(Color.BLACK)
    }

    private var parcelFileDescriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private var currentPageIndex = 0
    private var currentFilePath: String? = null

    private var onProgressChanged: ((locator: String, percent: Float) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null

    init {
        orientation = VERTICAL

        val controls = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
            addView(previousButton)
            addView(pageLabel)
            addView(nextButton)
        }

        addView(controls, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(imageView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        previousButton.setOnClickListener { showPage(currentPageIndex - 1) }
        nextButton.setOnClickListener { showPage(currentPageIndex + 1) }
    }

    fun setCallbacks(
        onProgressChanged: (locator: String, percent: Float) -> Unit,
        onError: (String) -> Unit
    ) {
        this.onProgressChanged = onProgressChanged
        this.onError = onError
    }

    fun render(document: ReaderDocument.Pdf, preferences: ReaderPreferences) {
        applyTheme(preferences)
        openDocument(document.filePath)
        val pageCount = renderer?.pageCount ?: return
        val page = document.initialPageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        showPage(page)
    }

    fun release() {
        renderer?.close()
        renderer = null
        parcelFileDescriptor?.close()
        parcelFileDescriptor = null
    }

    private fun openDocument(filePath: String) {
        // Reload if different file or not yet opened
        if (filePath == currentFilePath && renderer != null && parcelFileDescriptor != null) {
            return
        }

        // Close previous document if any
        renderer?.close()
        renderer = null
        parcelFileDescriptor?.close()
        parcelFileDescriptor = null
        currentFilePath = filePath

        runCatching {
            val pfd = ParcelFileDescriptor.open(File(filePath), ParcelFileDescriptor.MODE_READ_ONLY)
            parcelFileDescriptor = pfd
            renderer = PdfRenderer(pfd)
        }.onFailure {
            onError?.invoke(it.message ?: "Failed to open PDF")
        }
    }

    private fun showPage(pageIndex: Int) {
        val pdfRenderer = renderer ?: return
        if (pdfRenderer.pageCount <= 0) {
            onError?.invoke("PDF has no pages")
            return
        }

        val clamped = pageIndex.coerceIn(0, pdfRenderer.pageCount - 1)
        currentPageIndex = clamped

        val page = pdfRenderer.openPage(clamped)
        val bitmap = Bitmap.createBitmap(
            (page.width * 1.5f).toInt().coerceAtLeast(1),
            (page.height * 1.5f).toInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888
        )
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()

        imageView.setImageBitmap(bitmap)
        pageLabel.text = "Page ${clamped + 1}/${pdfRenderer.pageCount}"
        previousButton.isEnabled = clamped > 0
        nextButton.isEnabled = clamped < pdfRenderer.pageCount - 1

        val percent = if (pdfRenderer.pageCount <= 1) 1f else clamped.toFloat() / (pdfRenderer.pageCount - 1).toFloat()
        onProgressChanged?.invoke(ReaderLocatorCodec.encodePdfPageIndex(clamped), percent)
    }

    private fun applyTheme(preferences: ReaderPreferences) {
        when (preferences.themeMode) {
            ReaderThemeMode.DARK -> {
                setBackgroundColor(Color.BLACK)
                pageLabel.setTextColor(Color.WHITE)
            }
            ReaderThemeMode.LIGHT,
            ReaderThemeMode.SYSTEM -> {
                setBackgroundColor(Color.WHITE)
                pageLabel.setTextColor(Color.BLACK)
            }
        }
    }
}
