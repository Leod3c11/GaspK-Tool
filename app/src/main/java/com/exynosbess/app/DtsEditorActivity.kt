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
    private lateinit var saveDtsButton: Button
    private lateinit var validateDtsButton: Button
    private lateinit var editorStatus: TextView

    private var dtbIndex: Int = 0
    private lateinit var dtsFile: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_dts_editor)

        editorTitle = findViewById(R.id.editorTitle)
        editorPath = findViewById(R.id.editorPath)
        dtsEditor = findViewById(R.id.dtsEditor)
        saveDtsButton = findViewById(R.id.saveDtsButton)
        validateDtsButton = findViewById(R.id.validateDtsButton)
        editorStatus = findViewById(R.id.editorStatus)

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

        saveDtsButton.setOnClickListener {
            saveDts()
        }

        validateDtsButton.setOnClickListener {
            validateDts()
        }
    }

    private fun loadDts() {

        if (!dtsFile.exists()) {

            editorStatus.text =
                "✗ Arquivo DTS não encontrado"

            saveDtsButton.isEnabled = false
            validateDtsButton.isEnabled = false

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

        saveDtsButton.isEnabled = false
        validateDtsButton.isEnabled = false

        /*
         * Salvamos primeiro o texto atual do editor.
         */
        try {

            dtsFile.writeText(
                dtsEditor.text.toString()
            )

        } catch (e: Exception) {

            editorStatus.text =
                "✗ Falha ao salvar antes da validação\n${e.message}"

            saveDtsButton.isEnabled = true
            validateDtsButton.isEnabled = true

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

                    saveDtsButton.isEnabled = true
                    validateDtsButton.isEnabled = true
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
                val appUid = Process.myUid()

                ShellExecutor.root(
                    "chown $appUid:$appUid '${validatedDtb.absolutePath}' && " +
                    "chmod 600 '${validatedDtb.absolutePath}'"
                )
            }

            runOnUiThread {

                saveDtsButton.isEnabled = true
                validateDtsButton.isEnabled = true

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
