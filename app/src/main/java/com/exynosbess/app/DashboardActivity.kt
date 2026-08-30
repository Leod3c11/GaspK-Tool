package com.exynosbess.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
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

    private lateinit var dtbContainer: LinearLayout
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

        dtbContainer =
            findViewById(
                R.id.dashboardDtbContainer
            )

        advanced =
            findViewById(
                R.id.dashboardAdvanced
            )

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

                dtbContainer.removeAllViews()

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

                createDtbButtons(
                    result.dtbCount
                )

                dashboardStatus.text =
                    "✓ Ambiente preparado automaticamente"
            }

        }.start()
    }

    private fun createDtbButtons(
        count: Int
    ) {

        dtbContainer.removeAllViews()

        if (count <= 0) {

            val empty =
                TextView(this)

            empty.text =
                "Nenhum Device Tree disponível"

            empty.setTextColor(
                getColor(
                    R.color.exynos_text_secondary
                )
            )

            empty.textSize =
                13f

            empty.setPadding(
                4,
                12,
                4,
                12
            )

            dtbContainer.addView(
                empty
            )

            return
        }

        for (index in 0 until count) {

            val button =
                Button(this)

            button.text =
                "Editar Device Tree $index"

            button.isAllCaps =
                false

            button.textSize =
                14f

            button.setTextColor(
                getColor(
                    R.color.exynos_text
                )
            )

            button.setBackgroundResource(
                R.drawable.bg_button_secondary
            )

            button.setPadding(
                16,
                0,
                16,
                0
            )

            val params =
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dpToPx(50)
                )

            if (index > 0) {
                params.topMargin =
                    dpToPx(8)
            }

            button.layoutParams =
                params

            button.setOnClickListener {
                openEditor(index)
            }

            dtbContainer.addView(
                button
            )
        }
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

    private fun dpToPx(
        dp: Int
    ): Int {

        return (
            dp *
                resources.displayMetrics.density
            ).toInt()
    }
}
