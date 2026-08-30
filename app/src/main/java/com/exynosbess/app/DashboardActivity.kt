package com.exynosbess.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import com.exynosbess.app.startup.AutoSetupManager
import com.exynosbess.app.startup.DeviceTreeInfoDetector

class DashboardActivity : Activity() {

    private lateinit var dtbModel: TextView
    private lateinit var compatible: TextView
    private lateinit var gpu: TextView
    private lateinit var gpuCompatible: TextView

    private lateinit var rootStatus: TextView
    private lateinit var dtbCount: TextView
    private lateinit var bootStatus: TextView
    private lateinit var dashboardStatus: TextView

    private lateinit var editDts0: Button
    private lateinit var editDts1: Button
    private lateinit var advanced: Button

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_dashboard
        )

        dtbModel =
            findViewById(
                R.id.dashboardDtbModel
            )

        compatible =
            findViewById(
                R.id.dashboardCompatible
            )

        gpu =
            findViewById(
                R.id.dashboardGpu
            )

        gpuCompatible =
            findViewById(
                R.id.dashboardGpuCompatible
            )

        rootStatus =
            findViewById(
                R.id.dashboardRoot
            )

        dtbCount =
            findViewById(
                R.id.dashboardDtbCount
            )

        bootStatus =
            findViewById(
                R.id.dashboardBoot
            )

        dashboardStatus =
            findViewById(
                R.id.dashboardStatus
            )

        editDts0 =
            findViewById(
                R.id.dashboardEditDts0
            )

        editDts1 =
            findViewById(
                R.id.dashboardEditDts1
            )

        advanced =
            findViewById(
                R.id.dashboardAdvanced
            )

        editDts0.isEnabled = false
        editDts1.isEnabled = false

        editDts0.setOnClickListener {
            openEditor(0)
        }

        editDts1.setOnClickListener {
            openEditor(1)
        }

        advanced.setOnClickListener {

            startActivity(
                Intent(
                    this,
                    MainActivity::class.java
                )
            )
        }

        runAutomaticSetup()
    }

    private fun runAutomaticSetup() {

        val progressText =
            TextView(this)

        progressText.setPadding(
            48,
            34,
            48,
            34
        )

        progressText.text =
            "Inicializando..."

        val dialog =
            AlertDialog.Builder(this)
                .setTitle(
                    "Preparando GaspKernel"
                )
                .setView(progressText)
                .setCancelable(false)
                .create()

        dialog.show()

        Thread {

            val result =
                AutoSetupManager.run(
                    this
                ) { status ->

                    runOnUiThread {
                        progressText.text =
                            status
                    }
                }

            runOnUiThread {

                dialog.dismiss()

                rootStatus.text =
                    if (result.rootGranted)
                        "Root: ✓ concedido"
                    else
                        "Root: ✗ indisponível"

                dtbCount.text =
                    "Device Trees: ${result.dtbCount}"

                bootStatus.text =
                    "Boot: ${result.bootPath ?: "não encontrado"}"

                if (
                    !result.success ||
                    result.workspacePath == null
                ) {

                    dashboardStatus.text =
                        "Falha na preparação automática:\n" +
                            result.message

                    return@runOnUiThread
                }

                val info =
                    DeviceTreeInfoDetector.detect(
                        result.workspacePath
                    )

                dtbModel.text =
                    info.model

                compatible.text =
                    info.compatible

                gpu.text =
                    info.gpu

                gpuCompatible.text =
                    "DTB: ${info.gpuCompatible}"

                editDts0.isEnabled =
                    result.dtbCount >= 1

                editDts1.isEnabled =
                    result.dtbCount >= 2

                dashboardStatus.text =
                    "✓ Ambiente preparado automaticamente"
            }

        }.start()
    }

    private fun openEditor(
        index: Int
    ) {

        val intent =
            Intent(
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
