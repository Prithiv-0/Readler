package com.readler.app

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.readler.feature.library.LibraryViewModel
import com.readler.feature.reader.ReaderViewModel
import com.readler.feature.reader.prefs.ReaderScrollMode
import com.readler.feature.reader.prefs.ReaderThemeMode
import com.readler.feature.reader.view.ReaderContentView
import com.readler.app.telemetry.PerfTimer
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val libraryViewModel by viewModels<LibraryViewModel> {
        LibraryViewModelFactory()
    }

    private val readerViewModel by viewModels<ReaderViewModel> {
        ReaderViewModelFactory()
    }

    private var loadedBookId: String? = null
    private var latestBookId: String? = null
    private var startupLogged = false
    private var pendingBookLoadId: String? = null
    private var pendingBookLoadTimer: PerfTimer? = null
    private var lastPromptedQueueRequestId: String? = null
    private val startupTimer = PerfTimer()
    private val telemetryLogger by lazy { AppContainer.telemetryLogger }

    private val importBookLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            telemetryLogger.logEvent("import_cancelled")
            return@registerForActivityResult
        }

        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        telemetryLogger.logEvent("import_selected", mapOf("uri" to uri.toString()))
        libraryViewModel.importBookFromUri(uri.toString())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── Theme colors (will be updated dynamically) ──
        var currentBg = Color.parseColor("#121212")
        var currentFg = Color.parseColor("#e6e6e6")
        var currentAccent = Color.parseColor("#2196F3")
        var currentSurface = Color.parseColor("#1e1e1e")

        fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

        fun styleButton(btn: Button, small: Boolean = false) {
            btn.isAllCaps = false
            btn.textSize = if (small) 12f else 13f
            btn.setPadding(dp(12), dp(6), dp(12), dp(6))
            btn.minimumWidth = 0
            btn.minimumHeight = 0
            btn.setBackgroundColor(currentSurface)
            btn.setTextColor(currentFg)
        }

        fun styleEditText(et: EditText) {
            et.textSize = 13f
            et.setTextColor(currentFg)
            et.setHintTextColor(Color.argb(120, Color.red(currentFg), Color.green(currentFg), Color.blue(currentFg)))
            et.setBackgroundColor(currentSurface)
            et.setPadding(dp(8), dp(6), dp(8), dp(6))
        }

        // ── Root layout ──
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(currentBg)
        }

        // ── Status bar ──
        val statusText = TextView(this).apply {
            text = "Loading library..."
            textSize = 13f
            setTextColor(currentFg)
            setPadding(dp(16), dp(12), dp(16), dp(8))
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }

        // ── Top toolbar: row 1 — Import, Resume ──
        val toolbarRow1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(4), dp(12), dp(2))
            gravity = Gravity.CENTER_VERTICAL
        }
        val importButton = Button(this).apply { text = "Import" }
        val resumeButton = Button(this).apply { text = "Resume" }
        styleButton(importButton)
        styleButton(resumeButton)
        val spacer1 = View(this)
        toolbarRow1.addView(importButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(6) })
        toolbarRow1.addView(resumeButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(6) })
        toolbarRow1.addView(spacer1, LinearLayout.LayoutParams(0, 1, 1f))

        // ── Top toolbar: row 2 — Theme, Scroll, AI toggle, Tools toggle ──
        val toolbarRow2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(2), dp(12), dp(4))
            gravity = Gravity.CENTER_VERTICAL
        }
        val themeButton = Button(this).apply { text = "\u263C Light" }  // ☼
        val scrollButton = Button(this).apply { text = "\u25A4 Scroll" }
        val aiToggleButton = Button(this).apply { text = "AI Off" }
        val toolsToggleButton = Button(this).apply { text = "\u2699 Tools" }  // ⚙
        styleButton(themeButton, small = true)
        styleButton(scrollButton, small = true)
        styleButton(aiToggleButton, small = true)
        styleButton(toolsToggleButton, small = true)
        for (btn in listOf(themeButton, scrollButton, aiToggleButton, toolsToggleButton)) {
            toolbarRow2.addView(btn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(3); marginEnd = dp(3)
            })
        }

        // ── Reader content (takes all remaining space) ──
        val readerContent = ReaderContentView(this)

        // ── Collapsible tools panel ──
        val toolsPanel = ScrollView(this).apply {
            visibility = View.GONE
            setBackgroundColor(currentSurface)
            setPadding(0, dp(4), 0, dp(4))
        }
        val toolsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(4), dp(12), dp(4))
        }
        toolsPanel.addView(toolsContainer)

        // ── Search row ──
        val searchLabel = TextView(this).apply {
            text = "Search"
            textSize = 12f
            setTextColor(currentAccent)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(4), 0, dp(2))
        }
        val searchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val searchInput = EditText(this).apply {
            hint = "Search in book..."
            inputType = InputType.TYPE_CLASS_TEXT
            isSingleLine = true
        }
        val searchButton = Button(this).apply { text = "Go" }
        styleEditText(searchInput)
        styleButton(searchButton, small = true)
        searchRow.addView(searchInput, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) })
        searchRow.addView(searchButton)

        // ── Font scale ──
        val fontLabel = TextView(this).apply {
            text = "Font size"
            textSize = 12f
            setTextColor(currentAccent)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(8), 0, dp(2))
        }
        val fontScaleSeek = SeekBar(this).apply {
            max = 125
            progress = 25
        }

        // ── Font family ──
        val fontFamilyLabel = TextView(this).apply {
            text = "Font"
            textSize = 12f
            setTextColor(currentAccent)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(8), 0, dp(2))
        }
        val fontFamilyRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val fontFamilyValues = com.readler.feature.reader.prefs.ReaderFontFamily.entries
        val fontFamilyButtons = fontFamilyValues.map { ff ->
            Button(this).apply {
                text = ff.label
                tag = ff
            }
        }
        for (btn in fontFamilyButtons) {
            styleButton(btn, small = true)
            fontFamilyRow.addView(btn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(2); marginEnd = dp(2)
            })
        }

        // ── Line spacing ──
        val lineSpacingLabel = TextView(this).apply {
            text = "Line spacing"
            textSize = 12f
            setTextColor(currentAccent)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(8), 0, dp(2))
        }
        val lineSpacingRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val lineSpacingValues = com.readler.feature.reader.prefs.ReaderLineSpacing.entries
        val lineSpacingButtons = lineSpacingValues.map { ls ->
            Button(this).apply {
                text = ls.label
                tag = ls
            }
        }
        for (btn in lineSpacingButtons) {
            styleButton(btn, small = true)
            lineSpacingRow.addView(btn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(2); marginEnd = dp(2)
            })
        }

        // ── AI section header ──
        val aiLabel = TextView(this).apply {
            text = "AI Assistant"
            textSize = 12f
            setTextColor(currentAccent)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, dp(8), 0, dp(2))
        }

        // AI Key row
        val aiKeyRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(2))
        }
        val aiKeyInput = EditText(this).apply {
            hint = "Gemini API key"
            inputType = InputType.TYPE_CLASS_TEXT
            isSingleLine = true
        }
        val aiSaveKeyButton = Button(this).apply { text = "Save" }
        styleEditText(aiKeyInput)
        styleButton(aiSaveKeyButton, small = true)
        aiKeyRow.addView(aiKeyInput, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) })
        aiKeyRow.addView(aiSaveKeyButton)

        // AI Question row
        val aiQuestionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(2))
        }
        val aiQuestionInput = EditText(this).apply {
            hint = "Ask about this book..."
            inputType = InputType.TYPE_CLASS_TEXT
            isSingleLine = true
        }
        val aiAskButton = Button(this).apply { text = "Ask" }
        val aiSimilarButton = Button(this).apply { text = "Similar" }
        styleEditText(aiQuestionInput)
        styleButton(aiAskButton, small = true)
        styleButton(aiSimilarButton, small = true)
        aiQuestionRow.addView(aiQuestionInput, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) })
        aiQuestionRow.addView(aiAskButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(4) })
        aiQuestionRow.addView(aiSimilarButton)

        // AI Selection + Explain/Translate
        val aiSelectionInput = EditText(this).apply {
            hint = "Paste text to explain/translate"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 2
            maxLines = 3
        }
        styleEditText(aiSelectionInput)

        val aiActionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(2))
        }
        val aiExplainButton = Button(this).apply { text = "Explain" }
        val aiTranslateButton = Button(this).apply { text = "Translate" }
        val aiLanguageInput = EditText(this).apply {
            hint = "Language"
            inputType = InputType.TYPE_CLASS_TEXT
            isSingleLine = true
            setText("English")
        }
        styleButton(aiExplainButton, small = true)
        styleButton(aiTranslateButton, small = true)
        styleEditText(aiLanguageInput)
        aiActionRow.addView(aiExplainButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(4) })
        aiActionRow.addView(aiTranslateButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(6) })
        aiActionRow.addView(aiLanguageInput, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

        // AI Summary
        val aiSummaryRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(2), 0, dp(2))
        }
        val aiSectionInput = EditText(this).apply {
            hint = "Section text to summarize"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 2
            maxLines = 3
        }
        val aiSummaryButton = Button(this).apply { text = "Summarize" }
        styleEditText(aiSectionInput)
        styleButton(aiSummaryButton, small = true)
        aiSummaryRow.addView(aiSectionInput, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) })
        aiSummaryRow.addView(aiSummaryButton)

        // AI Response
        val aiResponseText = TextView(this).apply {
            text = ""
            textSize = 12f
            setTextColor(currentFg)
            setPadding(0, dp(4), 0, dp(4))
            maxLines = 6
            ellipsize = TextUtils.TruncateAt.END
        }

        // ── Assemble tools panel ──
        val wrapParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        toolsContainer.addView(searchLabel, wrapParams)
        toolsContainer.addView(searchRow, wrapParams)
        toolsContainer.addView(fontLabel, wrapParams)
        toolsContainer.addView(fontScaleSeek, wrapParams)
        toolsContainer.addView(fontFamilyLabel, wrapParams)
        toolsContainer.addView(fontFamilyRow, wrapParams)
        toolsContainer.addView(lineSpacingLabel, wrapParams)
        toolsContainer.addView(lineSpacingRow, wrapParams)
        toolsContainer.addView(aiLabel, wrapParams)
        toolsContainer.addView(aiKeyRow, wrapParams)
        toolsContainer.addView(aiQuestionRow, wrapParams)
        toolsContainer.addView(aiSelectionInput, wrapParams)
        toolsContainer.addView(aiActionRow, wrapParams)
        toolsContainer.addView(aiSummaryRow, wrapParams)
        toolsContainer.addView(aiResponseText, wrapParams)

        // ── Assemble root ──
        root.addView(statusText, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(toolbarRow1, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(toolbarRow2, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(readerContent, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(toolsPanel, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            height = (resources.displayMetrics.heightPixels * 0.4f).toInt()  // Max 40% of screen
        })

        setContentView(root)

        // ── Theme apply helper ──
        fun applyThemeColors(mode: ReaderThemeMode) {
            when (mode) {
                ReaderThemeMode.DARK -> {
                    currentBg = Color.parseColor("#121212")
                    currentFg = Color.parseColor("#e6e6e6")
                    currentSurface = Color.parseColor("#1e1e1e")
                    currentAccent = Color.parseColor("#64B5F6")
                }
                ReaderThemeMode.LIGHT -> {
                    currentBg = Color.parseColor("#fafafa")
                    currentFg = Color.parseColor("#1a1a1a")
                    currentSurface = Color.parseColor("#f0f0f0")
                    currentAccent = Color.parseColor("#1565C0")
                }
                ReaderThemeMode.SYSTEM -> {
                    currentBg = Color.parseColor("#fafafa")
                    currentFg = Color.parseColor("#1a1a1a")
                    currentSurface = Color.parseColor("#f0f0f0")
                    currentAccent = Color.parseColor("#1565C0")
                }
            }
            root.setBackgroundColor(currentBg)
            statusText.setTextColor(currentFg)
            toolsPanel.setBackgroundColor(currentSurface)

            val allButtons = listOf(importButton, resumeButton, themeButton, scrollButton, aiToggleButton, toolsToggleButton,
                searchButton, aiSaveKeyButton, aiAskButton, aiSimilarButton, aiExplainButton, aiTranslateButton, aiSummaryButton) +
                fontFamilyButtons + lineSpacingButtons
            for (btn in allButtons) {
                btn.setBackgroundColor(currentSurface)
                btn.setTextColor(currentFg)
            }

            val allEditTexts = listOf(searchInput, aiKeyInput, aiQuestionInput, aiSelectionInput, aiLanguageInput, aiSectionInput)
            val hintColor = Color.argb(120, Color.red(currentFg), Color.green(currentFg), Color.blue(currentFg))
            for (et in allEditTexts) {
                et.setTextColor(currentFg)
                et.setHintTextColor(hintColor)
                et.setBackgroundColor(currentSurface)
            }

            val allLabels = listOf(searchLabel, fontLabel, fontFamilyLabel, lineSpacingLabel, aiLabel)
            for (lbl in allLabels) { lbl.setTextColor(currentAccent) }
            aiResponseText.setTextColor(currentFg)

            themeButton.text = when (mode) {
                ReaderThemeMode.LIGHT -> "\u263E Dark" // ☾ switch to dark
                ReaderThemeMode.DARK -> "\u263C Light"  // ☼ switch to light
                ReaderThemeMode.SYSTEM -> "\u263C Light"
            }
        }

        // ── Callbacks ──
        readerContent.setCallbacks(
            onProgressChanged = { locator, percent ->
                readerViewModel.onReadingPositionChanged(locator, percent)
            },
            onError = { message ->
                statusText.text = message
            }
        )

        importButton.setOnClickListener {
            importBookLauncher.launch(arrayOf("application/epub+zip", "application/pdf"))
            statusText.text = "Select an EPUB or PDF to import..."
        }

        resumeButton.setOnClickListener {
            val targetBookId = latestBookId
            if (targetBookId == null) {
                statusText.text = "No recent book to resume"
            } else {
                loadedBookId = targetBookId
                pendingBookLoadId = targetBookId
                pendingBookLoadTimer = PerfTimer()
                telemetryLogger.logEvent("resume_last_tapped", mapOf("bookId" to targetBookId))
                readerViewModel.loadBook(targetBookId)
            }
        }

        var currentThemeMode = ReaderThemeMode.DARK
        themeButton.setOnClickListener {
            val newMode = if (currentThemeMode == ReaderThemeMode.DARK) ReaderThemeMode.LIGHT else ReaderThemeMode.DARK
            readerViewModel.onThemeModeChanged(newMode)
        }

        var currentScrollMode = ReaderScrollMode.CONTINUOUS
        scrollButton.setOnClickListener {
            val newMode = if (currentScrollMode == ReaderScrollMode.CONTINUOUS) ReaderScrollMode.PAGED else ReaderScrollMode.CONTINUOUS
            readerViewModel.onScrollModeChanged(newMode)
        }

        toolsToggleButton.setOnClickListener {
            if (toolsPanel.visibility == View.VISIBLE) {
                toolsPanel.visibility = View.GONE
                toolsToggleButton.text = "\u2699 Tools"
            } else {
                toolsPanel.visibility = View.VISIBLE
                toolsToggleButton.text = "\u2715 Close"  // ✕
            }
        }

        fontScaleSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val scale = 0.75f + (progress / 100f)
                readerViewModel.onFontScaleChanged(scale)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })

        for (btn in fontFamilyButtons) {
            btn.setOnClickListener {
                val ff = btn.tag as com.readler.feature.reader.prefs.ReaderFontFamily
                readerViewModel.onFontFamilyChanged(ff)
            }
        }

        for (btn in lineSpacingButtons) {
            btn.setOnClickListener {
                val ls = btn.tag as com.readler.feature.reader.prefs.ReaderLineSpacing
                readerViewModel.onLineSpacingChanged(ls)
            }
        }

        searchButton.setOnClickListener {
            readerViewModel.searchInCurrentBook(searchInput.text.toString())
        }

        aiToggleButton.setOnClickListener {
            val currentEnabled = readerViewModel.uiState.value.aiEnabled
            readerViewModel.onAiEnabledChanged(!currentEnabled)
        }

        aiSaveKeyButton.setOnClickListener {
            readerViewModel.saveAiApiKey(aiKeyInput.text.toString())
            aiKeyInput.setText("")
            statusText.text = "API key saved"
        }

        aiAskButton.setOnClickListener {
            readerViewModel.askBookQuestion(aiQuestionInput.text.toString())
        }

        aiSimilarButton.setOnClickListener {
            readerViewModel.askSimilarBooks()
        }

        aiExplainButton.setOnClickListener {
            readerViewModel.explainSelectedText(aiSelectionInput.text.toString())
        }

        aiTranslateButton.setOnClickListener {
            readerViewModel.translateSelectedText(
                aiSelectionInput.text.toString(),
                aiLanguageInput.text.toString()
            )
        }

        aiSummaryButton.setOnClickListener {
            readerViewModel.summarizeCurrentSection(aiSectionInput.text.toString())
        }

        // ── State collection ──
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    libraryViewModel.uiState.collect { state ->
                        val first = state.books.firstOrNull()
                        latestBookId = first?.id

                        if (state.errorMessage != null) {
                            telemetryLogger.logEvent("library_error", mapOf("message" to state.errorMessage.orEmpty()))
                            statusText.text = state.errorMessage
                        } else if (first == null && state.isLoading) {
                            statusText.text = "Library is loading..."
                        } else if (first == null) {
                            statusText.text = "No books yet \u2014 tap Import to add an EPUB or PDF"
                        } else if (loadedBookId != first.id) {
                            loadedBookId = first.id
                            pendingBookLoadId = first.id
                            pendingBookLoadTimer = PerfTimer()
                            telemetryLogger.logEvent("auto_open_first_book", mapOf("bookId" to first.id))
                            statusText.text = "Opening ${first.title}"
                            readerViewModel.loadBook(first.id)
                        }
                    }
                }

                launch {
                    readerViewModel.uiState.collect { state ->
                        // Update theme for the entire activity chrome
                        val themeMode = state.preferences.themeMode
                        if (themeMode != currentThemeMode) {
                            currentThemeMode = themeMode
                            applyThemeColors(themeMode)
                        }

                        // Update scroll mode button text
                        currentScrollMode = state.preferences.scrollMode
                        scrollButton.text = when (currentScrollMode) {
                            ReaderScrollMode.CONTINUOUS -> "\u25A4 Scroll"
                            ReaderScrollMode.PAGED -> "\u25A6 Paged"
                        }

                        if (state.isLoading) {
                            statusText.text = "Opening book..."
                        } else if (state.errorMessage != null) {
                            telemetryLogger.logEvent("reader_error", mapOf("message" to state.errorMessage.orEmpty()))
                            statusText.text = state.errorMessage
                        } else {
                            val currentBook = state.currentBook
                            if (currentBook != null) {
                                val searchSuffix = if (state.searchQuery.isNotBlank()) {
                                    " | ${state.searchResults.size} match(es)"
                                } else ""
                                statusText.text = "${currentBook.title}$searchSuffix"

                                if (!startupLogged) {
                                    startupLogged = true
                                    telemetryLogger.logMetric(
                                        name = "startup_to_first_reader_ms",
                                        valueMs = startupTimer.elapsedMs(),
                                        attributes = mapOf("bookId" to currentBook.id)
                                    )
                                }

                                if (pendingBookLoadId == currentBook.id && !state.isLoading) {
                                    val elapsed = pendingBookLoadTimer?.elapsedMs()
                                    if (elapsed != null) {
                                        telemetryLogger.logMetric(
                                            name = "book_open_ms",
                                            valueMs = elapsed,
                                            attributes = mapOf("bookId" to currentBook.id)
                                        )
                                    }
                                    pendingBookLoadId = null
                                    pendingBookLoadTimer = null
                                }
                            }
                        }

                        aiToggleButton.text = if (state.aiEnabled) "AI On" else "AI Off"
                        val capabilitySuffix = when {
                            !state.aiCapability.hasApiKey -> " (no key)"
                            !state.aiCapability.hasNetwork -> " (offline)"
                            state.isAiLoading -> " ..."
                            else -> ""
                        }
                        aiResponseText.text = if (state.aiResponse.isNullOrBlank()) {
                            ""
                        } else {
                            "${state.aiResponse}$capabilitySuffix"
                        }

                        val pending = state.pendingQueueRequest
                        if (pending != null && pending.requestId != lastPromptedQueueRequestId) {
                            lastPromptedQueueRequestId = pending.requestId
                            AlertDialog.Builder(this@MainActivity)
                                .setTitle("No network")
                                .setMessage("Queue this AI request for when connectivity returns?")
                                .setNegativeButton("No") { _, _ ->
                                    readerViewModel.confirmQueuePending(false)
                                }
                                .setPositiveButton("Queue") { _, _ ->
                                    readerViewModel.confirmQueuePending(true)
                                }
                                .setOnDismissListener {
                                    if (readerViewModel.uiState.value.pendingQueueRequest != null) {
                                        readerViewModel.confirmQueuePending(false)
                                    }
                                }
                                .show()
                        }

                        fontScaleSeek.progress = ((state.preferences.fontScale - 0.75f) * 100f).toInt()
                        readerContent.render(
                            document = state.currentDocument,
                            preferences = state.preferences,
                            errorMessage = state.errorMessage
                        )
                    }
                }
            }
        }

        // Apply initial theme
        applyThemeColors(currentThemeMode)
    }

    override fun onDestroy() {
        telemetryLogger.logEvent("main_activity_destroy")
        super.onDestroy()
        val rootView = findViewById<LinearLayout>(android.R.id.content)
        val readerContent = findReaderContentView(rootView)
        readerContent?.release()
    }

    private fun findReaderContentView(view: android.view.View): ReaderContentView? {
        if (view is ReaderContentView) {
            return view
        }

        if (view is android.view.ViewGroup) {
            for (index in 0 until view.childCount) {
                val found = findReaderContentView(view.getChildAt(index))
                if (found != null) return found
            }
        }

        return null
    }
}
