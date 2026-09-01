package com.exynosbess.app

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.os.Process
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.LinearLayout
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import com.exynosbess.app.boot.ShellExecutor
import com.exynosbess.app.boot.BootRebuildManager
import com.exynosbess.app.boot.BootRoundTripVerifier
import com.exynosbess.app.boot.BootFlashManager
import com.exynosbess.app.tools.ToolManager
import java.io.File
import java.math.BigInteger

class DtsEditorActivity : Activity() {

    private lateinit var editorTitle: TextView
    private lateinit var editorPath: TextView
    private lateinit var dtsEditor: EditText

    private lateinit var searchInput: EditText
    private lateinit var searchButton: Button
    private lateinit var searchNextButton: Button
    private lateinit var searchStatus: TextView

    private lateinit var saveDtsButton: Button
    private lateinit var validateDtsButton: Button
    private lateinit var editorStatus: TextView

    private lateinit var numericBaseButton: Button

    private var decimalMode = false

    private lateinit var bootActionsPanel: LinearLayout
    private lateinit var bootActionStatus: TextView
    private lateinit var rebuildFromEditorButton: Button
    private lateinit var verifyFromEditorButton: Button
    private lateinit var flashFromEditorButton: Button

    private var loadingDts = false
    private var validatedContent: String? = null
    private var bootGenerated = false
    private var bootVerified = false

    private var dtbIndex: Int = 0
    private lateinit var dtsFile: File

    private var sessionMode =
        false

    private var searchResults =
        emptyList<Int>()

    private var currentSearchIndex =
        -1

    private var lastSearchQuery =
        ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_dts_editor)

        editorTitle =
            findViewById(R.id.editorTitle)

        editorPath =
            findViewById(R.id.editorPath)

        dtsEditor =
            findViewById(R.id.dtsEditor)

        searchInput =
            findViewById(R.id.searchInput)

        searchButton =
            findViewById(R.id.searchButton)

        searchNextButton =
            findViewById(R.id.searchNextButton)

        searchStatus =
            findViewById(R.id.searchStatus)

        saveDtsButton =
            findViewById(R.id.saveDtsButton)

        validateDtsButton =
            findViewById(R.id.validateDtsButton)

        editorStatus =
            findViewById(R.id.editorStatus)

        numericBaseButton =
            findViewById(R.id.numericBaseButton)

        bootActionsPanel =
            findViewById(R.id.bootActionsPanel)

        bootActionStatus =
            findViewById(R.id.bootActionStatus)

        rebuildFromEditorButton =
            findViewById(R.id.rebuildFromEditorButton)

        verifyFromEditorButton =
            findViewById(R.id.verifyFromEditorButton)

        flashFromEditorButton =
            findViewById(R.id.flashFromEditorButton)

        bootActionsPanel.visibility =
            View.VISIBLE

        bootActionStatus.text =
            "○ Valide o DTS para continuar"

        rebuildFromEditorButton.isEnabled =
            false

        verifyFromEditorButton.isEnabled =
            false

        flashFromEditorButton.isEnabled =
            false

        dtbIndex =
            intent.getIntExtra(
                "dtb_index",
                0
            )

        val workspacePath =
            intent.getStringExtra(
                "workspace_path"
            )

        sessionMode =
            !workspacePath.isNullOrBlank()

        val workspace =
            if (
                workspacePath.isNullOrBlank()
            ) {
                File(
                    filesDir,
                    "workspace"
                )
            } else {
                File(
                    workspacePath
                )
            }

        dtsFile =
            File(
                workspace,
                "dtbs/dtb_${dtbIndex}.dts"
            )

        editorTitle.text =
            "Editor DTS $dtbIndex"

        editorPath.text =
            dtsFile.absolutePath

        if (sessionMode) {

            bootActionsPanel.visibility =
                View.GONE
        }

        loadDts()

        searchButton.setOnClickListener {
            startSearch()
        }

        searchNextButton.setOnClickListener {
            showNextResult()
        }

        saveDtsButton.setOnClickListener {
            saveDts()
        }

        validateDtsButton.setOnClickListener {
            validateDts()
        }

        numericBaseButton.setOnClickListener {
            toggleNumericBase()
        }

        rebuildFromEditorButton.setOnClickListener {
            rebuildBoot()
        }

        verifyFromEditorButton.setOnClickListener {
            verifyBoot()
        }

        flashFromEditorButton.setOnClickListener {
            confirmFlash()
        }

        dtsEditor.addTextChangedListener(
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
                    if (!loadingDts) {
                        invalidateBootFlowIfNeeded()
                    }
                }

                override fun afterTextChanged(
                    s: Editable?
                ) = Unit
            }
        )
    }

    private fun startSearch() {

        val query =
            searchInput.text
                .toString()
                .trim()

        if (query.isEmpty()) {

            searchResults =
                emptyList()

            currentSearchIndex =
                -1

            searchNextButton.isEnabled =
                false

            searchStatus.text =
                "Digite algo para buscar"

            return
        }

        val content =
            dtsEditor.text
                .toString()

        val lowerContent =
            content.lowercase()

        val lowerQuery =
            query.lowercase()

        val results =
            mutableListOf<Int>()

        var startIndex =
            0

        while (startIndex < lowerContent.length) {

            val found =
                lowerContent.indexOf(
                    lowerQuery,
                    startIndex
                )

            if (found == -1) {
                break
            }

            results.add(found)

            startIndex =
                found +
                maxOf(
                    1,
                    lowerQuery.length
                )
        }

        searchResults =
            results

        lastSearchQuery =
            query

        if (searchResults.isEmpty()) {

            currentSearchIndex =
                -1

            searchNextButton.isEnabled =
                false

            searchStatus.text =
                "Nenhuma ocorrência encontrada"

            return
        }

        currentSearchIndex =
            0

        searchNextButton.isEnabled =
            searchResults.size > 1

        selectCurrentResult()
    }

    private fun showNextResult() {

        val query =
            searchInput.text
                .toString()
                .trim()

        /*
         * Se o usuário alterou a palavra,
         * refazer a busca.
         */
        if (
            query != lastSearchQuery ||
            searchResults.isEmpty()
        ) {
            startSearch()
            return
        }

        currentSearchIndex++

        if (
            currentSearchIndex >=
            searchResults.size
        ) {
            currentSearchIndex = 0
        }

        selectCurrentResult()
    }

    private fun selectCurrentResult() {

        if (
            currentSearchIndex < 0 ||
            currentSearchIndex >=
            searchResults.size
        ) {
            return
        }

        val query =
            lastSearchQuery

        val start =
            searchResults[
                currentSearchIndex
            ]

        val end =
            start +
            query.length

        try {

            dtsEditor.requestFocus()

            dtsEditor.setSelection(
                start,
                minOf(
                    end,
                    dtsEditor.text.length
                )
            )

            searchStatus.text =
                "Resultado " +
                "${currentSearchIndex + 1} " +
                "de ${searchResults.size}"

        } catch (e: Exception) {

            searchStatus.text =
                "Falha ao selecionar resultado"
        }
    }

    private fun loadDts() {

        if (!dtsFile.exists()) {

            editorStatus.text =
                "✗ Arquivo DTS não encontrado"

            saveDtsButton.isEnabled =
                false

            validateDtsButton.isEnabled =
                false

            searchButton.isEnabled =
                false

            searchNextButton.isEnabled =
                false

            return
        }

        try {

            val content =
                dtsFile.readText()

            loadingDts = true
            dtsEditor.setText(content)
            loadingDts = false

            editorStatus.text =
                "✓ DTS carregado"

        } catch (e: Exception) {

            editorStatus.text =
                "✗ Falha ao carregar DTS\n${e.message}"
        }
    }

    private fun saveDts() {

        ensureHexModeBeforePersistence()

        try {

            dtsFile.writeText(
                dtsEditor.text.toString()
            )

            editorStatus.text =
                "✓ DTS salvo"

            invalidateBootFlow()

        } catch (e: Exception) {

            editorStatus.text =
                "✗ Falha ao salvar DTS\n${e.message}"
        }
    }

    private fun validateDts() {

        ensureHexModeBeforePersistence()

        editorStatus.text =
            "Validando DTS..."

        saveDtsButton.isEnabled =
            false

        validateDtsButton.isEnabled =
            false

        try {

            dtsFile.writeText(
                dtsEditor.text.toString()
            )

        } catch (e: Exception) {

            editorStatus.text =
                "✗ Falha ao salvar antes da validação\n${e.message}"

            saveDtsButton.isEnabled =
                true

            validateDtsButton.isEnabled =
                true

            return
        }

        Thread {

            val tool =
                ToolManager.installDtc(this)

            if (
                !tool.success ||
                tool.path == null
            ) {

                runOnUiThread {

                    editorStatus.text =
                        "✗ Falha ao preparar dtc\n${tool.message}"

                    saveDtsButton.isEnabled =
                        true

                    validateDtsButton.isEnabled =
                        true
                }

                return@Thread
            }

            val validatedDtb =
                File(
                    dtsFile.parentFile,
                    "validated_dtb_${dtbIndex}.dtb"
                )

            if (validatedDtb.exists()) {
                validatedDtb.delete()
            }

            val command =
                "'${tool.path}' " +
                "-I dts " +
                "-O dtb " +
                "-o '${validatedDtb.absolutePath}' " +
                "'${dtsFile.absolutePath}'"

            val result =
                ShellExecutor.root(
                    command
                )

            if (
                result.success &&
                validatedDtb.exists() &&
                validatedDtb.length() > 0
            ) {

                val appUid =
                    Process.myUid()

                ShellExecutor.root(
                    "chown $appUid:$appUid '${validatedDtb.absolutePath}' && " +
                    "chmod 600 '${validatedDtb.absolutePath}'"
                )
            }

            runOnUiThread {

                saveDtsButton.isEnabled =
                    true

                validateDtsButton.isEnabled =
                    true

                if (
                    result.success &&
                    validatedDtb.exists() &&
                    validatedDtb.length() > 0
                ) {

                    editorStatus.text =
                        "✓ DTS válido"

                    validatedContent =
                        dtsEditor.text.toString()

                    bootGenerated =
                        false

                    bootVerified =
                        false

                    if (sessionMode) {

                        bootActionsPanel.visibility =
                            View.GONE

                    } else {

                        bootActionsPanel.visibility =
                            View.VISIBLE

                        rebuildFromEditorButton.isEnabled =
                            true

                        verifyFromEditorButton.isEnabled =
                            false

                        flashFromEditorButton.isEnabled =
                            false

                        bootActionStatus.text =
                            "✓ DTS $dtbIndex validado\n" +
                            "○ Novo boot ainda não gerado"
                    }

                } else {

                    if (validatedDtb.exists()) {
                        validatedDtb.delete()
                    }

                    val errorLine =
                        result.output
                            .lineSequence()
                            .firstOrNull {
                                it.isNotBlank()
                            }
                            ?.take(180)
                            ?: "Erro de compilação"

                    editorStatus.text =
                        "✗ DTS inválido: $errorLine"
                }
            }
        }.start()
    }

    private fun invalidateBootFlowIfNeeded() {

        val validated =
            validatedContent
                ?: return

        if (
            dtsEditor.text.toString() !=
            validated
        ) {
            invalidateBootFlow()

            bootActionsPanel.visibility =
                View.VISIBLE

            bootActionStatus.text =
                "⚠ DTS modificado após a validação\n" +
                "Valide novamente antes de gerar ou gravar o boot."
        }
    }

    private fun invalidateBootFlow() {

        validatedContent =
            null

        bootGenerated =
            false

        bootVerified =
            false

        rebuildFromEditorButton.isEnabled =
            false

        verifyFromEditorButton.isEnabled =
            false

        flashFromEditorButton.isEnabled =
            false
    }

    private fun rebuildBoot() {

        if (sessionMode) {
            editorStatus.text =
                "Reconstrução controlada pela sessão da partição."
            return
        }

        if (
            validatedContent == null ||
            dtsEditor.text.toString() != validatedContent
        ) {

            invalidateBootFlow()

            bootActionsPanel.visibility =
                View.VISIBLE

            bootActionStatus.text =
                "⚠ DTS precisa ser validado novamente."

            return
        }

        setBootActionsBusy(true)

        bootActionStatus.text =
            "Gerando boot_modified.img...\n" +
            "Recompilando todos os Device Trees."

        Thread {

            val result =
                BootRebuildManager.rebuild(
                    this
                )

            runOnUiThread {

                setBootActionsBusy(false)

                if (result.success) {

                    bootGenerated =
                        true

                    bootVerified =
                        false

                    rebuildFromEditorButton.isEnabled =
                        true

                    verifyFromEditorButton.isEnabled =
                        true

                    flashFromEditorButton.isEnabled =
                        false

                    bootActionStatus.text =
                        "✓ DTS validado\n" +
                        "✓ Novo boot gerado\n" +
                        "○ Aguardando verificação\n\n" +
                        "${result.bootPath ?: ""}"

                } else {

                    bootGenerated =
                        false

                    bootVerified =
                        false

                    rebuildFromEditorButton.isEnabled =
                        true

                    verifyFromEditorButton.isEnabled =
                        false

                    flashFromEditorButton.isEnabled =
                        false

                    bootActionStatus.text =
                        "✗ Falha ao gerar boot\n\n" +
                        result.message
                }
            }

        }.start()
    }

    private fun verifyBoot() {

        if (sessionMode) {
            editorStatus.text =
                "Verificação controlada pela sessão da partição."
            return
        }

        if (!bootGenerated) {

            bootActionStatus.text =
                "Gere o novo boot antes da verificação."

            return
        }

        setBootActionsBusy(true)

        bootActionStatus.text =
            "Verificando boot...\n" +
            "Executando round-trip dos Device Trees."

        Thread {

            val result =
                BootRoundTripVerifier.verify(
                    this
                )

            runOnUiThread {

                setBootActionsBusy(false)

                if (result.success) {

                    bootVerified =
                        true

                    rebuildFromEditorButton.isEnabled =
                        true

                    verifyFromEditorButton.isEnabled =
                        true

                    flashFromEditorButton.isEnabled =
                        true

                    val checks =
                        result.checks.joinToString(
                            separator = "\n"
                        ) {
                            check ->

                            if (check.equal) {
                                "✓ DTB ${check.index}: OK"
                            } else {
                                "✗ DTB ${check.index}: diferente"
                            }
                        }

                    bootActionStatus.text =
                        "✓ DTS validado\n" +
                        "✓ Novo boot gerado\n" +
                        "✓ Round-trip verificado\n\n" +
                        checks

                } else {

                    bootVerified =
                        false

                    rebuildFromEditorButton.isEnabled =
                        true

                    verifyFromEditorButton.isEnabled =
                        true

                    flashFromEditorButton.isEnabled =
                        false

                    bootActionStatus.text =
                        "✗ Verificação falhou\n\n" +
                        result.message
                }
            }

        }.start()
    }

    private fun confirmFlash() {

        if (sessionMode) {
            editorStatus.text =
                "Flash bloqueado no editor. Use a sessão da partição."
            return
        }

        if (!bootVerified) {

            bootActionStatus.text =
                "Flash bloqueado.\n" +
                "Verifique o novo boot primeiro."

            return
        }

        AlertDialog.Builder(this)
            .setTitle(
                "Gravar novo boot?"
            )
            .setMessage(
                "O boot modificado será gravado na partição boot.\n\n" +
                "Antes da gravação será criado um backup pré-flash e, " +
                "depois, a imagem gravada será verificada por SHA-256.\n\n" +
                "O aparelho NÃO será reiniciado automaticamente."
            )
            .setNegativeButton(
                "Cancelar",
                null
            )
            .setPositiveButton(
                "FLASH"
            ) { _, _ ->

                executeFlash()
            }
            .show()
    }

    private fun executeFlash() {

        if (!bootVerified) {
            return
        }

        setBootActionsBusy(true)

        bootActionStatus.text =
            "Gravando boot...\n" +
            "Não feche o aplicativo e não reinicie o aparelho."

        Thread {

            val result =
                BootFlashManager.flash(
                    this
                )

            runOnUiThread {

                setBootActionsBusy(false)

                rebuildFromEditorButton.isEnabled =
                    true

                verifyFromEditorButton.isEnabled =
                    true

                flashFromEditorButton.isEnabled =
                    result.success

                if (result.success) {

                    bootActionStatus.text =
                        "✓ BOOT GRAVADO E VERIFICADO\n\n" +
                        result.message

                } else {

                    flashFromEditorButton.isEnabled =
                        false

                    bootActionStatus.text =
                        "✗ FLASH NÃO CONCLUÍDO\n\n" +
                        result.message
                }
            }

        }.start()
    }

    private fun setBootActionsBusy(
        busy: Boolean
    ) {

        rebuildFromEditorButton.isEnabled =
            !busy &&
            validatedContent != null

        verifyFromEditorButton.isEnabled =
            !busy &&
            bootGenerated

        flashFromEditorButton.isEnabled =
            !busy &&
            bootVerified
    }


    private fun toggleNumericBase() {

        val current =
            dtsEditor.text.toString()

        val cursor =
            dtsEditor.selectionStart
                .coerceAtLeast(0)

        val converted =
            if (decimalMode) {
                convertCellsDecimalToHex(current)
            } else {
                convertCellsHexToDecimal(current)
            }

        loadingDts = true

        dtsEditor.setText(converted)

        dtsEditor.setSelection(
            minOf(
                cursor,
                converted.length
            )
        )

        loadingDts = false

        decimalMode =
            !decimalMode

        numericBaseButton.text =
            if (decimalMode)
                "HEX"
            else
                "DEC"

        editorStatus.text =
            if (decimalMode)
                "Modo decimal"
            else
                "Modo hexadecimal"
    }


    private fun ensureHexModeBeforePersistence() {

        if (!decimalMode) {
            return
        }

        val current =
            dtsEditor.text.toString()

        val converted =
            convertCellsDecimalToHex(
                current
            )

        val cursor =
            dtsEditor.selectionStart
                .coerceAtLeast(0)

        loadingDts =
            true

        dtsEditor.setText(
            converted
        )

        dtsEditor.setSelection(
            minOf(
                cursor,
                converted.length
            )
        )

        loadingDts =
            false

        decimalMode =
            false

        numericBaseButton.text =
            "DEC"
    }


    private fun convertCellsHexToDecimal(
        source: String
    ): String {

        val cellRegex =
            Regex("""<([^<>]*)>""")

        val hexRegex =
            Regex("""0[xX]([0-9a-fA-F]+)""")

        return cellRegex.replace(source) { cell ->

            val content =
                cell.groupValues[1]

            val converted =
                hexRegex.replace(content) { number ->

                    try {

                        BigInteger(
                            number.groupValues[1],
                            16
                        ).toString(10)

                    } catch (_: Exception) {

                        number.value
                    }
                }

            "<$converted>"
        }
    }


    private fun convertCellsDecimalToHex(
        source: String
    ): String {

        val cellRegex =
            Regex("""<([^<>]*)>""")

        val decimalRegex =
            Regex(
                """(?<![A-Za-z0-9_])([0-9]+)(?![A-Za-z0-9_])"""
            )

        return cellRegex.replace(source) { cell ->

            val content =
                cell.groupValues[1]

            val converted =
                decimalRegex.replace(content) { number ->

                    try {

                        val value =
                            BigInteger(
                                number.groupValues[1],
                                10
                            )

                        "0x" +
                            value.toString(16)

                    } catch (_: Exception) {

                        number.value
                    }
                }

            "<$converted>"
        }
    }

}
