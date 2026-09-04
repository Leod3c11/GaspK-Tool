package com.exynosbess.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.exynosbess.app.dtbfile.DtbFileManager
import com.exynosbess.app.dtbfile.InstalledDtbExporter
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

    private lateinit var selectorButton:
        MaterialButton

    private var selectedDtsIndex =
        0

    private var installedDtbForExport:
        File? = null


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

        selectorButton =
            findViewById(
                R.id.dtbFileSelectorButton
            )

        selectorButton.setOnClickListener {
            showDtsSelector()
        }


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

            status.text =
                getString(R.string.dtb_reading_installed)

            importButton.isEnabled =
                false

            exportButton.isEnabled =
                false

            Thread {

                val result =
                    InstalledDtbExporter.dump(
                        this
                    )

                runOnUiThread {

                    importButton.isEnabled =
                        true

                    exportButton.isEnabled =
                        true

                    if (
                        !result.success ||
                        result.file == null
                    ) {

                        status.text =
                            "✗ ${result.message}"

                        return@runOnUiThread
                    }

                    installedDtbForExport =
                        result.file

                    status.text =
                        getString(R.string.dtb_installed_read_success)

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
                                "gaspkernel_installed_dtb.dtb"
                            )
                        }

                    startActivityForResult(
                        intent,
                        exportRequest
                    )
                }
            }.start()
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
                    getString(R.string.dtb_reading)

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
            installedDtbForExport

        if (
            source == null ||
            !source.exists()
        ) {
            status.text =
                getString(R.string.dtb_export_none)
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
                    getString(R.string.dtb_destination_failed)
                )

            status.text =
                getString(R.string.dtb_export_success)

        } catch (
            e: Exception
        ) {

            status.text =
                getString(R.string.dtb_export_failed, e.message ?: "")
        }
    }


    private fun refresh() {

        val current =
            DtbFileManager.importedFile(
                this
            )

        if (!current.exists()) {

            status.text =
                getString(R.string.dtb_select_file)

            details.text =
                getString(R.string.dtb_local_read)

            preview.text =
                ""

            exportButton.isEnabled =
                true

            return
        }

        status.text =
            getString(R.string.dtb_reading)

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
                getString(R.string.dtb_read_success)
            } else {
                getString(R.string.dtb_read_failed, info.message)
            }

        exportButton.isEnabled =
            true


        details.text =
            buildString {

                if (
                    info.file != null
                ) {

                    append(
                        getString(R.string.dtb_size, formatBytes(info.size))
                    )

                    info.declaredSize
                        ?.let {

                            append(
                                "\n" + getString(R.string.dtb_declared_size, formatBytes(it))
                            )
                        }

                    info.model
                        ?.let {

                            append(
                                "\n" + getString(R.string.dtb_model, it)
                            )
                        }

                    info.compatible
                        ?.let {

                            append(
                                "\n" + getString(R.string.dtb_compatible, it)
                            )
                        }

                    info.sha256
                        ?.let {

                            append(
                                "\n" + getString(R.string.dtb_sha256, it)
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

                } catch (
                    e: Exception
                ) {

                    getString(R.string.dtb_dts_read_failed, e.message ?: "")
                }

            } else {

                ""
            }
    }


    private fun showDtsSelector() {

        val parts =
            DtbFileManager.dtsParts(
                this
            )

        if (parts.isEmpty()) {
            return
        }

        val labels =
            parts.mapIndexed {
                    index,
                    file ->

                "Device Tree ${index + 1}  •  ${formatBytes(file.length())}"
            }
                .toTypedArray()

        androidx.appcompat.app.AlertDialog.Builder(
            this
        )
            .setTitle(
                "Selecionar Device Tree"
            )
            .setSingleChoiceItems(
                labels,
                selectedDtsIndex
            ) {
                    dialog,
                    which ->

                selectedDtsIndex =
                    which

                val selected =
                    parts[which]

                try {
                    preview.text =
                        selected.readText()

                    selectorButton.text =
                        "Device Tree ${which + 1} / ${parts.size}"

                } catch (
                    e: Exception
                ) {
                    preview.text =
                        "Falha lendo DTS: ${e.message}"
                }

                dialog.dismiss()
            }
            .setNegativeButton(
                "Cancelar",
                null
            )
            .show()
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
