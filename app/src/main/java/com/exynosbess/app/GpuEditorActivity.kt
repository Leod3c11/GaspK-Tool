package com.exynosbess.app

import com.exynosbess.app.partition.PartitionSelection
import com.exynosbess.app.partition.PartitionStateStore
import com.exynosbess.app.partition.PartitionSessionState

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import com.exynosbess.app.boot.ShellExecutor
import com.exynosbess.app.gpu.MaliGpuParser
import com.exynosbess.app.tools.ToolManager
import java.io.File
import java.math.BigInteger

class GpuEditorActivity : LocalizedActivity() {

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

    private var gpuDtsFiles =
        emptyList<File>()

    private var currentFileIndex = 0

    private var loadedNodeStart = -1
    private var loadedNodeEnd = -1

    private var loadingEditor = false

    private var validated = false

    private var searchMatches =
        emptyList<Int>()

    private var searchPosition = -1

    private var decimalMode = false

    private var workspacePath:
        String? = null

    private var sessionMode =
        false




    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        setContentView(
            R.layout.activity_gpu_editor
        )

        workspacePath =
            intent.getStringExtra(
                "workspace_path"
            )

        sessionMode =
            !workspacePath.isNullOrBlank()


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
                ) = Unit


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
                ) = Unit
            }
        )
    }

    private fun discoverGpuDtbs() {

        val workspace =
            workspacePath
                ?.takeIf {
                    it.isNotBlank()
                }
                ?.let {
                    File(it)
                }
                ?: File(
                    filesDir,
                    "workspace"
                )

        val dtbDir =
            File(
                workspace,
                "dtbs"
            )

        gpuDtsFiles =
            dtbDir.listFiles()
                ?.filter {
                    it.isFile &&
                        it.name.matches(
                            Regex("""dtb_\d+\.dts""")
                        )
                }
                ?.filter {
                    try {
                        MaliGpuParser.isSupported(
                            it.readText()
                        )
                    } catch (_: Exception) {
                        false
                    }
                }
                ?.sortedBy {
                    dtbIndex(it)
                }
                ?: emptyList()

        if (gpuDtsFiles.isEmpty()) {

            subtitle.text =
                getString(R.string.gpu_not_found)

            status.text =
                getString(R.string.gpu_node_none)

            setEditorEnabled(false)

            return
        }

        currentFileIndex = 0

        loadCurrentGpuNode()
    }


    private fun loadCurrentGpuNode() {

        val file =
            gpuDtsFiles[currentFileIndex]

        val dts =
            try {
                file.readText()
            } catch (
                e: Exception
            ) {
                status.text =
                    "✗ ${e.message}"

                return
            }

        val node =
            extractMaliNode(dts)

        if (node == null) {

            status.text =
                getString(R.string.gpu_node_not_found, file.name)

            return
        }

        loadedNodeStart =
            node.start

        loadedNodeEnd =
            node.end

        loadingEditor = true

        editor.setText(
            node.text
        )

        editor.setSelection(0)

        loadingEditor = false

        decimalMode = false

        numericBaseButton.text =
            "DEC"

        subtitle.text =
            "${file.name} • ${node.name}"

        status.text =
            getString(R.string.gpu_loaded)

        searchInput.setText("")
        searchStatus.text = ""

        updateNavigationButtons()
        invalidateBootState()
    }


    private fun switchDtb(
        direction: Int
    ) {

        if (gpuDtsFiles.size <= 1)
            return

        val target =
            currentFileIndex +
                direction

        if (
            target < 0 ||
            target >= gpuDtsFiles.size
        ) {
            return
        }

        if (hasUnsavedChanges()) {

            AlertDialog.Builder(this)
                .setTitle(
                    getString(R.string.gpu_unsaved_title)
                )
                .setMessage(
                    getString(R.string.gpu_unsaved_message)
                )
                .setNegativeButton(
                    getString(R.string.action_cancel),
                    null
                )
                .setPositiveButton(
                    "Descartar"
                ) {
                    _,
                    _ ->

                    currentFileIndex =
                        target

                    loadCurrentGpuNode()
                }
                .show()

        } else {

            currentFileIndex =
                target

            loadCurrentGpuNode()
        }
    }


    private fun hasUnsavedChanges():
        Boolean {

        if (gpuDtsFiles.isEmpty())
            return false

        val file =
            gpuDtsFiles[currentFileIndex]

        val dts =
            try {
                file.readText()
            } catch (_: Exception) {
                return false
            }

        val node =
            extractMaliNode(dts)
                ?: return false

        return editor.text
            .toString() !=
            node.text
    }


    private fun saveGpuNode() {

        ensureHexModeBeforePersistence()

        if (gpuDtsFiles.isEmpty())
            return

        val file =
            gpuDtsFiles[currentFileIndex]

        val original =
            try {
                file.readText()
            } catch (
                e: Exception
            ) {
                status.text =
                    "✗ ${e.message}"

                return
            }

        val originalNode =
            extractMaliNode(original)

        if (originalNode == null) {

            status.text =
                getString(R.string.gpu_original_node_missing)

            return
        }

        val edited =
            editor.text
                .toString()

        if (
            !edited.trimStart()
                .startsWith(
                    originalNode.name
                )
        ) {

            status.text =
                getString(R.string.gpu_root_changed)

            return
        }

        val editedNode =
            extractMaliNode(edited)

        if (editedNode == null) {

            status.text =
                getString(R.string.gpu_invalid_structure)

            return
        }

        val replacement =
            edited.substring(
                editedNode.start,
                editedNode.end
            )

        val modified =
            original.substring(
                0,
                originalNode.start
            ) +
                replacement +
                original.substring(
                    originalNode.end
                )

        try {

            file.writeText(
                modified
            )

            loadedNodeStart =
                originalNode.start

            loadedNodeEnd =
                originalNode.start +
                    replacement.length

            status.text =
                getString(R.string.gpu_saved, file.name)

            if (sessionMode) {

                PartitionSelection.get()
                    ?.let { selected ->

                        PartitionStateStore.save(
                            this,
                            selected,
                            PartitionSessionState.SAVED
                        )
                    }
            }

            invalidateBootState()

        } catch (
            e: Exception
        ) {

            status.text =
                "✗ ${e.message}"
        }
    }




    private fun validateCurrentDts() {

        ensureHexModeBeforePersistence()


        if (hasUnsavedChanges()) {

            status.text =
                getString(R.string.gpu_saving_before_validation)

            saveGpuNode()

            if (hasUnsavedChanges()) {
                return
            }
        }


        val file =
            gpuDtsFiles.getOrNull(
                currentFileIndex
            )
                ?: return


        setBusy(
            true
        )

        status.text =
            getString(R.string.gpu_validating, file.name)


        Thread {

            val tool =
                ToolManager.installDtc(
                    this
                )

            if (
                !tool.success ||
                tool.path == null
            ) {

                runOnUiThread {

                    status.text =
                        getString(R.string.gpu_dtc_unavailable, tool.message)

                    setBusy(
                        false
                    )
                }

                return@Thread
            }


            val validationDir =
                File(
                    filesDir,
                    "gpu_validation"
                )

            validationDir.mkdirs()


            val output =
                File(
                    validationDir,
                    "${file.nameWithoutExtension}.dtb"
                )


            if (output.exists()) {
                output.delete()
            }


            val result =
                ShellExecutor.root(
                    "'${tool.path}' " +
                        "-I dts " +
                        "-O dtb " +
                        "-o '${output.absolutePath}' " +
                        "'${file.absolutePath}'"
                )


            runOnUiThread {

                if (
                    result.success &&
                    output.exists() &&
                    output.length() > 0L
                ) {

                    validated =
                        true

                    status.text =
                        if (sessionMode) {
                            getString(R.string.gpu_valid_session)
                        } else {
                            getString(R.string.gpu_valid)
                        }


                    if (sessionMode) {

                        PartitionSelection.get()
                            ?.let { selected ->

                                PartitionStateStore.save(
                                    this,
                                    selected,
                                    PartitionSessionState.VALIDATED
                                )
                            }
                    }

                } else {

                    validated =
                        false

                    val error =
                        result.output
                            .lineSequence()
                            .firstOrNull {
                                it.isNotBlank()
                            }
                            ?.take(
                                220
                            )
                            ?: "Erro de compilação"

                    status.text =
                        getString(R.string.gpu_invalid_dts, error)
                }


                setBusy(
                    false
                )
            }

        }.start()
    }

    private fun startSearch() {

        val query =
            searchInput.text
                .toString()

        if (query.isBlank()) {

            searchStatus.text =
                getString(R.string.search_type_query)

            return
        }

        val content =
            editor.text
                .toString()

        searchMatches =
            Regex(
                Regex.escape(query),
                RegexOption.IGNORE_CASE
            )
                .findAll(content)
                .map {
                    it.range.first
                }
                .toList()

        if (searchMatches.isEmpty()) {

            searchPosition = -1

            searchStatus.text =
                getString(R.string.search_no_results)

            return
        }

        searchPosition = 0

        showSearchResult()
    }


    private fun nextSearchResult() {

        if (searchMatches.isEmpty()) {

            startSearch()

            return
        }

        searchPosition =
            (
                searchPosition + 1
            ) % searchMatches.size

        showSearchResult()
    }


    private fun showSearchResult() {

        if (
            searchPosition !in
            searchMatches.indices
        ) {
            return
        }

        val start =
            searchMatches[
                searchPosition
            ]

        val queryLength =
            searchInput.text
                .length

        val end =
            (
                start +
                    queryLength
            ).coerceAtMost(
                editor.text.length
            )

        editor.requestFocus()

        editor.setSelection(
            start,
            end
        )

        searchStatus.text =
            "${searchPosition + 1}/${searchMatches.size}"
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
                getString(R.string.view_decimal)
            } else {
                getString(R.string.view_hex)
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

        validated =
            false


        if (sessionMode) {

            PartitionSelection.get()
                ?.let { selected ->

                    PartitionStateStore.save(
                        this,
                        selected,
                        PartitionSessionState.DIRTY
                    )
                }
        }
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
