package com.exynosbess.app

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import com.exynosbess.app.boot.BootBackupManager
import com.exynosbess.app.boot.BootPartitionResolver
import com.exynosbess.app.boot.BootUnpackManager

class MainActivity : Activity() {

    private lateinit var rootStatus: TextView
    private lateinit var rootOutput: TextView
    private lateinit var bootStatus: TextView
    private lateinit var backupStatus: TextView
    private lateinit var unpackStatus: TextView

    private lateinit var checkRootButton: Button
    private lateinit var detectBootButton: Button
    private lateinit var backupBootButton: Button
    private lateinit var unpackBootButton: Button

    private var bootPartitionPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        rootStatus = findViewById(R.id.rootStatus)
        rootOutput = findViewById(R.id.rootOutput)
        bootStatus = findViewById(R.id.bootStatus)
        backupStatus = findViewById(R.id.backupStatus)
        unpackStatus = findViewById(R.id.unpackStatus)

        checkRootButton = findViewById(R.id.checkRootButton)
        detectBootButton = findViewById(R.id.detectBootButton)
        backupBootButton = findViewById(R.id.backupBootButton)
        unpackBootButton = findViewById(R.id.unpackBootButton)

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
                    rootStatus.text = "✓ Acesso root concedido"
                    detectBootButton.isEnabled = true
                } else {
                    rootStatus.text = "✗ Acesso root não concedido"
                    detectBootButton.isEnabled = false
                    backupBootButton.isEnabled = false
                    unpackBootButton.isEnabled = false
                }

                rootOutput.text = "Resultado:\n${result.output}"
            }
        }.start()
    }

    private fun detectBootPartition() {
        bootStatus.text = "Procurando partição boot..."
        backupBootButton.isEnabled = false

        Thread {
            val result = BootPartitionResolver.detect()

            runOnUiThread {
                if (result.found && result.path != null) {
                    bootPartitionPath = result.path

                    bootStatus.text =
                        "✓ Partição boot encontrada\n\n" +
                        "Link:\n${result.path}\n\n" +
                        "Dispositivo real:\n${result.details}"

                    backupBootButton.isEnabled = true
                } else {
                    bootPartitionPath = null
                    bootStatus.text = "✗ ${result.details}"
                    backupBootButton.isEnabled = false
                }
            }
        }.start()
    }

    private fun createBootBackup() {
        val bootPath = bootPartitionPath ?: return

        backupStatus.text = "Criando backup do boot.img..."
        backupBootButton.isEnabled = false

        Thread {
            val result = BootBackupManager.backup(this, bootPath)

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
        unpackStatus.text = "Descompactando boot.img..."
        unpackBootButton.isEnabled = false

        Thread {
            val result = BootUnpackManager.unpack(this)

            runOnUiThread {
                unpackBootButton.isEnabled = true

                if (result.success) {
                    val fileList = result.files.joinToString("\n")

                    unpackStatus.text =
                        "✓ Boot.img descompactado\n\n" +
                        "Workspace:\n${result.workspace}\n\n" +
                        "Arquivos encontrados:\n$fileList\n\n" +
                        "magiskboot:\n${result.output}"
                } else {
                    unpackStatus.text =
                        "✗ Falha ao descompactar\n\n" +
                        result.output
                }
            }
        }.start()
    }
}
