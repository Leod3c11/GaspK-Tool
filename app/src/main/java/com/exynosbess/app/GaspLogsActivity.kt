package com.exynosbess.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.exynosbess.app.logging.GaspLog

class GaspLogsActivity : LocalizedActivity() {

    private lateinit var logView:
        TextView

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_gasp_logs
        )

        logView =
            findViewById(
                R.id.gaspLogsContent
            )

        findViewById<Button>(
            R.id.gaspLogsRefresh
        ).setOnClickListener {
            refresh()
        }

        findViewById<Button>(
            R.id.gaspLogsCopy
        ).setOnClickListener {
            copyLogs()
        }

        findViewById<Button>(
            R.id.gaspLogsClear
        ).setOnClickListener {
            confirmClear()
        }

        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {

        logView.text =
            GaspLog.formatted(
                this
            )
    }

    private fun copyLogs() {

        val text =
            GaspLog.formatted(
                this
            )

        val clipboard =
            getSystemService(
                Context.CLIPBOARD_SERVICE
            ) as ClipboardManager

        clipboard.setPrimaryClip(
            ClipData.newPlainText(
                "GaspKernel Logs",
                text
            )
        )

        Toast.makeText(
            this,
            getString(R.string.logs_copied),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun confirmClear() {

        AlertDialog.Builder(this)
            .setTitle(
                getString(R.string.logs_clear_title)
            )
            .setMessage(
                getString(R.string.logs_clear_message)
            )
            .setNegativeButton(
                getString(R.string.action_cancel),
                null
            )
            .setPositiveButton(
                getString(R.string.action_clear)
            ) { _, _ ->

                GaspLog.clear(
                    this
                )

                refresh()
            }
            .show()
    }
}
