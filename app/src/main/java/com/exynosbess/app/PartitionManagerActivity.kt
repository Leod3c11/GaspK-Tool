package com.exynosbess.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.exynosbess.app.partition.PartitionSelection
import com.exynosbess.app.partition.PartitionInfo
import com.exynosbess.app.partition.PartitionScanner
import com.exynosbess.app.partition.PartitionType

class PartitionManagerActivity : Activity() {

    private lateinit var status: TextView

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
                        "✗ ${result.message}"

                    return@runOnUiThread
                }

                status.text =
                    buildString {

                        append(
                            "✓ ${result.message}"
                        )

                        result.activeSlotSuffix
                            ?.let {

                                append(
                                    "\nSlot ativo: $it"
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
                createPartitionView(
                    partition
                )
            )
        }

        showEmptyMessageIfNeeded(
            bootContainer,
            "Nenhuma partição boot encontrada."
        )

        showEmptyMessageIfNeeded(
            dtbContainer,
            "Nenhuma partição dtb encontrada."
        )

        showEmptyMessageIfNeeded(
            dtboContainer,
            "Nenhuma partição dtbo encontrada."
        )
    }

    private fun createPartitionView(
        partition: PartitionInfo
    ): View {

        val root =
            LinearLayout(
                this
            ).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(14),
                    dp(14),
                    dp(14),
                    dp(14)
                )

                background =
                    getDrawable(
                        R.drawable.bg_advanced_card
                    )

                val params =
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )

                params.bottomMargin =
                    dp(10)

                layoutParams =
                    params
            }

        val active =
            partition.slotSuffix != null &&
                partition.slotSuffix ==
                    activeSlot

        val title =
            TextView(
                this
            ).apply {

                text =
                    if (active) {
                        "${partition.name}  •  ATIVA"
                    } else {
                        partition.name
                    }

                setTextColor(
                    0xFFF2F4F7.toInt()
                )

                textSize =
                    16f
            }

        root.addView(
            title
        )

        val details =
            TextView(
                this
            ).apply {

                text =
                    buildString {

                        append(
                            partition.sizeDisplay
                        )

                        append(
                            "\n${partition.aliasPath}"
                        )

                        if (
                            partition.resolvedPath !=
                            partition.aliasPath
                        ) {
                            append(
                                "\n→ ${partition.resolvedPath}"
                            )
                        }

                        if (
                            partition.aliases.size > 1
                        ) {
                            append(
                                "\n${partition.aliases.size} aliases"
                            )
                        }
                    }

                setTextColor(
                    0xFF919BA7.toInt()
                )

                textSize =
                    12f

                setPadding(
                    0,
                    dp(5),
                    0,
                    dp(10)
                )
            }

        root.addView(
            details
        )

        val button =
            Button(
                this
            ).apply {

                text =
                    "Abrir"

                isAllCaps =
                    false

                setOnClickListener {
                    openPartition(
                        partition
                    )
                }
            }

        root.addView(
            button
        )

        return root
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

    private fun showEmptyMessageIfNeeded(
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
