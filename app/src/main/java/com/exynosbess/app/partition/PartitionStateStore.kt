package com.exynosbess.app.partition

import android.content.Context
import java.io.File

object PartitionStateStore {

    fun load(
        context: Context,
        partition: PartitionInfo
    ): PartitionSessionState {

        val workspace =
            PartitionWorkspace.get(
                context,
                partition
            )

        val stateFile =
            File(
                workspace.root,
                "state.txt"
            )

        if (stateFile.exists()) {

            val value =
                try {
                    stateFile
                        .readText()
                        .trim()
                } catch (_: Exception) {
                    ""
                }

            try {
                return PartitionSessionState.valueOf(
                    value
                )
            } catch (_: Exception) {
                // Faz fallback abaixo.
            }
        }

        return if (
            workspace.original.exists() &&
            workspace.original.length() > 0L
        ) {
            PartitionSessionState.IMPORTED
        } else {
            PartitionSessionState.DETECTED
        }
    }

    fun save(
        context: Context,
        partition: PartitionInfo,
        state: PartitionSessionState
    ): Boolean {

        return try {

            val workspace =
                PartitionWorkspace.prepare(
                    context,
                    partition
                )

            File(
                workspace.root,
                "state.txt"
            ).writeText(
                state.name
            )

            true

        } catch (_: Exception) {
            false
        }
    }
}
