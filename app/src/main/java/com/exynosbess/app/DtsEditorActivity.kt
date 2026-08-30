package com.exynosbess.app

import android.app.Activity
import android.os.Bundle
import android.os.Process
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import com.exynosbess.app.boot.ShellExecutor
import com.exynosbess.app.tools.ToolManager
import java.io.File

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

    private var dtbIndex: Int = 0
    private lateinit var dtsFile: File

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

        dtbIndex =
            intent.getIntExtra(
                "dtb_index",
                0
            )

        dtsFile =
            File(
                filesDir,
                "workspace/dtbs/dtb_${dtbIndex}.dts"
            )

        editorTitle.text =
            "Editor DTS $dtbIndex"

        editorPath.text =
            dtsFile.absolutePath

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

            dtsEditor.setText(content)

            editorStatus.text =
                "✓ DTS carregado\n" +
                "${content.length} caracteres"

        } catch (e: Exception) {

            editorStatus.text =
                "✗ Falha ao carregar DTS\n${e.message}"
        }
    }

    private fun saveDts() {

        try {

            dtsFile.writeText(
                dtsEditor.text.toString()
            )

            editorStatus.text =
                "✓ DTS salvo\n" +
                "${dtsFile.length()} bytes"

        } catch (e: Exception) {

            editorStatus.text =
                "✗ Falha ao salvar DTS\n${e.message}"
        }
    }

    private fun validateDts() {

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
                        "✓ DTS válido\n\n" +
                        "DTB de teste criado:\n" +
                        "${validatedDtb.absolutePath}\n\n" +
                        "Tamanho: ${validatedDtb.length()} bytes\n\n" +
                        if (result.output.isNotBlank())
                            result.output
                        else
                            "dtc concluiu sem erros"

                } else {

                    if (validatedDtb.exists()) {
                        validatedDtb.delete()
                    }

                    editorStatus.text =
                        "✗ DTS inválido\n\n" +
                        "Erro do dtc:\n" +
                        result.output
                }
            }
        }.start()
    }
}
