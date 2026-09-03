package com.exynosbess.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import com.exynosbess.app.dtbfile.DtbFileManager
import com.google.android.material.button.MaterialButton
import java.io.File

class DtbFileActivity :
    LocalizedActivity() {

    private lateinit var status:
        TextView

    private lateinit var details:
        TextView

    private lateinit var preview:
        TextView

    private lateinit var importButton:
        MaterialButton

    private lateinit var exportButton:
        MaterialButton


    private val importRequest =
        4101

    private val exportRequest =
        4102


    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        setContentView(
            R.layout.activity_dtb_file
        )

        status =
            findViewById(
                R.id.dtbFileStatus
            )

        details =
            findViewById(
                R.id.dtbFileDetails
            )

        preview =
            findViewById(
                R.id.dtbFilePreview
            )

        importButton =
            findViewById(
                R.id.dtbFileImportButton
            )

        exportButton =
            findViewById(
                R.id.dtbFileExportButton
            )


        importButton.setOnClickListener {

            val intent =
                Intent(
                    Intent.ACTION_OPEN_DOCUMENT
                ).apply {

                    addCategory(
                        Intent.CATEGORY_OPENABLE
                    )

                    type =
                        "application/octet-stream"
                }

            startActivityForResult(
                intent,
                importRequest
            )
        }


        exportButton.setOnClickListener {

            val source =
                DtbFileManager.importedFile(
                    this
                )

            if (!source.exists()) {
                status.text =
                    "Nenhum DTB para exportar."
                return@setOnClickListener
            }

            val intent =
                Intent(
                    Intent.ACTION_CREATE_DOCUMENT
                ).apply {

                    addCategory(
                        Intent.CATEGORY_OPENABLE
                    )

                    type =
                        "application/octet-stream"

                    putExtra(
                        Intent.EXTRA_TITLE,
                        "gaspkernel_export.dtb"
                    )
                }

            startActivityForResult(
                intent,
                exportRequest
            )
        }

        refresh()
    }


    @Deprecated(
        "Legacy activity result API kept for minSdk compatibility."
    )
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {

        super.onActivityResult(
            requestCode,
            resultCode,
            data
        )

        if (
            resultCode !=
            Activity.RESULT_OK
        ) {
            return
        }

        val uri =
            data?.data
                ?: return


        when (requestCode) {

            importRequest -> {

                status.text =
                    "Lendo DTB..."

                importDtb(
                    uri
                )
            }


            exportRequest -> {

                exportDtb(
                    uri
                )
            }
        }
    }


    private fun importDtb(
        uri: Uri
    ) {

        importButton.isEnabled =
            false

        exportButton.isEnabled =
            false

        Thread {

            val result =
                DtbFileManager.import(
                    this,
                    uri
                )

            runOnUiThread {

                importButton.isEnabled =
                    true

                render(
                    result
                )
            }

        }.start()
    }


    private fun exportDtb(
        uri: Uri
    ) {

        val source =
            DtbFileManager.importedFile(
                this
            )

        if (!source.exists()) {
            status.text =
                "Nenhum DTB para exportar."
            return
        }

        try {

            contentResolver
                .openOutputStream(uri)
                ?.use { output ->

                    source.inputStream()
                        .use { input ->

                            input.copyTo(
                                output
                            )
                        }

                } ?: throw IllegalStateException(
                    "Não foi possível abrir o destino."
                )

            status.text =
                "✓ DTB exportado"

        } catch (
            e: Exception
        ) {

            status.text =
                "✗ Falha ao exportar: ${e.message}"
        }
    }


    private fun refresh() {

        val current =
            DtbFileManager.importedFile(
                this
            )

        if (!current.exists()) {

            status.text =
                "Selecione um arquivo DTB."

            details.text =
                "A leitura é feita localmente e não requer root."

            preview.text =
                ""

            exportButton.isEnabled =
                false

            return
        }

        status.text =
            "Lendo DTB..."

        Thread {

            val result =
                DtbFileManager.inspect(
                    this
                )

            runOnUiThread {

                render(
                    result
                )
            }

        }.start()
    }


    private fun render(
        info: DtbFileManager.Info
    ) {

        status.text =
            if (info.valid) {
                "✓ ${info.message}"
            } else {
                "✗ ${info.message}"
            }

        exportButton.isEnabled =
            info.valid &&
            info.file != null


        details.text =
            buildString {

                if (
                    info.file != null
                ) {

                    append(
                        "Tamanho: ${formatBytes(info.size)}"
                    )

                    info.declaredSize
                        ?.let {

                            append(
                                "\nTamanho declarado: ${formatBytes(it)}"
                            )
                        }

                    info.model
                        ?.let {

                            append(
                                "\nModelo: $it"
                            )
                        }

                    info.compatible
                        ?.let {

                            append(
                                "\nCompatible: $it"
                            )
                        }

                    info.sha256
                        ?.let {

                            append(
                                "\nSHA-256: $it"
                            )
                        }
                }
            }


        val dts =
            info.dts

        preview.text =
            if (
                dts != null &&
                dts.exists()
            ) {

                try {

                    dts.readText()
                        .take(
                            30000
                        )

                } catch (
                    e: Exception
                ) {

                    "Falha lendo DTS: ${e.message}"
                }

            } else {

                ""
            }
    }


    private fun formatBytes(
        value: Long
    ): String {

        return when {

            value >=
                1024L * 1024L ->

                "%.2f MB".format(
                    value.toDouble() /
                        (1024.0 * 1024.0)
                )

            value >=
                1024L ->

                "%.2f KB".format(
                    value.toDouble() /
                        1024.0
                )

            else ->
                "$value B"
        }
    }
}
