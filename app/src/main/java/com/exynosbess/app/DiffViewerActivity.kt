package com.exynosbess.app

import android.os.Bundle
import android.widget.TextView
import com.exynosbess.app.editor.TextDiffEngine
import com.exynosbess.app.localization.LocalizedActivity
import java.io.File

class DiffViewerActivity :
    LocalizedActivity() {

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(savedInstanceState)

        setContentView(
            R.layout.activity_diff_viewer
        )

        val summary =
            findViewById<TextView>(
                R.id.diffSummary
            )

        val diffText =
            findViewById<TextView>(
                R.id.diffText
            )

        val originalPath =
            intent.getStringExtra(
                EXTRA_ORIGINAL
            )

        val modifiedPath =
            intent.getStringExtra(
                EXTRA_MODIFIED
            )

        if (
            originalPath.isNullOrBlank() ||
            modifiedPath.isNullOrBlank()
        ) {
            summary.text =
                getString(R.string.diff_error)
            return
        }

        Thread {

            try {

                val original =
                    File(originalPath)
                        .readText()

                val modified =
                    File(modifiedPath)
                        .readText()

                val result =
                    TextDiffEngine.compare(
                        original,
                        modified
                    )

                runOnUiThread {

                    summary.text =
                        if (result.changed) {
                            getString(
                                R.string.diff_summary,
                                result.added,
                                result.removed
                            )
                        } else {
                            getString(
                                R.string.diff_no_changes
                            )
                        }

                    diffText.text =
                        result.text
                }

            } catch (e: Exception) {

                runOnUiThread {

                    summary.text =
                        getString(
                            R.string.diff_error_detail,
                            e.message ?: "?"
                        )
                }
            }

        }.start()
    }


    companion object {

        const val EXTRA_ORIGINAL =
            "diff_original"

        const val EXTRA_MODIFIED =
            "diff_modified"
    }
}
