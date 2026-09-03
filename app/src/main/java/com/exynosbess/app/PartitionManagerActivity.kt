package com.exynosbess.app

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.exynosbess.app.partition.PartitionDtbCatalog
import com.exynosbess.app.partition.PartitionInfo
import com.exynosbess.app.partition.PartitionScanner
import com.exynosbess.app.partition.PartitionSelection
import com.exynosbess.app.partition.PartitionSessionState
import com.exynosbess.app.partition.PartitionStateStore
import com.exynosbess.app.partition.PartitionType
import com.exynosbess.app.partition.PartitionWorkspace

class PartitionManagerActivity : Activity() {

    private lateinit var status:
        TextView

    private lateinit var bootContainer:
        LinearLayout

    private lateinit var dtbContainer:
        LinearLayout

    private lateinit var dtboContainer:
        LinearLayout

    private lateinit var scanButton:
        Button

    private var activeSlot:
        String? = null


    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(
            savedInstanceState
        )

        setContentView(
            R.layout.activity_partition_manager
        )

        status =
            findViewById(
                R.id.partitionStatus
            )

        bootContainer =
            findViewById(
                R.id.bootPartitionContainer
            )

        dtbContainer =
            findViewById(
                R.id.dtbPartitionContainer
            )

        dtboContainer =
            findViewById(
                R.id.dtboPartitionContainer
            )

        scanButton =
            findViewById(
                R.id.scanPartitionsButton
            )

        scanButton.setOnClickListener {
            scanPartitions()
        }

        scanPartitions()
    }


    private fun scanPartitions() {

        scanButton.isEnabled =
            false

        status.text =
            "Detectando partições..."

        clearContainers()

        Thread {

            val result =
                PartitionScanner.scan()

            runOnUiThread {

                scanButton.isEnabled =
                    true

                activeSlot =
                    result.activeSlotSuffix

                if (!result.success) {

                    status.text =
                        result.message

                    return@runOnUiThread
                }

                status.text =
                    buildString {

                        append(
                            "${result.partitions.size} partição(ões) disponível(is)"
                        )

                        result.activeSlotSuffix
                            ?.let {

                                append(
                                    "  •  slot $it"
                                )
                            }
                    }

                renderPartitions(
                    result.partitions
                )
            }

        }.start()
    }


    private fun renderPartitions(
        partitions: List<PartitionInfo>
    ) {

        partitions.forEach {
            partition ->

            val target =
                when (
                    partition.type
                ) {

                    PartitionType.BOOT ->
                        bootContainer

                    PartitionType.DTB ->
                        dtbContainer

                    PartitionType.DTBO ->
                        dtboContainer
                }

            target.addView(
                createPartitionCard(
                    partition
                )
            )
        }

        showEmpty(
            bootContainer,
            "Nenhuma partição boot encontrada."
        )

        showEmpty(
            dtbContainer,
            "Nenhuma partição DTB encontrada."
        )

        showEmpty(
            dtboContainer,
            "Nenhuma partição DTBO encontrada."
        )
    }


    private fun createPartitionCard(
        partition: PartitionInfo
    ): View {

        val root =
            LinearLayout(
                this
            ).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(18),
                    dp(17),
                    dp(18),
                    dp(17)
                )

                background =
                    getDrawable(
                        R.drawable.bg_dashboard_card
                    )

                isClickable =
                    true

                isFocusable =
                    true

                setOnClickListener {
                    openPartition(
                        partition
                    )
                }

                layoutParams =
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {

                        bottomMargin =
                            dp(10)
                    }
            }


        val header =
            LinearLayout(
                this
            ).apply {

                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL
            }


        val title =
            TextView(
                this
            ).apply {

                text =
                    friendlyName(
                        partition
                    )

                setTextColor(
                    0xFFF2F4F7.toInt()
                )

                textSize =
                    18f

                setTypeface(
                    typeface,
                    Typeface.BOLD
                )

                layoutParams =
                    LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    )
            }


        val arrow =
            TextView(
                this
            ).apply {

                text =
                    "›"

                textSize =
                    28f

                setTextColor(
                    0xFF6E9EFF.toInt()
                )
            }


        header.addView(
            title
        )

        header.addView(
            arrow
        )

        root.addView(
            header
        )


        val active =
            partition.slotSuffix != null &&
                partition.slotSuffix ==
                activeSlot


        val identifier =
            TextView(
                this
            ).apply {

                text =
                    buildString {

                        append(
                            partition.name
                        )

                        append(
                            "  •  "
                        )

                        append(
                            partition.sizeDisplay
                        )

                        if (active) {
                            append(
                                "  •  ATIVA"
                            )
                        }
                    }

                setTextColor(
                    0xFF6E9EFF.toInt()
                )

                textSize =
                    11f

                setPadding(
                    0,
                    dp(5),
                    0,
                    0
                )
            }

        root.addView(
            identifier
        )


        val state =
            PartitionStateStore.load(
                this,
                partition
            )

        val dtbCount =
            PartitionDtbCatalog.load(
                PartitionWorkspace.get(
                    this,
                    partition
                ).extracted.absolutePath
            ).size


        val summary =
            TextView(
                this
            ).apply {

                text =
                    sessionSummary(
                        state,
                        dtbCount
                    )

                setTextColor(
                    0xFF919BA7.toInt()
                )

                textSize =
                    12f

                setPadding(
                    0,
                    dp(9),
                    0,
                    0
                )
            }

        root.addView(
            summary
        )

        return root
    }


    private fun friendlyName(
        partition: PartitionInfo
    ): String {

        return when (
            partition.type
        ) {

            PartitionType.BOOT ->
                "Boot"

            PartitionType.DTB ->
                "Device Tree"

            PartitionType.DTBO ->
                "Device Tree Overlays"
        }
    }


    private fun sessionSummary(
        state: PartitionSessionState,
        count: Int
    ): String {

        return when (state) {

            PartitionSessionState.DETECTED ->
                "Toque para preparar"

            PartitionSessionState.IMPORTED ->
                "Preparação pendente"

            PartitionSessionState.EXTRACTED ->

                if (count > 0) {
                    "Pronta para editar  •  $count item(ns)"
                } else {
                    "Pronta para editar"
                }

            PartitionSessionState.DIRTY,
            PartitionSessionState.SAVED ->
                "Alterações pendentes"

            PartitionSessionState.VALIDATED ->
                "Alterações validadas"

            PartitionSessionState.BUILT ->
                "Imagem reconstruída"

            PartitionSessionState.VERIFIED ->
                "✓ Pronta para exportar ou instalar"
        }
    }


    private fun openPartition(
        partition: PartitionInfo
    ) {

        PartitionSelection.set(
            partition
        )

        startActivity(
            Intent(
                this,
                PartitionSessionActivity::class.java
            )
        )
    }


    private fun clearContainers() {

        bootContainer.removeAllViews()
        dtbContainer.removeAllViews()
        dtboContainer.removeAllViews()
    }


    private fun showEmpty(
        container: LinearLayout,
        message: String
    ) {

        if (
            container.childCount != 0
        ) {
            return
        }

        container.addView(
            TextView(
                this
            ).apply {

                text =
                    message

                setTextColor(
                    0xFF697586.toInt()
                )

                textSize =
                    13f

                setPadding(
                    dp(4),
                    dp(8),
                    dp(4),
                    dp(16)
                )
            }
        )
    }


    private fun dp(
        value: Int
    ): Int {

        return (
            value *
                resources
                    .displayMetrics
                    .density
            ).toInt()
    }
}
