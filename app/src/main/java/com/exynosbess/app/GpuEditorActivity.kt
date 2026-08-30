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


    private fun discoverGpuDtbs() {

        val dtbDir =
            File(
                filesDir,
                "workspace/dtbs"
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
                "GPU Mali não encontrada"

            status.text =
                "Nenhum nó mali@... encontrado"

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
                "✗ Nó Mali não encontrado em ${file.name}"

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
            "✓ Bloco completo da GPU carregado"

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
                    "Alterações não salvas"
                )
                .setMessage(
                    "Deseja trocar de DTB e descartar as alterações atuais?"
                )
                .setNegativeButton(
                    "Cancelar",
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
                "✗ Nó Mali original não encontrado"

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
                "✗ O nó raiz da GPU foi alterado"

            return
        }

        val editedNode =
            extractMaliNode(edited)

        if (editedNode == null) {

            status.text =
                "✗ Estrutura do nó GPU inválida"

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
                "✓ GPU salva em ${file.name}"

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
                "Salvando antes da validação..."

            saveGpuNode()

            if (hasUnsavedChanges())
                return
        }

        val file =
            gpuDtsFiles.getOrNull(
                currentFileIndex
            ) ?: return

        setBusy(true)

        status.text =
            "Validando ${file.name}..."

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
                        "✗ dtc indisponível: ${tool.message}"

                    setBusy(false)
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
                        "-I dts -O dtb " +
                        "-o '${output.absolutePath}' " +
                        "'${file.absolutePath}'"
                )

            runOnUiThread {

                if (
                    result.success &&
                    output.exists() &&
                    output.length() > 0L
                ) {

                    validated = true

                    rebuildButton.isEnabled =
                        true

                    status.text =
                        "✓ DTS válido"

                } else {

                    validated = false

                    rebuildButton.isEnabled =
                        false

                    val error =
                        result.output
                            .lineSequence()
                            .firstOrNull {
                                it.isNotBlank()
                            }
                            ?.take(180)
                            ?: "Erro de compilação"

                    status.text =
                        "✗ DTS inválido: $error"
                }

                setBusy(false)
            }

        }.start()
    }


    private fun rebuildBoot() {

        if (!validated) {

            status.text =
                "Valide o DTS primeiro"

            return
        }

        setBusy(true)

        status.text =
            "Reconstruindo boot..."

        Thread {

            val result =
                BootRebuildManager.rebuild(
                    this
                )

            runOnUiThread {

                if (result.success) {

                    bootGenerated =
                        true

                    verifyButton.isEnabled =
                        true

                    status.text =
                        "✓ Boot reconstruído"

                } else {

                    bootGenerated =
                        false

                    verifyButton.isEnabled =
                        false

                    status.text =
                        "✗ ${result.message}"
                }

                setBusy(false)
            }

        }.start()
    }


    private fun verifyBoot() {

        if (!bootGenerated) {

            status.text =
                "Gere o boot primeiro"

            return
        }

        setBusy(true)

        status.text =
            "Verificando boot..."

        Thread {

            val result =
                BootRoundTripVerifier.verify(
                    this
                )

            runOnUiThread {

                if (result.success) {

                    bootVerified =
                        true

                    flashButton.isEnabled =
                        true

                    status.text =
                        "✓ Boot verificado"

                } else {

                    bootVerified =
                        false

                    flashButton.isEnabled =
                        false

                    status.text =
                        "✗ ${result.message}"
                }

                setBusy(false)
            }

        }.start()
    }


    private fun confirmFlash() {

        if (!bootVerified)
            return

        AlertDialog.Builder(this)
            .setTitle(
                "Gravar boot"
            )
            .setMessage(
                "O boot foi reconstruído e verificado.\n\n" +
                    "Deseja gravá-lo na partição de boot?\n\n" +
                    "O aparelho não será reiniciado automaticamente."
            )
            .setNegativeButton(
                "Cancelar",
                null
            )
            .setPositiveButton(
                "Flash"
            ) {
                _,
                _ ->

                executeFlash()
            }
            .show()
    }


    private fun executeFlash() {

        setBusy(true)

        flashButton.isEnabled =
            false

        status.text =
            "Gravando boot..."

        Thread {

            val result =
                BootFlashManager.flash(
                    this
                )

            runOnUiThread {

                if (result.success) {

                    status.text =
                        "✓ Boot gravado e verificado"

                } else {

                    status.text =
                        "✗ ${result.message}"

                    flashButton.isEnabled =
                        true
                }

                setBusy(false)
            }

        }.start()
    }


    private fun startSearch() {

        val query =
            searchInput.text
                .toString()

        if (query.isBlank()) {

            searchStatus.text =
                "Digite algo para buscar"

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
                "Nenhum resultado"

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
}
