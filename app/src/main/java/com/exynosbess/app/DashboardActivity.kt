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
import android.widget.ScrollView
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

    private lateinit var dtbSelector: LinearLayout
    private lateinit var dtbSelectorCount: TextView

    private lateinit var advanced: Button

    private var availableDtbs =
        emptyList<DtbCatalogDetector.DtbInfo>()

    private var dtbDialog: AlertDialog? =
        null

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

        dtbSelector =
            findViewById(
                R.id.dashboardDtbSelector
            )

        dtbSelectorCount =
            findViewById(
                R.id.dashboardDtbSelectorCount
            )

        advanced =
            findViewById(
                R.id.dashboardAdvanced
            )

        dtbSelector.isEnabled =
            false

        dtbSelector.setOnClickListener {

            if (availableDtbs.isNotEmpty()) {
                showDtbSelector()
            }
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

                if (
                    !result.success ||
                    result.workspacePath == null
                ) {

                    dtbCount.text =
                        "Device Trees: 0"

                    dtbSelectorCount.text =
                        "Nenhum disponível"

                    dtbSelector.isEnabled =
                        false

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

                availableDtbs =
                    DtbCatalogDetector.detectAll(
                        workspace
                    )

                val count =
                    availableDtbs.size

                dtbCount.text =
                    "Device Trees: $count"

                dtbSelectorCount.text =
                    when (count) {

                        0 ->
                            "Nenhum disponível"

                        1 ->
                            "1 Device Tree disponível"

                        else ->
                            "$count Device Trees disponíveis"
                    }

                dtbSelector.isEnabled =
                    count > 0

                dashboardStatus.text =
                    if (count > 0) {

                        "✓ Ambiente preparado automaticamente"

                    } else {

                        "Nenhum DTS disponível para edição"
                    }
            }

        }.start()
    }

    private fun showDtbSelector() {

        if (availableDtbs.isEmpty()) {
            return
        }

        val container =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dpToPx(18),
                    dpToPx(8),
                    dpToPx(18),
                    dpToPx(12)
                )
            }

        for (
            (position, dtb)
            in availableDtbs.withIndex()
        ) {

            val item =
                createDtbListItem(
                    dtb
                )

            container.addView(
                item
            )

            if (
                position <
                availableDtbs.lastIndex
            ) {

                val separator =
                    TextView(this).apply {

                        setBackgroundColor(
                            getColor(
                                R.color.exynos_border
                            )
                        )
                    }

                val separatorParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dpToPx(1)
                    )

                separatorParams.setMargins(
                    0,
                    dpToPx(8),
                    0,
                    dpToPx(8)
                )

                container.addView(
                    separator,
                    separatorParams
                )
            }
        }

        val scroll =
            ScrollView(this).apply {

                addView(
                    container
                )

                isFillViewport =
                    true
            }

        val dialog =
            AlertDialog.Builder(this)
                .setTitle(
                    "Device Trees disponíveis"
                )
                .setView(
                    scroll
                )
                .setNegativeButton(
                    "Fechar",
                    null
                )
                .create()

        dtbDialog =
            dialog

        dialog.setOnShowListener {

            val maxHeight =
                (
                    resources
                        .displayMetrics
                        .heightPixels *
                        0.70f
                    ).toInt()

            scroll.layoutParams =
                scroll.layoutParams.apply {

                    height =
                        minOf(
                            scroll.measuredHeight,
                            maxHeight
                        )
                }
        }

        dialog.show()
    }

    private fun createDtbListItem(
        dtb: DtbCatalogDetector.DtbInfo
    ): LinearLayout {

        val item =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL

                setPadding(
                    0,
                    dpToPx(7),
                    0,
                    dpToPx(7)
                )
            }

        val info =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL
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

        info.addView(
            title
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
                    14f

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )

                maxLines =
                    2

                setPadding(
                    0,
                    dpToPx(3),
                    dpToPx(10),
                    0
                )
            }

        info.addView(
            model
        )

        val soc =
            TextView(this).apply {

                text =
                    dtb.soc

                setTextColor(
                    getColor(
                        R.color.exynos_text_secondary
                    )
                )

                textSize =
                    11f

                setPadding(
                    0,
                    dpToPx(3),
                    dpToPx(10),
                    0
                )
            }

        info.addView(
            soc
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
                    10f

                maxLines =
                    2

                setPadding(
                    0,
                    dpToPx(2),
                    dpToPx(10),
                    0
                )
            }

        info.addView(
            compatibleView
        )

        item.addView(
            info,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        val editButton =
            Button(this).apply {

                text =
                    "Editar"

                isAllCaps =
                    false

                textSize =
                    12f

                setTextColor(
                    getColor(
                        R.color.exynos_text
                    )
                )

                setBackgroundResource(
                    R.drawable.bg_button_secondary
                )

                setOnClickListener {

                    openEditor(
                        dtb.index
                    )
                }
            }

        item.addView(
            editButton,
            LinearLayout.LayoutParams(
                dpToPx(84),
                dpToPx(44)
            )
        )

        item.setOnClickListener {

            openEditor(
                dtb.index
            )
        }

        return item
    }

    private fun openEditor(
        index: Int
    ) {

        dtbDialog?.dismiss()

        dtbDialog =
            null

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
