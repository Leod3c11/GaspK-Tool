package com.exynosbess.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.exynosbess.app.partition.PartitionDtbCatalog
import com.exynosbess.app.partition.PartitionInfo
import com.exynosbess.app.partition.PartitionSelection
import com.exynosbess.app.partition.PartitionWorkspace

class PartitionDtbEditorActivity : LocalizedActivity() {

    private lateinit var partition:
        PartitionInfo

    private lateinit var titleView:
        TextView

    private lateinit var statusView:
        TextView

    private lateinit var dtbContainer:
        LinearLayout

    private lateinit var gpuButton:
        Button

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        val selected =
            PartitionSelection.get()

        if (selected == null) {
            finish()
            return
        }

        partition =
            selected

        setContentView(
            R.layout.activity_partition_dtb_editor
        )

        titleView =
            findViewById(
                R.id.partitionDtbEditorTitle
            )

        statusView =
            findViewById(
                R.id.partitionDtbEditorStatus
            )

        dtbContainer =
            findViewById(
                R.id.partitionDtbContainer
            )

        gpuButton =
            findViewById(
                R.id.partitionGpuEditorButton
            )

        titleView.text =
            getString(R.string.dtb_partition_title, partition.name)

        gpuButton.setOnClickListener {
            openGpuEditor()
        }

        renderDtbs()
    }


    override fun onResume() {
        super.onResume()

        if (::partition.isInitialized) {
            renderDtbs()
        }
    }


    private fun renderDtbs() {

        dtbContainer.removeAllViews()

        val workspace =
            PartitionWorkspace.get(
                this,
                partition
            )

        val workspacePath =
            workspace.extracted.absolutePath

        val entries =
            PartitionDtbCatalog.load(
                workspacePath
            )

        if (entries.isEmpty()) {

            statusView.text =
                getString(R.string.dtb_none_editable)

            gpuButton.visibility =
                View.GONE

            return
        }

        statusView.text =
            getString(R.string.dtb_available_count, entries.size)

        gpuButton.visibility =
            View.VISIBLE

        entries.forEach { entry ->

            val card =
                LinearLayout(this).apply {

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
                            R.drawable.bg_dashboard_card
                        )

                    layoutParams =
                        LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            topMargin =
                                dp(10)
                        }
                }

            val heading =
                TextView(this).apply {

                    text =
                        "DTB ${entry.index}"

                    textSize =
                        18f

                    setTextColor(
                        getColor(
                            R.color.exynos_text
                        )
                    )
                }

            val source =
                TextView(this).apply {

                    text =
                        buildString {

                            append(
                                getString(R.string.dtb_source, entry.sourceFile)
                            )

                            append(
                                "\nOffset: 0x"
                            )

                            append(
                                entry.offset
                                    .toString(16)
                                    .uppercase()
                            )

                            append(
                                "\n" + getString(
                                    R.string.dtb_entry_size,
                                    formatBytes(entry.size)
                                )
                            )
                        }

                    textSize =
                        12f

                    setTextColor(
                        getColor(
                            R.color.exynos_text_secondary
                        )
                    )

                    setPadding(
                        0,
                        dp(6),
                        0,
                        dp(10)
                    )
                }

            val edit =
                Button(this).apply {

                    text =
                        getString(R.string.dtb_edit_full)

                    isAllCaps =
                        false

                    setOnClickListener {

                        startActivity(
                            Intent(
                                this@PartitionDtbEditorActivity,
                                DtsEditorActivity::class.java
                            )
                                .putExtra(
                                    "workspace_path",
                                    workspacePath
                                )
                                .putExtra(
                                    "dtb_index",
                                    entry.index
                                )
                                .putExtra(
                                    "partition_session",
                                    true
                                )
                        )
                    }
                }

            card.addView(
                heading
            )

            card.addView(
                source
            )

            card.addView(
                edit
            )

            dtbContainer.addView(
                card
            )
        }
    }


    private fun openGpuEditor() {

        val workspace =
            PartitionWorkspace.get(
                this,
                partition
            )

        startActivity(
            Intent(
                this,
                GpuEditorActivity::class.java
            )
                .putExtra(
                    "workspace_path",
                    workspace.extracted.absolutePath
                )
                .putExtra(
                    "partition_session",
                    true
                )
        )
    }


    private fun formatBytes(
        bytes: Long
    ): String {

        return when {

            bytes >=
                1024L * 1024L ->

                "%.2f MB".format(
                    bytes.toDouble() /
                        (1024.0 * 1024.0)
                )

            bytes >=
                1024L ->

                "%.2f KB".format(
                    bytes.toDouble() /
                        1024.0
                )

            else ->
                "$bytes B"
        }
    }


    private fun dp(
        value: Int
    ): Int {

        return (
            value *
                resources.displayMetrics.density
            ).toInt()
    }
}
