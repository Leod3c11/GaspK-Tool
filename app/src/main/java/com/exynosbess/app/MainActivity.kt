package com.exynosbess.app

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import com.exynosbess.app.boot.BootBackupManager
import com.exynosbess.app.boot.BootPartitionResolver

class MainActivity : Activity() {

    private lateinit var rootStatus: TextView
    private lateinit var rootOutput: TextView
    private lateinit var bootStatus: TextView
    private lateinit var backupStatus: TextView

    private lateinit var checkRootButton: Button
    private lateinit var detectBootButton: Button
    private lateinit var backupBootButton: Button

    private var bootPartitionPath: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        rootStatus = findViewById(R.id.rootStatus)
        rootOutput = findViewById(R.id.rootOutput)
        bootStatus = findViewById(R.id.bootStatus)
        backupStatus = findViewById(R.id.backupStatus)

        checkRootButton = findViewById(R.id.checkRootButton)
        detectBootButton = findViewById(R.id.detectBootButton)
        backupBootButton = findViewById(R.id.backupBootButton)

        checkRootButton.setOnClickListener {
            verifyRoot()
        }

        detectBootButton.setOnClickListener {
            detectBootPartition()
        }

        backupBootButton.setOnClickListener {
            createBootBackup()
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
                }

                rootOutput.text =
                    "Resultado:\n${result.output}"
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

                    bootStatus.text =
                        "✗ ${result.details}"

                    backupBootButton.isEnabled = false
                }
            }

        }.start()
    }

    private fun createBootBackup() {

        val bootPath = bootPartitionPath ?: return

        backupStatus.text =
            "Criando backup do boot.img..."

        backupBootButton.isEnabled = false

        Thread {

            val result = BootBackupManager.backup(
                this,
                bootPath
            )

            runOnUiThread {

                backupBootButton.isEnabled = true

                if (result.success) {

                    backupStatus.text =
                        "✓ ${result.message}\n\n" +
                        "Arquivo:\n${result.outputPath}"

                } else {

                    backupStatus.text =
                        "✗ Falha no backup\n\n${result.message}"
                }
            }

        }.start()
    }
}
