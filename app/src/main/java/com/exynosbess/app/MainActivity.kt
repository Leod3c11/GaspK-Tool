package com.exynosbess.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import com.exynosbess.app.boot.BootBackupManager
import com.exynosbess.app.boot.BootPartitionResolver
import com.exynosbess.app.boot.BootUnpackManager
import com.exynosbess.app.boot.DtbScanner
import com.exynosbess.app.boot.DtbExtractor
import com.exynosbess.app.boot.DtbDecompiler

class MainActivity : Activity() {

    private lateinit var rootStatus: TextView
    private lateinit var rootOutput: TextView
    private lateinit var bootStatus: TextView
    private lateinit var backupStatus: TextView
    private lateinit var unpackStatus: TextView
    private lateinit var dtbScanStatus: TextView
    private lateinit var dtbExtractStatus: TextView
    private lateinit var decompileDtbStatus: TextView

    private lateinit var checkRootButton: Button
    private lateinit var detectBootButton: Button
    private lateinit var backupBootButton: Button
    private lateinit var unpackBootButton: Button
    private lateinit var scanDtbButton: Button
    private lateinit var extractDtbButton: Button
    private lateinit var decompileDtbButton: Button
    private lateinit var openDts0Button: Button
    private lateinit var openDts1Button: Button

    private var bootPartitionPath: String? = null
    private var workspacePath: String? = null
    private var lastDtbEntries: List<DtbScanner.DtbEntry> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        rootStatus = findViewById(R.id.rootStatus)
        rootOutput = findViewById(R.id.rootOutput)
        bootStatus = findViewById(R.id.bootStatus)
        backupStatus = findViewById(R.id.backupStatus)
        unpackStatus = findViewById(R.id.unpackStatus)
        dtbScanStatus = findViewById(R.id.dtbScanStatus)
        dtbExtractStatus = findViewById(R.id.dtbExtractStatus)
        decompileDtbStatus = findViewById(R.id.decompileDtbStatus)

        checkRootButton = findViewById(R.id.checkRootButton)
        detectBootButton = findViewById(R.id.detectBootButton)
        backupBootButton = findViewById(R.id.backupBootButton)
        unpackBootButton = findViewById(R.id.unpackBootButton)
        scanDtbButton = findViewById(R.id.scanDtbButton)
        extractDtbButton = findViewById(R.id.extractDtbButton)
        decompileDtbButton = findViewById(R.id.decompileDtbButton)
        openDts0Button = findViewById(R.id.openDts0Button)
        openDts1Button = findViewById(R.id.openDts1Button)

        checkRootButton.setOnClickListener {
            verifyRoot()
        }

        detectBootButton.setOnClickListener {
            detectBootPartition()
        }

        backupBootButton.setOnClickListener {
            createBootBackup()
        }

        unpackBootButton.setOnClickListener {
            unpackBoot()
        }

        scanDtbButton.setOnClickListener {
            scanDtb()
        }

        extractDtbButton.setOnClickListener {
            extractDtbs()
        }

        decompileDtbButton.setOnClickListener {
            decompileDtbs()
        }

        openDts0Button.setOnClickListener {
            openDtsEditor(0)
        }

        openDts1Button.setOnClickListener {
            openDtsEditor(1)
        }
    }

    private fun verifyRoot() {

        rootStatus.text = "Solicitando acesso root..."
        rootOutput.text = ""

        checkRootButton.isEnabled = false

        Thread {

            val result = RootManager.checkRoot()

            runOnUiThread {

                checkRootButton.isEnabled = true

                if (result.granted) {

                    rootStatus.text =
                        "✓ Acesso root concedido"

                    detectBootButton.isEnabled = true

                } else {

                    rootStatus.text =
                        "✗ Acesso root não concedido"

                    detectBootButton.isEnabled = false
                    backupBootButton.isEnabled = false
                    unpackBootButton.isEnabled = false
                    scanDtbButton.isEnabled = false
                }

                rootOutput.text =
                    "Resultado:\n${result.output}"
            }
        }.start()
    }

    private fun detectBootPartition() {

        bootStatus.text =
            "Procurando partição boot..."

        backupBootButton.isEnabled = false

        Thread {

            val result =
                BootPartitionResolver.detect()

            runOnUiThread {

                if (
                    result.found &&
                    result.path != null
                ) {

                    bootPartitionPath =
                        result.path

                    bootStatus.text =
                        "✓ Partição boot encontrada\n\n" +
                        "Link:\n${result.path}\n\n" +
                        "Dispositivo real:\n${result.details}"

                    backupBootButton.isEnabled = true

                } else {

                    bootPartitionPath = null

                    bootStatus.text =
                        "✗ ${result.details}"

                    backupBootButton.isEnabled = false
                }
            }
        }.start()
    }

    private fun createBootBackup() {

        val bootPath =
            bootPartitionPath ?: return

        backupStatus.text =
            "Criando backup do boot.img..."

        backupBootButton.isEnabled = false

        Thread {

            val result =
                BootBackupManager.backup(
                    this,
                    bootPath
                )

            runOnUiThread {

                backupBootButton.isEnabled = true

                if (result.success) {

                    backupStatus.text =
                        "✓ ${result.message}\n\n" +
                        "Arquivo:\n${result.outputPath}"

                    unpackBootButton.isEnabled = true

                } else {

                    backupStatus.text =
                        "✗ Falha no backup\n\n${result.message}"

                    unpackBootButton.isEnabled = false
                }
            }
        }.start()
    }

    private fun unpackBoot() {

        unpackStatus.text =
            "Descompactando boot.img..."

        unpackBootButton.isEnabled = false
        scanDtbButton.isEnabled = false

        Thread {

            val result =
                BootUnpackManager.unpack(this)

            runOnUiThread {

                unpackBootButton.isEnabled = true

                if (result.success) {

                    workspacePath =
                        result.workspace

                    val fileList =
                        result.files.joinToString("\n")

                    unpackStatus.text =
                        "✓ Boot.img descompactado\n\n" +
                        "Workspace:\n${result.workspace}\n\n" +
                        "Arquivos encontrados:\n$fileList\n\n" +
                        "magiskboot:\n${result.output}"

                    scanDtbButton.isEnabled = true

                } else {

                    workspacePath = null

                    unpackStatus.text =
                        "✗ Falha ao descompactar\n\n" +
                        result.output
                }
            }
        }.start()
    }

    private fun scanDtb() {

        val workspace =
            workspacePath ?: return

        dtbScanStatus.text =
            "Procurando Device Trees..."

        scanDtbButton.isEnabled = false

        Thread {

            val result =
                DtbScanner.scan(workspace)

            runOnUiThread {

                scanDtbButton.isEnabled = true

                if (result.success) {

                    lastDtbEntries = result.entries
                    extractDtbButton.isEnabled = true

                    val list =
                        result.entries.joinToString("\n\n") { entry ->

                            "Arquivo: ${entry.sourceFile}\n" +
                            "Offset: 0x${entry.offset.toString(16).uppercase()}\n" +
                            "Offset decimal: ${entry.offset}\n" +
                            "Tamanho: ${entry.size} bytes"
                        }

                    dtbScanStatus.text =
                        "✓ ${result.message}\n\n$list"

                } else {

                    dtbScanStatus.text =
                        "✗ ${result.message}"
                }
            }
        }.start()
    }

    private fun extractDtbs() {

        val workspace =
            workspacePath ?: return

        if (lastDtbEntries.isEmpty()) {
            dtbExtractStatus.text =
                "✗ Nenhum DTB disponível"
            return
        }

        dtbExtractStatus.text =
            "Extraindo DTBs..."

        extractDtbButton.isEnabled = false

        Thread {

            val result =
                DtbExtractor.extract(
                    workspace,
                    lastDtbEntries
                )

            runOnUiThread {

                extractDtbButton.isEnabled = true

                if (result.success) {

                    decompileDtbButton.isEnabled = true

                    val list =
                        result.files.joinToString("\n\n") { file ->

                            "DTB ${file.index}\n" +
                            "Origem: ${file.sourceFile}\n" +
                            "Offset: 0x${file.offset.toString(16).uppercase()}\n" +
                            "Tamanho: ${file.size} bytes\n" +
                            "Arquivo: ${file.outputPath}"
                        }

                    dtbExtractStatus.text =
                        "✓ ${result.message}\n\n$list"

                } else {

                    dtbExtractStatus.text =
                        "✗ ${result.message}"
                }
            }
        }.start()
    }


    private fun decompileDtbs() {

        val workspace =
            workspacePath ?: return

        decompileDtbStatus.text =
            "Convertendo DTB para DTS..."

        decompileDtbButton.isEnabled =
            false

        Thread {

            val result =
                DtbDecompiler.decompileAll(
                    this,
                    workspace
                )

            runOnUiThread {

                decompileDtbButton.isEnabled =
                    true

                if (result.success) {

                    val list =
                        result.files.joinToString("\n\n") { file ->

                            "DTS ${file.index}\n" +
                            "Arquivo: ${file.dtsPath}\n" +
                            "Tamanho: ${file.dtsSize} bytes"
                        }

                    decompileDtbStatus.text =
                        "✓ ${result.message}\n\n$list"

                    openDts0Button.isEnabled =
                        result.files.any { it.index == 0 }

                    openDts1Button.isEnabled =
                        result.files.any { it.index == 1 }

                } else {

                    decompileDtbStatus.text =
                        "✗ ${result.message}"
                }
            }
        }.start()
    }


    private fun openDtsEditor(index: Int) {

        val intent = Intent(
            this,
            DtsEditorActivity::class.java
        )

        intent.putExtra(
            "dtb_index",
            index
        )

        startActivity(intent)
    }

}
