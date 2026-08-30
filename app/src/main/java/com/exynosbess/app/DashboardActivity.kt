package com.exynosbess.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.exynosbess.app.startup.AutoSetupManager
import com.exynosbess.app.startup.DeviceTreeInfoDetector
import com.exynosbess.app.startup.DtbCatalogDetector

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
            TextView(this).apply {

                setPadding(
                    dpToPx(24),
                    dpToPx(18),
                    dpToPx(24),
                    dpToPx(18)
                )

                text =
                    "Inicializando..."
            }

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
                    if (result.rootGranted) {
                        "Root: ✓ concedido"
                    } else {
                        "Root: ✗ indisponível"
                    }

                bootStatus.text =
                    "Boot: ${result.bootPath ?: "não encontrado"}"

                dtbContainer.removeAllViews()

                if (
                    !result.success ||
                    result.workspacePath == null
                ) {

                    dtbCount.text =
                        "Device Trees: 0"

                    dashboardStatus.text =
                        "Falha na preparação automática:\n" +
                            result.message

                    return@runOnUiThread
                }

                val workspace =
                    result.workspacePath

                val generalInfo =
                    DeviceTreeInfoDetector.detect(
                        workspace
                    )

                dtbModel.text =
                    generalInfo.model

                compatible.text =
                    generalInfo.compatible

                gpu.text =
                    generalInfo.gpu

                gpuCompatible.text =
                    "DTB: ${generalInfo.gpuCompatible}"

                val dtbs =
                    DtbCatalogDetector.detectAll(
                        workspace
                    )

                dtbCount.text =
                    "Device Trees: ${dtbs.size}"

                createDtbCards(
                    dtbs
                )

                dashboardStatus.text =
                    if (dtbs.isNotEmpty()) {

                        "✓ ${dtbs.size} Device Tree(s) preparados"

                    } else {

                        "Nenhum DTS disponível para edição"
                    }
            }

        }.start()
    }

    private fun createDtbCards(
        dtbs: List<DtbCatalogDetector.DtbInfo>
    ) {

        dtbContainer.removeAllViews()

        if (dtbs.isEmpty()) {

            val empty =
                TextView(this).apply {

                    text =
                        "Nenhum Device Tree disponível"

                    setTextColor(
                        getColor(
                            R.color.exynos_text_secondary
                        )
                    )

                    textSize =
                        13f

                    setPadding(
                        dpToPx(4),
                        dpToPx(12),
                        dpToPx(4),
                        dpToPx(12)
                    )
                }

            dtbContainer.addView(
                empty
            )

            return
        }

        for (dtb in dtbs) {

            val card =
                LinearLayout(this).apply {

                    orientation =
                        LinearLayout.VERTICAL

                    setPadding(
                        dpToPx(16),
                        dpToPx(14),
                        dpToPx(16),
                        dpToPx(14)
                    )

                    setBackgroundResource(
                        R.drawable.bg_dashboard_card
                    )

                    isClickable =
                        true

                    isFocusable =
                        true

                    setOnClickListener {
                        openEditor(
                            dtb.index
                        )
                    }
                }

            val params =
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )

            params.bottomMargin =
                dpToPx(10)

            card.layoutParams =
                params

            val headerRow =
                LinearLayout(this).apply {

                    orientation =
                        LinearLayout.HORIZONTAL

                    gravity =
                        Gravity.CENTER_VERTICAL
                }

            val title =
                TextView(this).apply {

                    text =
                        "DTB ${dtb.index}"

                    setTextColor(
                        getColor(
                            R.color.exynos_accent
                        )
                    )

                    textSize =
                        13f

                    setTypeface(
                        typeface,
                        Typeface.BOLD
                    )
                }

            headerRow.addView(
                title,
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )

            val editLabel =
                TextView(this).apply {

                    text =
                        "EDITAR ›"

                    setTextColor(
                        getColor(
                            R.color.exynos_accent
                        )
                    )

                    textSize =
                        12f

                    setTypeface(
                        typeface,
                        Typeface.BOLD
                    )
                }

            headerRow.addView(
                editLabel
            )

            card.addView(
                headerRow
            )

            val model =
                TextView(this).apply {

                    text =
                        dtb.model

                    setTextColor(
                        getColor(
                            R.color.exynos_text
                        )
                    )

                    textSize =
                        17f

                    setTypeface(
                        typeface,
                        Typeface.BOLD
                    )

                    setPadding(
                        0,
                        dpToPx(8),
                        0,
                        0
                    )
                }

            card.addView(
                model
            )

            val compatibleView =
                TextView(this).apply {

                    text =
                        dtb.compatible

                    setTextColor(
                        getColor(
                            R.color.exynos_text_secondary
                        )
                    )

                    textSize =
                        12f

                    setPadding(
                        0,
                        dpToPx(5),
                        0,
                        0
                    )
                }

            card.addView(
                compatibleView
            )

            val socView =
                TextView(this).apply {

                    text =
                        "SoC: ${dtb.soc}"

                    setTextColor(
                        getColor(
                            R.color.exynos_text_secondary
                        )
                    )

                    textSize =
                        11f

                    setPadding(
                        0,
                        dpToPx(8),
                        0,
                        0
                    )
                }

            card.addView(
                socView
            )

            val fileView =
                TextView(this).apply {

                    text =
                        "Arquivo: ${dtb.fileName}"

                    setTextColor(
                        getColor(
                            R.color.exynos_text_secondary
                        )
                    )

                    textSize =
                        10f

                    setPadding(
                        0,
                        dpToPx(3),
                        0,
                        0
                    )
                }

            card.addView(
                fileView
            )

            dtbContainer.addView(
                card
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

        startActivity(
            intent
        )
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
