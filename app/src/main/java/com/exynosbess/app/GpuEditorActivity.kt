package com.exynosbess.app

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import com.exynosbess.app.boot.BootFlashManager
import com.exynosbess.app.boot.BootRebuildManager
import com.exynosbess.app.boot.BootRoundTripVerifier
import com.exynosbess.app.boot.ShellExecutor
import com.exynosbess.app.gpu.MaliGpuParser
import com.exynosbess.app.tools.ToolManager
import java.io.File
import java.math.BigInteger

class GpuEditorActivity : Activity() {

    private lateinit var subtitle: TextView
    private lateinit var status: TextView

    private lateinit var editor: EditText

    private lateinit var searchInput: EditText
    private lateinit var searchStatus: TextView
    private lateinit var numericBaseButton: Button
    private lateinit var previousButton: Button
    private lateinit var nextButton: Button

    private lateinit var saveButton: Button
    private lateinit var validateButton: Button
    private lateinit var rebuildButton: Button
    private lateinit var verifyButton: Button
    private lateinit var flashButton: Button

    private var gpuDtsFiles =
        emptyList<File>()

    private var currentFileIndex = 0

    private var loadedNodeStart = -1
    private var loadedNodeEnd = -1

    private var loadingEditor = false

    private var validated = false
    private var bootGenerated = false
    private var bootVerified = false

    private var searchMatches =
        emptyList<Int>()

    private var searchPosition = -1

    private var decimalMode = false


    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_gpu_editor
        )

        bindViews()
        setupListeners()
        discoverGpuDtbs()
    }


    private fun bindViews() {

        subtitle =
            findViewById(
                R.id.gpuEditorSubtitle
            )

        status =
            findViewById(
                R.id.gpuEditorStatus
            )

        editor =
            findViewById(
                R.id.gpuNodeEditor
            )

        searchInput =
            findViewById(
                R.id.gpuSearchInput
            )

        searchStatus =
            findViewById(
                R.id.gpuSearchStatus
            )
numericBaseButton =
            findViewById(
                R.id.gpuNumericBaseButton
            )

        previousButton =
            findViewById(
                R.id.gpuPreviousDtbButton
            )

        nextButton =
            findViewById(
                R.id.gpuNextDtbButton
            )

        saveButton =
            findViewById(
                R.id.gpuSaveButton
            )

        validateButton =
            findViewById(
                R.id.gpuValidateButton
            )

        rebuildButton =
            findViewById(
                R.id.gpuRebuildButton
            )

        verifyButton =
            findViewById(
                R.id.gpuVerifyButton
            )

        flashButton =
            findViewById(
                R.id.gpuFlashButton
            )
    }


    private fun setupListeners() {
numericBaseButton.setOnClickListener {
            toggleNumericBase()
        }

        previousButton.setOnClickListener {
            switchDtb(-1)
        }

        nextButton.setOnClickListener {
            switchDtb(1)
        }

        saveButton.setOnClickListener {
            saveGpuNode()
        }

        validateButton.setOnClickListener {
            validateCurrentDts()
        }

        rebuildButton.setOnClickListener {
            rebuildBoot()
        }

        verifyButton.setOnClickListener {
            verifyBoot()
        }

        flashButton.setOnClickListener {
            confirmFlash()
        }

        findViewById<Button>(
            R.id.gpuSearchButton
        ).setOnClickListener {
            startSearch()
        }

        findViewById<Button>(
            R.id.gpuSearchNextButton
        ).setOnClickListener {
            nextSearchResult()
        }

        editor.addTextChangedListener(
            object : TextWatcher {

                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int
                ) {
                }

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int
                ) {
                    if (!loadingEditor) {
                        invalidateBootState()
                    }
                }

                override fun afterTextChanged(
                    s: Editable?
                ) {
                }
            }
        )
    }


    private fun toggleNumericBase() {

        val current =
            editor.text
                .toString()

        val converted =
            if (!decimalMode) {
                convertCellsHexToDecimal(
                    current
                )
            } else {
                convertCellsDecimalToHex(
                    current
                )
            }

        val selection =
            editor.selectionStart
                .coerceAtLeast(0)

        loadingEditor = true

        editor.setText(
            converted
        )

        editor.setSelection(
            selection.coerceAtMost(
                converted.length
            )
        )

        loadingEditor = false

        decimalMode =
            !decimalMode

        numericBaseButton.text =
            if (decimalMode) {
                "HEX"
            } else {
                "DEC"
            }

        status.text =
            if (decimalMode) {
                "Visualização decimal"
            } else {
                "Visualização hexadecimal"
            }
    }


    private fun ensureHexModeBeforePersistence() {

        if (!decimalMode)
            return

        val converted =
            convertCellsDecimalToHex(
                editor.text
                    .toString()
            )

        loadingEditor = true

        editor.setText(
            converted
        )

        loadingEditor = false

        decimalMode = false

        numericBaseButton.text =
            "DEC"
    }


    private fun convertCellsHexToDecimal(
        source: String
    ): String {

        val cellRegex =
            Regex(
                """<([^<>]*)>"""
            )

        val hexRegex =
            Regex(
                """0[xX]([0-9a-fA-F]+)"""
            )

        return cellRegex.replace(
            source
        ) {
            cellMatch ->

            val content =
                cellMatch.groupValues[1]

            val converted =
                hexRegex.replace(
                    content
                ) {
                    hexMatch ->

                    try {
                        BigInteger(
                            hexMatch.groupValues[1],
                            16
                        ).toString(10)

                    } catch (_: Exception) {
                        hexMatch.value
                    }
                }

            "<$converted>"
        }
    }


    private fun convertCellsDecimalToHex(
        source: String
    ): String {

        val cellRegex =
            Regex(
                """<([^<>]*)>"""
            )

        val decimalRegex =
            Regex(
                """(?<![A-Za-z0-9_])([0-9]+)(?![A-Za-z0-9_])"""
            )

        return cellRegex.replace(
            source
        ) {
            cellMatch ->

            val content =
                cellMatch.groupValues[1]

            val converted =
                decimalRegex.replace(
                    content
                ) {
                    decimalMatch ->

                    try {
                        val value =
                            BigInteger(
                                decimalMatch.groupValues[1],
                                10
                            )

                        "0x" +
                            value.toString(16)

                    } catch (_: Exception) {
                        decimalMatch.value
                    }
                }

            "<$converted>"
        }
    }


    private fun invalidateBootState() {

        validated = false
        bootGenerated = false
        bootVerified = false

        rebuildButton.isEnabled =
            false

        verifyButton.isEnabled =
            false

        flashButton.isEnabled =
            false
    }


    private fun updateNavigationButtons() {

        previousButton.isEnabled =
            currentFileIndex > 0

        nextButton.isEnabled =
            currentFileIndex <
                gpuDtsFiles.lastIndex
    }


    private fun setBusy(
        busy: Boolean
    ) {

        saveButton.isEnabled =
            !busy

        validateButton.isEnabled =
            !busy

        previousButton.isEnabled =
            !busy &&
                currentFileIndex > 0

        nextButton.isEnabled =
            !busy &&
                currentFileIndex <
                gpuDtsFiles.lastIndex
    }


    private fun setEditorEnabled(
        enabled: Boolean
    ) {

        editor.isEnabled =
            enabled

        saveButton.isEnabled =
            enabled

        validateButton.isEnabled =
            enabled

        previousButton.isEnabled =
            false

        nextButton.isEnabled =
            false
    }


    private data class GpuNode(
        val name: String,
        val start: Int,
        val end: Int,
        val text: String
    )


    private fun extractMaliNode(
        source: String
    ): GpuNode? {

        val match =
            Regex(
                """(?i)\bmali@[0-9a-f]+\s*\{"""
            ).find(source)
                ?: return null

        val openBrace =
            source.indexOf(
                '{',
                match.range.first
            )

        if (openBrace < 0)
            return null

        var depth = 0
        var index = openBrace

        var inString = false
        var escaped = false

        while (
            index < source.length
        ) {

            val c =
                source[index]

            if (inString) {

                if (escaped) {

                    escaped = false

                } else if (
                    c == '\\'
                ) {

                    escaped = true

                } else if (
                    c == '"'
                ) {

                    inString = false
                }

            } else {

                when (c) {

                    '"' -> {
                        inString = true
                    }

                    '{' -> {
                        depth++
                    }

                    '}' -> {

                        depth--

                        if (depth == 0) {

                            var end =
                                index + 1

                            while (
                                end <
                                source.length &&
                                source[end]
                                    .isWhitespace()
                            ) {
                                end++
                            }

                            if (
                                end <
                                source.length &&
                                source[end] == ';'
                            ) {
                                end++
                            }

                            val nodeName =
                                match.value
                                    .substringBefore(
                                        '{'
                                    )
                                    .trim()

                            return GpuNode(
                                name =
                                    nodeName,

                                start =
                                    match.range.first,

                                end =
                                    end,

                                text =
                                    source.substring(
                                        match.range.first,
                                        end
                                    )
                            )
                        }
                    }
                }
            }

            index++
        }

        return null
    }


    private fun dtbIndex(
        file: File
    ): Int {

        return Regex(
            """dtb_(\d+)\.dts"""
        )
            .find(
                file.name
            )
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: 0
    }


    private fun dpToPx(
        dp: Int
    ): Int {

        return (
            dp *
                resources.displayMetrics.density
            ).toInt()
    }

}
